package com.gothwad.store.data.install

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * `pm install` helper shared by the root and Shizuku back-ends.
 *
 * Two proven command shapes are supported:
 *  · direct path  – `pm install -r /path/app.apk` (root can read app-private files)
 *  · streamed     – `pm install -r -S <bytes>` with the APK piped to stdin, which
 *                   is exactly how `adb install` works when the shell user
 *                   cannot read the file.
 */
object PmInstall {

    private const val TIMEOUT_SECONDS = 240L

    /** Signature of the process launcher: root uses su, Shizuku uses its service. */
    fun interface Runner {
        suspend fun run(
            command: Array<String>,
            stdinFile: File?,
            onProgress: ((Long, Long) -> Unit)?,
        ): ShellResult
    }

    val rootRunner = Runner { command, stdinFile, onProgress ->
        withContext(Dispatchers.IO) {
            val wrapped = arrayOf("su", "-c", command.joinToString(" ") { shellQuote(it) })
            val process = try {
                ProcessBuilder(*wrapped)
                    .redirectErrorStream(true)
                    .start()
            } catch (error: IOException) {
                return@withContext ShellResult(-1, "", "Root shell (su) is not available on this device.")
            }
            val output = StringBuilder()
            try {
                val reader = Thread {
                    runCatching {
                        process.inputStream.bufferedReader().forEachLine { output.appendLine(it) }
                    }
                }
                reader.isDaemon = true
                reader.start()
                if (stdinFile != null) {
                    process.outputStream.use { sink ->
                        stdinFile.inputStream().use { input ->
                            val total = stdinFile.length()
                            var copied = 0L
                            val buffer = ByteArray(128 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                sink.write(buffer, 0, read)
                                copied += read
                                onProgress?.invoke(copied, total)
                            }
                            sink.flush()
                        }
                    }
                } else {
                    runCatching { process.outputStream.close() }
                }
                val finished = process.awaitExit(TIMEOUT_SECONDS)
                if (!finished) {
                    process.destroy()
                    return@withContext ShellResult(-1, output.toString(), "The root installer timed out.")
                }
                reader.join(2_000)
                ShellResult(process.exitValue(), output.toString(), "")
            } catch (error: Throwable) {
                runCatching { process.destroy() }
                ShellResult(-1, output.toString(), error.message ?: "Root install failed.")
            }
        }
    }

    fun shizukuRunner(): Runner = Runner { command, stdinFile, onProgress ->
        ShizukuBridge.runCommand(command, stdinFile, onProgress)
    }

    /**
     * Installs [apk]. When [stream] is true the file is piped through stdin,
     * otherwise the plain path form is used.
     */
    suspend fun install(
        runner: Runner,
        apk: File,
        packageName: String,
        allowDowngrade: Boolean,
        stream: Boolean,
        onProgress: ((Float) -> Unit)? = null,
    ): InstallPhase {
        if (!apk.exists() || apk.length() == 0L) {
            return InstallPhase.Failed(
                "The downloaded APK is missing.",
                "Download the app again from its Store page.",
            )
        }
        val base = mutableListOf("pm", "install", "-r")
        if (allowDowngrade) base += "-d"
        val command = if (stream) {
            base += listOf("-S", apk.length().toString())
            base.toTypedArray()
        } else {
            (base + apk.absolutePath).toTypedArray()
        }

        val result = runner.run(
            command,
            if (stream) apk else null,
        ) { copied, total ->
            if (total > 0) onProgress?.invoke((copied.toFloat() / total.toFloat()).coerceIn(0f, 1f))
        }

        return interpret(result, packageName)
    }

    fun interpret(result: ShellResult, packageName: String): InstallPhase {
        val output = result.combined
        if (result.exitCode == 0 && !output.contains("Failure", ignoreCase = true)) {
            return InstallPhase.Completed("$packageName installed.")
        }
        val reason = output.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("Failure") }
            ?.removePrefix("Failure")
            ?.trim('[', ']', ' ')
            .orEmpty()
        val message = when {
            output.isBlank() -> "The privileged installer did not answer (exit ${result.exitCode})."
            reason.isBlank() -> output.lineSequence().lastOrNull { it.isNotBlank() }
                ?: "The privileged installer failed."
            else -> reason
        }
        return InstallPhase.Failed(friendlyMessage(message, packageName), hintFor(message))
    }

    /** Turns `INSTALL_FAILED_*` codes into something a user can act on. */
    fun friendlyMessage(raw: String, packageName: String): String {
        val code = raw.substringBefore(':').trim().uppercase()
        return when {
            code.contains("VERSION_DOWNGRADE") ->
                "Android refused the install because the installed version is newer. " +
                    "Enable “Allow downgrade” in Download settings and try again."
            code.contains("UPDATE_INCOMPATIBLE") ->
                "This build is signed with a different key than the installed $packageName. " +
                    "Uninstall the installed copy first, then install it again from Kiwi Store."
            code.contains("ALREADY_EXISTS") ->
                "$packageName is already installed. Uninstall it and try again."
            code.contains("INSUFFICIENT_STORAGE") ->
                "Not enough free storage. Free up space and try again."
            code.contains("VERIFICATION_FAILURE") ->
                "Play Protect blocked the install. Turn off “Verify apps over USB” / scanning " +
                    "for this install and retry."
            code.contains("NO_MATCHING_ABIS") ->
                "This APK does not contain native code for your device's CPU."
            code.contains("PARSE_FAILED") ->
                "The APK looks damaged. Delete the download and download it again."
            code.contains("USER_RESTRICTED") ->
                "Android blocked this install. Allow installs from Kiwi Store in Settings."
            code.contains("TEST_ONLY") -> "This build is a test-only build." 
            code.contains("INVALID_APK") -> "The APK is not valid for this Android version."
            else -> raw.ifBlank { "The install failed." }
        }
    }

    fun hintFor(raw: String): String? = when {
        raw.contains("UPDATE_INCOMPATIBLE", true) ->
            "Tip: the Shizuku/root installer can replace apps signed with the platform key, but not apps signed with a different developer key."
        raw.contains("USER_RESTRICTED", true) ->
            "Open Settings → Apps → Kiwi Store → Install unknown apps and allow it."
        raw.contains("VERIFICATION_FAILURE", true) ->
            "Settings → Security → Google Play Protect → turn off scanning temporarily."
        else -> null
    }

    private fun shellQuote(value: String): String =
        if (value.matches(Regex("[A-Za-z0-9_./:=-]+"))) value else "'" + value.replace("'", "'\\''") + "'"
}
