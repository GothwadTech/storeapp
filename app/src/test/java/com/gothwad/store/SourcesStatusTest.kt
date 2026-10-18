package com.gothwad.store

import com.gothwad.store.data.remote.FdroidCatalog
import com.gothwad.store.data.remote.FdroidRepoStatus
import com.gothwad.store.data.remote.FdroidStatus
import com.gothwad.store.ui.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "App sources" screen tells the user which repositories Kiwi Store
 * mirrors, how big they are and what is running right now — so the status
 * model has to know both repos and add their numbers up.
 */
class SourcesStatusTest {

    @Test
    fun `both upstream repositories are known`() {
        val status = FdroidStatus()

        assertEquals("F-Droid", status.fdroid.name)
        assertEquals(FdroidCatalog.REPO_URL, status.fdroid.repoUrl)
        assertEquals(FdroidCatalog.INDEX_URL, status.fdroid.indexUrl)
        assertEquals("IzzyOnDroid", status.izzy.name)
        assertEquals(FdroidCatalog.IZZY_REPO_URL, status.izzy.repoUrl)
        assertEquals(2, status.repos.size)
        assertTrue(status.mirrors.isNotEmpty())
        assertEquals(0, status.totalApps)
        assertFalse(status.syncing)
    }

    @Test
    fun `totals add up over both repositories`() {
        val status = FdroidStatus(
            fdroid = FdroidRepoStatus(
                name = "F-Droid",
                repoUrl = FdroidCatalog.REPO_URL,
                indexUrl = FdroidCatalog.INDEX_URL,
                apps = 3000,
                releases = 2900,
                indexBytes = 9_000_000L,
            ),
            izzy = FdroidRepoStatus(
                name = "IzzyOnDroid",
                repoUrl = FdroidCatalog.IZZY_REPO_URL,
                indexUrl = FdroidCatalog.IZZY_INDEX_URL,
                apps = 200,
                releases = 190,
                indexBytes = 1_600_000L,
            ),
        )

        assertEquals(3200, status.totalApps)
        assertEquals(3090, status.totalReleases)
        assertEquals(10_600_000L, status.totalIndexBytes)
    }

    @Test
    fun `the sources screen has its own route`() {
        assertEquals("settings/sources", Routes.SOURCES)
    }
}
