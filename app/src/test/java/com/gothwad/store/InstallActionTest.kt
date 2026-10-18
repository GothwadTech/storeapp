package com.gothwad.store

import com.gothwad.store.data.download.DownloadStatus
import com.gothwad.store.data.download.DownloadTask
import com.gothwad.store.data.install.InstallMethod
import com.gothwad.store.data.install.InstallPhase
import com.gothwad.store.data.model.StoreRelease
import com.gothwad.store.ui.InstallAction
import com.gothwad.store.ui.resolveInstallAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The install button is the single most important control in the app: every
 * combination of (installed version, published release, download job, install
 * phase) must resolve to exactly one action.
 */
class InstallActionTest {

    private fun release(versionCode: Long, minSdk: Int? = 24) = StoreRelease(
        id = "rel-$versionCode",
        appId = "app-1",
        versionCode = versionCode,
        versionName = "1.0.$versionCode",
        apkUrl = "https://kiwistore.gothwadtech.com/api/download/rel-$versionCode",
        apkSizeBytes = 4_274_549,
        apkSha256 = "cc08fc96d2c386679d4be4058610e8924d206f5976eedbd1bfe7921e7ce423f0",
        minSdk = minSdk,
        targetSdk = 36,
        isLive = true,
        reviewStatus = "approved",
    )

    private fun task(
        status: DownloadStatus,
        bytes: Long = 0,
        total: Long = 4_274_549,
        filePath: String? = null,
    ) = DownloadTask(
        id = "rel-137",
        appId = "app-1",
        appName = "Gothwad Browser",
        packageName = "com.gothwad.browser",
        versionName = "1.0.137",
        versionCode = 137,
        method = InstallMethod.Session,
        status = status,
        bytesDownloaded = bytes,
        totalBytes = total,
        filePath = filePath,
    )

    @Test
    fun `fresh app with a published release offers install`() {
        val action = resolveInstallAction(null, release(137), null, InstallPhase.Idle)
        assertEquals(InstallAction.Install, action)
    }

    @Test
    fun `older installed version offers update`() {
        val action = resolveInstallAction(100L, release(137), null, InstallPhase.Idle)
        assertEquals(InstallAction.Update, action)
    }

    @Test
    fun `same version offers open`() {
        val action = resolveInstallAction(137L, release(137), null, InstallPhase.Idle)
        assertEquals(InstallAction.Open, action)
    }

    @Test
    fun `newer installed build is not downgraded`() {
        val action = resolveInstallAction(200L, release(137), null, InstallPhase.Idle)
        assertEquals(InstallAction.Open, action)
    }

    @Test
    fun `no compatible release is unavailable`() {
        val action = resolveInstallAction(null, null, null, InstallPhase.Idle)
        assertEquals(InstallAction.Unavailable, action)
    }

    @Test
    fun `running download shows percentage`() {
        val action = resolveInstallAction(null, release(137), task(DownloadStatus.RUNNING, bytes = 2_137_274), InstallPhase.Idle)
        assertTrue(action is InstallAction.Downloading)
        assertEquals("50%", action.label)
    }

    @Test
    fun `paused download shows resume`() {
        val action = resolveInstallAction(null, release(137), task(DownloadStatus.PAUSED, bytes = 1_000), InstallPhase.Idle)
        assertTrue(action is InstallAction.Paused)
        assertEquals("Resume", action.label)
    }

    @Test
    fun `queued download is busy`() {
        val action = resolveInstallAction(null, release(137), task(DownloadStatus.QUEUED), InstallPhase.Idle)
        assertEquals(InstallAction.Busy, action)
    }

    @Test
    fun `failed download with an incomplete file offers retry`() {
        val action = resolveInstallAction(
            null,
            release(137),
            task(DownloadStatus.FAILED, bytes = 1_000, filePath = "/tmp/app.apk"),
            InstallPhase.Idle,
        )
        assertEquals(InstallAction.Retry, action)
    }

    @Test
    fun `failed job whose file is complete still installs`() {
        val action = resolveInstallAction(
            null,
            release(137),
            task(DownloadStatus.FAILED, bytes = 4_274_549, filePath = "/tmp/app.apk"),
            InstallPhase.Idle,
        )
        assertEquals(InstallAction.Install, action)
    }

    @Test
    fun `completed download offers install when nothing is installed`() {
        val action = resolveInstallAction(
            null,
            release(137),
            task(DownloadStatus.COMPLETED, bytes = 4_274_549, filePath = "/tmp/app.apk"),
            InstallPhase.Idle,
        )
        assertEquals(InstallAction.Install, action)
    }

    @Test
    fun `completed download for an older installed version offers update`() {
        val action = resolveInstallAction(
            100L,
            release(137),
            task(DownloadStatus.COMPLETED, bytes = 4_274_549, filePath = "/tmp/app.apk"),
            InstallPhase.Idle,
        )
        assertEquals(InstallAction.Update, action)
    }

    @Test
    fun `installed download opens the app`() {
        val action = resolveInstallAction(
            137L,
            release(137),
            task(DownloadStatus.INSTALLED, bytes = 4_274_549, filePath = "/tmp/app.apk"),
            InstallPhase.Idle,
        )
        assertEquals(InstallAction.Open, action)
    }

    @Test
    fun `install phase always wins over the download status`() {
        val installing = resolveInstallAction(
            null,
            release(137),
            task(DownloadStatus.COMPLETED, bytes = 4_274_549, filePath = "/tmp/app.apk"),
            InstallPhase.Installing(0.4f, "Installing Gothwad Browser…"),
        )
        assertTrue(installing is InstallAction.Installing)
        assertEquals(0.4f, (installing as InstallAction.Installing).progress, 0.001f)

        val preparing = resolveInstallAction(null, release(137), null, InstallPhase.Preparing)
        assertTrue(preparing is InstallAction.Installing)
    }

    @Test
    fun `incompatible release is filtered out by isCompatible`() {
        val needsApi33 = release(137, minSdk = 33)
        assertTrue(!com.gothwad.store.ui.isCompatible(needsApi33, 30))
        assertTrue(com.gothwad.store.ui.isCompatible(needsApi33, 33))
    }

    @Test
    fun `states are distinguishable for a retry after cancel`() {
        val action = resolveInstallAction(
            null,
            release(137),
            task(DownloadStatus.CANCELLED, bytes = 500),
            InstallPhase.Idle,
        )
        assertEquals(InstallAction.Retry, action)
    }
}
