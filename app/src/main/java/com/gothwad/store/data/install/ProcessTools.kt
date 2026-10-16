package com.gothwad.store.data.install

import android.os.Build
import androidx.annotation.RequiresApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Waits for a `su`/`sh` helper process to exit.
 *
 * [Process.waitFor] with a timeout only exists from API 26 while the app still
 * ships to API 24, so older devices get a watchdog thread instead.
 */
internal fun Process.awaitExit(seconds: Long): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        return awaitExitOreo(seconds)
    }
    val exited = CountDownLatch(1)
    val watchdog = Thread {
        runCatching { waitFor() }
        exited.countDown()
    }
    watchdog.isDaemon = true
    watchdog.start()
    return exited.await(seconds, TimeUnit.SECONDS)
}

@RequiresApi(Build.VERSION_CODES.O)
private fun Process.awaitExitOreo(seconds: Long): Boolean = waitFor(seconds, TimeUnit.SECONDS)

/** Force-kills a helper that ignored its timeout, without API 26-only calls. */
internal fun Process.terminate() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        terminateOreo()
    } else {
        runCatching { destroy() }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun Process.terminateOreo() {
    runCatching { destroyForcibly() }
}
