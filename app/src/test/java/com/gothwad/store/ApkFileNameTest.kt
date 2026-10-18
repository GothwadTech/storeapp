package com.gothwad.store

import com.gothwad.store.data.download.ApkDownloader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The downloaded APK is written to disk with a name built from publisher
 * supplied strings. A name with a slash or a space would make the download
 * fail (or escape the download folder), so the sanitising is verified here.
 */
class ApkFileNameTest {

    private fun name(
        packageName: String = "com.gothwad.browser",
        appId: String = "app-1",
        versionName: String = "1.0.137",
        versionCode: Long = 137,
    ): String = ApkDownloader.apkFileName(packageName, appId, versionName, versionCode)

    @Test
    fun `normal package and version are kept`() {
        assertEquals("com.gothwad.browser-1.0.137.apk", name())
    }

    @Test
    fun `slashes and spaces in the version name are replaced`() {
        val fileName = name(versionName = "1.0 beta/2 final")
        assertEquals("1.0_beta_2_final", fileName.removeSuffix(".apk").substringAfter("-", ""))
        assertFalse(fileName.contains("/"))
        assertFalse(fileName.contains(" "))
        assertTrue(fileName.endsWith(".apk"))
    }

    @Test
    fun `illegal characters never survive`() {
        val fileName = name(packageName = "com.gothwad:bröwser?", versionName = "a:b*c")
        assertTrue(fileName.matches(Regex("[A-Za-z0-9._-]+\\.apk")), "unexpected file name: $fileName")
    }

    @Test
    fun `the version code is used when the version name is empty`() {
        assertEquals("com.gothwad.browser-137.apk", name(versionName = ""))
    }

    @Test
    fun `the app id is used when the package name is empty`() {
        assertEquals("fdroid_com.example-1.0.apk", name(packageName = "", appId = "fdroid:com.example", versionName = "1.0"))
    }

    @Test
    fun `very long names are capped`() {
        val fileName = name(packageName = "com.gothwad." + "a".repeat(200), versionName = "1.0")
        assertTrue(fileName.length <= 104, "file name is too long: ${fileName.length}")
        assertTrue(fileName.endsWith(".apk"))
    }
}
