package com.gothwad.store

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gothwad.store.core.AppGraph
import com.gothwad.store.data.settings.ThemeMode
import com.gothwad.store.ui.KiwiApp
import com.gothwad.store.ui.theme.KiwiStoreTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppGraph.ensure(applicationContext)
        handleIntent(intent)
        setContent {
            val settings = AppGraph.settings.settings
                .collectAsStateWithLifecycle(initialValue = null)
            KiwiStoreTheme(themeMode = settings.value?.themeMode ?: ThemeMode.System) {
                KiwiApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** OAuth redirects and notification taps both arrive here. */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val data: Uri? = intent.data
        if (data != null && AppGraph.hasAuth) {
            AppGraph.auth.handleRedirect(data)
        }
        intent.getStringExtra(EXTRA_ROUTE)?.let { route ->
            AppGraph.requestNavigation(route, intent.getStringExtra(EXTRA_TASK_ID))
        }
    }

    companion object {
        const val EXTRA_ROUTE = "gothwad_route"
        const val EXTRA_TASK_ID = "gothwad_task_id"
    }
}
