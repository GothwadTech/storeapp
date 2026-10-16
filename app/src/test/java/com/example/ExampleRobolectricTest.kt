package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.*
import com.example.ui.MainStoreScreen
import com.example.ui.StoreViewModel
import com.example.ui.theme.GothwadStoreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Gothwad Store", context.getString(R.string.app_name))
    }

    @Test fun `render live-contract listing and open detail without production network`() {
        val app = StoreApp("app-1", "com.gothwad.notes", "Test Notes", "Gothwad",
            "tools", "app", "published", fullDesc = "A real-contract test listing")
        val api = object : StoreApi {
            override suspend fun catalog(kind: String?, category: String?, query: String?) =
                CatalogResponse(listOf(app), "live")
            override suspend fun categories() = CategoriesResponse(emptyList(), "live")
            override suspend fun detail(id: String) = AppDetailResponse(app)
        }
        val model = StoreViewModel(StoreRepository(api))
        composeTestRule.setContent { GothwadStoreTheme { MainStoreScreen(model) } }
        composeTestRule.onNodeWithText("Test Notes").assertExists().performClick()
        composeTestRule.onNodeWithText("A real-contract test listing").assertExists()
        composeTestRule.onNodeWithText("No approved release compatible with this device is available.").assertExists()
    }
}
