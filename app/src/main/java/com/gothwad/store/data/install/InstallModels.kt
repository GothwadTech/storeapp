package com.gothwad.store.data.install

import android.os.Build

/**
 * How Kiwi Store is allowed to hand an APK to Android.
 *
 *  · [Session]  – PackageInstaller session. Play-Store-like in-app progress,
 *                 Android still shows its own confirmation dialog.
 *  · [Intent]   – Classic "open the APK" hand-off with a content:// URI. This is
 *                 the path that needs the *Install unknown apps* permission.
 *  · [Shizuku]  – Runs `pm install` with ADB/shell identity through Shizuku/Sui.
 *  · [Root]     – Runs `pm install` through `su`.
 *  · [Ask]      – Never auto-pick; the installer sheet is always shown.
 */
enum class InstallMethod(val id: String) {
    Session("session"),
    Intent("intent"),
    Shizuku("shizuku"),
    Root("root"),
    Ask("ask");

    /** Human label used by the installer sheet and the settings screens. */
    fun displayName(): String = when (this) {
        Session -> "PackageInstaller session"
        Intent -> "Android installer (APK intent)"
        Shizuku -> "Shizuku / Sui (ADB)"
        Root -> "Root (su)"
        Ask -> "Ask me every time"
    }

    val blurb: String
        get() = when (this) {
            Session -> "In-app progress; Android shows one confirmation dialog."
            Intent -> "Classic hand-off — needs the Install unknown apps permission."
            Shizuku -> "Silent installs through the Shizuku service running as shell."
            Root -> "Silent installs through su; works with Magisk/KernelSU."
            Ask -> "Always open the installer sheet so you can pick per app."
        }

    companion object {
        val selectable: List<InstallMethod> get() = listOf(Session, Intent, Shizuku, Root, Ask)
        fun fromId(id: String?): InstallMethod =
            entries.firstOrNull { it.id == id } ?: Session
    }
}

/** Everything a screen needs to describe the state of one install. */
sealed interface InstallPhase {
    data object Idle : InstallPhase
    data object Preparing : InstallPhase
    data class Installing(val progress: Float, val label: String) : InstallPhase
    data class AwaitingUser(val label: String) : InstallPhase
    data class Completed(val message: String) : InstallPhase
    data class Failed(val message: String, val hint: String? = null) : InstallPhase

    val isBusy: Boolean get() = this is Preparing || this is Installing || this is AwaitingUser
}

data class InstallRequest(
    val appId: String,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val apkPath: String,
    val expectedSha256: String? = null,
    val method: InstallMethod = InstallMethod.Session,
    val allowDowngrade: Boolean = false,
    val verifyChecksum: Boolean = true,
)

enum class InstallerAvailability { Available, NeedsPermission, NotInstalled, Unavailable }

/** Live capability report rendered on the install-settings screens. */
data class InstallCapabilities(
    val sessionAvailable: Boolean = true,
    val installPermissionGranted: Boolean = false,
    val unknownSourcesGranted: Boolean = false,
    val rootGranted: Boolean = false,
    val rootChecked: Boolean = false,
    val suPath: String? = null,
    val shizukuInstalled: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuPermissionGranted: Boolean = false,
    val shizukuVersion: Int = 0,
    val shizukuUid: Int = -1,
    val storageWritable: Boolean = true,
) {
    fun statusOf(method: InstallMethod): InstallerAvailability = when (method) {
        InstallMethod.Session -> if (unknownSourcesGranted || installPermissionGranted)
            InstallerAvailability.Available else InstallerAvailability.NeedsPermission
        InstallMethod.Intent -> if (installPermissionGranted) InstallerAvailability.Available
        else InstallerAvailability.NeedsPermission
        InstallMethod.Shizuku -> when {
            !shizukuInstalled -> InstallerAvailability.NotInstalled
            !shizukuRunning -> InstallerAvailability.Unavailable
            !shizukuPermissionGranted -> InstallerAvailability.NeedsPermission
            else -> InstallerAvailability.Available
        }
        InstallMethod.Root -> when {
            !rootChecked -> InstallerAvailability.Unavailable
            rootGranted -> InstallerAvailability.Available
            else -> InstallerAvailability.NeedsPermission
        }
        InstallMethod.Ask -> InstallerAvailability.Available
    }

    /** Best privileged method for silent installs, if any (used by auto-update). */
    val silentMethod: InstallMethod?
        get() = when {
            shizukuInstalled && shizukuRunning && shizukuPermissionGranted -> InstallMethod.Shizuku
            rootGranted -> InstallMethod.Root
            else -> null
        }

    /** One-line status shown on the installer settings screens. */
    fun healthSummary(): String = when {
        shizukuInstalled && shizukuRunning && shizukuPermissionGranted -> "Shizuku ready (uid $shizukuUid)"
        shizukuInstalled && shizukuRunning -> "Shizuku running — permission not granted"
        shizukuInstalled -> "Shizuku installed but not running"
        rootGranted -> "Root granted"
        rootChecked -> "No root access"
        installPermissionGranted -> "Install permission granted"
        else -> "Only the Android installer is available"
    }

    fun labelFor(method: InstallMethod): String = when (statusOf(method)) {
        InstallerAvailability.Available -> "Ready"
        InstallerAvailability.NeedsPermission -> "Needs permission"
        InstallerAvailability.NotInstalled -> "Not installed"
        InstallerAvailability.Unavailable -> "Unavailable"
    }

    companion object {
        val SupportedAbis: List<String> get() = Build.SUPPORTED_ABIS.toList()
    }
}

/** Reasons shown by the "Report app" screen — mirrors the Worker's `reports` table. */
val REPORT_REASONS: List<String> = listOf(
    "Malware or virus",
    "Fake or impersonating app",
    "Broken or crashes",
    "Misleading content",
    "Inappropriate content",
    "Spam or repetitive",
    "Copyright or trademark",
    "Other",
)
