package com.gothwad.store.ui

import com.gothwad.store.data.download.DownloadStatus
import com.gothwad.store.data.download.DownloadTask
import com.gothwad.store.data.download.progress
import com.gothwad.store.data.install.InstallPhase
import com.gothwad.store.data.model.StoreRelease
import kotlin.math.roundToInt

/** What the round install button on a row/card should do right now. */
sealed interface InstallAction {
    data object Install : InstallAction
    data object Update : InstallAction
    data object Open : InstallAction
    data class Downloading(val progress: Float) : InstallAction
    data class Paused(val progress: Float) : InstallAction
    data class Installing(val progress: Float) : InstallAction
    data object Retry : InstallAction
    data object Unavailable : InstallAction
    data object Busy : InstallAction

    val label: String
        get() = when (this) {
            Install -> "Install"
            Update -> "Update"
            Open -> "Open"
            is Downloading -> "${(progress * 100f).roundToInt()}%"
            is Paused -> "Resume"
            is Installing -> "Installing"
            Retry -> "Retry"
            Unavailable -> "Unavailable"
            Busy -> "Working…"
        }
}

/**
 * Pure mapping from (installed version, newest release, download job, install
 * phase) to a button action. Kept free of Android types so it is unit tested.
 */
fun resolveInstallAction(
    installedVersion: Long?,
    release: StoreRelease?,
    task: DownloadTask?,
    phase: InstallPhase,
): InstallAction {
    if (phase is InstallPhase.Installing || phase is InstallPhase.Preparing) {
        val progress = (phase as? InstallPhase.Installing)?.progress ?: 0f
        return InstallAction.Installing(progress)
    }
    if (task != null) {
        when (task.status) {
            DownloadStatus.RUNNING -> return InstallAction.Downloading(task.progress)
            DownloadStatus.QUEUED -> return InstallAction.Busy
            DownloadStatus.PAUSED -> return InstallAction.Paused(task.progress)
            DownloadStatus.FAILED -> return if (task.filePath != null && task.totalBytes > 0 &&
                task.bytesDownloaded >= task.totalBytes
            ) {
                if (installedVersion != null && installedVersion >= task.versionCode) InstallAction.Open
                else InstallAction.Install
            } else InstallAction.Retry
            DownloadStatus.CANCELLED -> return InstallAction.Retry
            DownloadStatus.COMPLETED -> return when {
                installedVersion == null -> InstallAction.Install
                installedVersion < task.versionCode -> InstallAction.Update
                else -> InstallAction.Open
            }
            DownloadStatus.INSTALLED -> return when {
                release != null && (installedVersion ?: 0L) < release.versionCode -> InstallAction.Update
                installedVersion != null -> InstallAction.Open
                else -> InstallAction.Install
            }
        }
    }
    if (release == null) {
        return if (installedVersion != null) InstallAction.Open else InstallAction.Unavailable
    }
    return when {
        installedVersion == null -> InstallAction.Install
        installedVersion < release.versionCode -> InstallAction.Update
        else -> InstallAction.Open
    }
}

/** Releases a device can install come from the Worker's own filtering rules. */
fun isCompatible(release: StoreRelease, sdk: Int): Boolean = (release.minSdk ?: 1) <= sdk
