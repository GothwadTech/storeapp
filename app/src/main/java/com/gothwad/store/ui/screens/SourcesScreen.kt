package com.gothwad.store.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.gothwad.store.BuildConfig
import com.gothwad.store.core.AppGraph
import com.gothwad.store.core.Format
import com.gothwad.store.data.auth.AuthState
import com.gothwad.store.data.download.isActive
import com.gothwad.store.data.remote.FdroidRepoStatus
import com.gothwad.store.ui.NetworkStatus
import com.gothwad.store.ui.Routes
import com.gothwad.store.ui.StoreViewModel
import com.gothwad.store.ui.UiState
import com.gothwad.store.ui.components.BannerTone
import com.gothwad.store.ui.components.GothwadScaffold
import com.gothwad.store.ui.components.InfoBanner
import com.gothwad.store.ui.components.KeyValueRow
import com.gothwad.store.ui.components.OutlineButton
import com.gothwad.store.ui.components.SectionHeader
import com.gothwad.store.ui.components.StatTile
import com.gothwad.store.ui.components.SubScreenHeader
import com.gothwad.store.ui.theme.GothwadTheme
import com.gothwad.store.ui.theme.Spacing

/**
 * "App sources" — everything the app is running on, in one place: the Kiwi
 * Store web catalogue, the F-Droid / IzzyOnDroid mirrors with their repo
 * URLs, index sizes, listing counts and sync times, plus a live view of what
 * is running right now (catalogue sync, download jobs, installer back-ends,
 * network and account).
 */
@Composable
fun SourcesScreen(vm: StoreViewModel, nav: NavController) {
    val status by vm.fdroidStatus.collectAsStateWithLifecycle()
    val fdroid by vm.fdroid.collectAsStateWithLifecycle()
    val kiwi by vm.kiwi.collectAsStateWithLifecycle()
    val tasks by vm.downloads.collectAsStateWithLifecycle()
    val capabilities by AppGraph.installer.capabilities.collectAsStateWithLifecycle()
    val authState by AppGraph.auth.state.collectAsStateWithLifecycle()
    var notice by rememberSaveable { mutableStateOf<String?>(null) }

    val webApps = (kiwi as? UiState.Ready)?.value?.apps?.size ?: 0
    val mergedApps = fdroid.apps.size
    val activeJobs = tasks.count { it.isActive }
    val fdroidJobs = tasks.count { it.appId.startsWith("fdroid:") }
    val fdroidBytes = tasks.filter { it.appId.startsWith("fdroid:") }.sumOf { it.bytesDownloaded }
    val signedIn = authState as? AuthState.SignedIn

    GothwadScaffold(
        topBar = {
            SubScreenHeader(
                title = "App sources",
                subtitle = "What Kiwi Store is running on",
                onBack = { nav.popBackStack() },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.screenH),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatTile("Web store", "$webApps", modifier = Modifier.weight(1f))
                    StatTile("F-Droid", "$mergedApps", modifier = Modifier.weight(1f))
                    StatTile("APKs", Format.bytes(AppGraph.downloader.usedBytes()), modifier = Modifier.weight(1f))
                }
            }

            item { SectionHeader(title = "Kiwi Store (first-party)") }
            item {
                Column {
                    KeyValueRow("Web store", BuildConfig.STORE_WEB_URL)
                    KeyValueRow("API", BuildConfig.STORE_API_BASE_URL)
                    KeyValueRow("Listings", "$webApps")
                    KeyValueRow("Status", "Connected — this is the Kiwi tab")
                }
            }

            item { SectionHeader(title = "F-Droid") }
            item { RepoRows(status.fdroid) }
            item { SectionHeader(title = "IzzyOnDroid") }
            item { RepoRows(status.izzy) }

            item { SectionHeader(title = "Failover mirrors") }
            item {
                Column {
                    KeyValueRow("Mirrors", "${status.mirrors.size} HTTPS hosts")
                    status.mirrors.forEach { mirror ->
                        KeyValueRow("·", mirror)
                    }
                }
            }

            item { SectionHeader(title = "Downloads") }
            item {
                Column {
                    KeyValueRow("Jobs", "${tasks.size}")
                    KeyValueRow("Running", "$activeJobs")
                    KeyValueRow("F-Droid APKs", "$fdroidJobs · ${Format.bytes(fdroidBytes)}")
                    KeyValueRow("APK bytes on device", Format.bytes(AppGraph.downloader.usedBytes()))
                    KeyValueRow("Where the bytes come from", "F-Droid / IzzyOnDroid host the APKs")
                }
            }

            item { SectionHeader(title = "Running right now") }
            item {
                Column {
                    KeyValueRow(
                        "Catalogue sync",
                        if (status.syncing) "Syncing the F-Droid index…" else "Idle",
                    )
                    KeyValueRow("Download service", "$activeJobs active job(s)")
                    KeyValueRow("Install back-ends", capabilities.healthSummary())
                    KeyValueRow("Network", NetworkStatus.networkLabel())
                    KeyValueRow(
                        "Account",
                        signedIn?.user?.displayName?.let { "Signed in as $it" } ?: "Not signed in",
                    )
                }
            }

            status.lastError?.let { error ->
                item {
                    Column(Modifier.padding(Spacing.screenH)) {
                        InfoBanner(
                            title = "F-Droid problem",
                            message = error,
                            tone = BannerTone.Warning,
                        )
                    }
                }
            }

            notice?.let { text ->
                item {
                    Column(Modifier.padding(Spacing.screenH)) {
                        InfoBanner(title = "Done", message = text)
                    }
                }
            }

            item {
                Column(Modifier.padding(Spacing.screenH)) {
                    Spacer(Modifier.height(10.dp))
                    OutlineButton(
                        label = "Refresh the F-Droid catalogue",
                        onClick = {
                            vm.retryFdroid()
                            notice = "F-Droid and IzzyOnDroid are syncing again."
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlineButton(
                        label = "Open download history",
                        onClick = { nav.navigate(Routes.DOWNLOAD_HISTORY) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "F-Droid APKs are downloaded straight from the repositories above — " +
                            "they are not hosted, proxied or counted by Gothwad.",
                        fontSize = 12.sp,
                        color = GothwadTheme.extra.textTertiary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RepoRows(repo: FdroidRepoStatus) {
    Column {
        KeyValueRow("Repository", repo.repoUrl)
        KeyValueRow("Index", repo.indexUrl)
        KeyValueRow("Apps", "${repo.apps}")
        KeyValueRow("Releases", "${repo.releases}")
        KeyValueRow("Index size", Format.bytes(repo.indexBytes))
        KeyValueRow(
            "Last sync",
            if (repo.lastSyncedAt > 0L) Format.relative(repo.lastSyncedAt) else "Never",
        )
        KeyValueRow(
            "Status",
            when {
                repo.syncing -> "Syncing…"
                !repo.error.isNullOrBlank() -> repo.error.orEmpty()
                repo.apps > 0 -> "Ready"
                else -> "Not downloaded yet"
            },
        )
    }
}
