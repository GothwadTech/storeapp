package com.gothwad.store

import com.gothwad.store.data.model.StoreApp
import com.gothwad.store.ui.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bottom bar has five tabs: Home, Apps, Kiwi, Games, Profile. Search is
 * not a tab any more — every tab header opens it — and the Kiwi tab shows the
 * listings that are live on the Kiwi Store web.
 */
class KiwiTabTest {

    @Test
    fun `kiwi replaces search in the tab bar`() {
        assertEquals(
            listOf(Routes.HOME, Routes.APPS, Routes.KIWI, Routes.GAMES, Routes.ACCOUNT),
            Routes.tabs,
        )
        assertFalse(Routes.tabs.contains(Routes.SEARCH))
        assertEquals(5, Routes.tabs.size)
    }

    @Test
    fun `only web store listings belong to the kiwi tab`() {
        val webListing = StoreApp(
            id = "app-44e4ba84",
            packageName = "com.gothwad.browser",
            name = "Gothwad Browser",
        )
        val fdroidListing = StoreApp(
            id = "fdroid:org.fdroid.fdroid",
            packageName = "org.fdroid.fdroid",
            name = "F-Droid",
            source = "F-Droid",
        )

        assertTrue(webListing.isKiwiStoreListing)
        assertFalse(fdroidListing.isKiwiStoreListing)
    }
}
