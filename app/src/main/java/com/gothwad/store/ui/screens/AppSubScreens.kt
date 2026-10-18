package com.gothwad.store.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.gothwad.store.core.Format
import com.gothwad.store.data.model.AppDetailResponse
import com.gothwad.store.data.model.StoreRelease
import com.gothwad.store.ui.Routes
import com.gothwad.store.ui.StoreViewModel
import com.gothwad.store.ui.UiState
import com.gothwad.store.ui.components.ErrorState
import com.gothwad.store.ui.components.GothwadScaffold
import com.gothwad.store.ui.components.GothwadTopBar
import com.gothwad.store.ui.components.InfoBanner
import com.gothwad.store.ui.components.KeyValueRow
import com.gothwad.store.ui.components.SectionHeader
import com.gothwad.store.ui.components.SkeletonList
import com.gothwad.store.ui.theme.GothwadTheme
import com.gothwad.store.ui.theme.Spacing

/** Tiny helper so every read-only sub-screen loads the same detail object. */
@Composable
private fun DetailHost(
    vm: StoreViewModel,
    nav: NavController,
    appId: String,
    title: String,
    content: @Composable (AppDetailResponse) -> Unit,
) {
    val state by vm.detail.collectAsStateWithLifecycle()
    LaunchedEffect(appId) {
        if (state !is UiState.Ready) vm.openApp(appId)
    }
    GothwadScaffold(
        topBar = {
            GothwadTopBar(
                title = title,
                onBack = { nav.popBackStack() },
                actions = {
                    DownloadsActionInline(nav)
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                UiState.Loading -> SkeletonList(rows = 5)
                is UiState.Failed -> ErrorState(current.message, onRetry = { vm.openApp(appId) })
                is UiState.Ready -> content(current.value)
            }
        }
    }
}

@Composable
private fun DownloadsActionInline(nav: NavController) {
    com.gothwad.store.ui.components.DownloadsAction { nav.navigate(Routes.DOWNLOADS) }
}

@Composable
fun ScreenshotsScreen(vm: StoreViewModel, nav: NavController, appId: String) {
    DetailHost(vm, nav, appId, "Screenshots") { detail ->
        LazyColumn(
            contentPadding = PaddingValues(Spacing.screenH, 12.dp, Spacing.screenH, Spacing.bottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    "${detail.screenshots.size} screenshots · ${detail.app.name}",
                    fontSize = 13.sp,
                    color = GothwadTheme.extra.textSecondary,
                )
            }
            items(detail.screenshots) { url ->
                AsyncImage(
                    model = url,
                    contentDescription = "Screenshot of ${detail.app.name}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

@Composable
fun WhatsNewScreen(vm: StoreViewModel, nav: NavController, appId: String) {
    DetailHost(vm, nav, appId, "What's new") { detail ->
        val releases = detail.installableReleases(android.os.Build.VERSION.SDK_INT)
        LazyColumn(
            contentPadding = PaddingValues(Spacing.screenH, 12.dp, Spacing.screenH, Spacing.bottomContentPadding),
        ) {
            if (releases.isEmpty()) {
                item {
                    InfoBanner(
                        title = "No release notes",
                        message = "This listing has no approved release for your device yet.",
                        tone = com.gothwad.store.ui.components.BannerTone.Warning,
                    )
                }
            }
            items(releases) { release ->
                ReleaseCard(release, isLive = release.id == detail.liveRelease?.id)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: StoreRelease, isLive: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Update, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(
                "Version ${release.versionName} (${release.versionCode})",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            if (isLive) {
                com.gothwad.store.ui.components.PillLabel(
                    text = "Latest",
                    background = GothwadTheme.extra.successContainer,
                    contentColor = GothwadTheme.extra.success,
                )
            }
        }
        Text(
            release.changelog.ifBlank { "No release notes were provided." },
            fontSize = 13.sp,
            color = GothwadTheme.extra.textSecondary,
            lineHeight = 19.sp,
        )
        Text(
            "${Format.bytes(release.apkSizeBytes)} · min API ${release.minSdk ?: 1} · ${release.apkProvider}",
            fontSize = 11.sp,
            color = GothwadTheme.extra.textTertiary,
        )
    }
}

@Composable
fun AppPermissionsScreen(vm: StoreViewModel, nav: NavController, appId: String) {
    DetailHost(vm, nav, appId, "Permissions") { detail ->
        val release = detail.downloadableRelease(android.os.Build.VERSION.SDK_INT)
            ?: detail.liveRelease
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.screenH, 12.dp, Spacing.screenH, Spacing.bottomContentPadding),
        ) {
            item {
                InfoBanner(
                    title = "Read from the published APK",
                    message = "These permissions come from the build manifest that the reviewer " +
                        "approved, not from a marketing list.",
                )
            }
            item { SectionHeader(title = "Requested permissions") }
            val permissions = release?.permissions.orEmpty()
            if (permissions.isEmpty()) {
                item {
                    Text(
                        "This build requests no extra permissions.",
                        fontSize = 13.sp,
                        color = GothwadTheme.extra.textSecondary,
                        modifier = Modifier.padding(horizontal = Spacing.screenH),
                    )
                }
            } else {
                items(permissions) { permission ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.screenH, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Security,
                            contentDescription = null,
                            tint = GothwadTheme.extra.textTertiary,
                            modifier = Modifier.size(17.dp),
                        )
                        Column {
                            Text(Format.permission(permission), fontSize = 14.sp, color = GothwadTheme.extra.textPrimary)
                            Text(permission, fontSize = 11.sp, color = GothwadTheme.extra.textTertiary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppInfoScreen(vm: StoreViewModel, nav: NavController, appId: String) {
    DetailHost(vm, nav, appId, "Technical details") { detail ->
        val app = detail.app
        val release = detail.downloadableRelease(android.os.Build.VERSION.SDK_INT)
        LazyColumn(
            contentPadding = PaddingValues(Spacing.screenH, 8.dp, Spacing.screenH, Spacing.bottomContentPadding),
        ) {
            item { SectionHeader(title = "Listing") }
            item {
                Column {
                    KeyValueRow("Package", app.packageName)
                    KeyValueRow("App id", app.id)
                    KeyValueRow("Developer", app.developerName.ifBlank { "—" })
                    KeyValueRow("Developer id", app.developerId.ifBlank { "—" })
                    KeyValueRow("Category", app.category.ifBlank { "—" })
                    KeyValueRow("Kind", app.kind)
                    KeyValueRow("Status", app.status)
                    KeyValueRow("Support", app.supportEmail.ifBlank { "—" })
                    KeyValueRow("Published", Format.isoDate(app.createdAt))
                    KeyValueRow("Updated", Format.isoDate(app.updatedAt))
                }
            }
            item { SectionHeader(title = "Release") }
            item {
                Column {
                    if (release == null) {
                        Text(
                            "No compatible approved release for this device.",
                            fontSize = 13.sp,
                            color = GothwadTheme.extra.textSecondary,
                        )
                    } else {
                        KeyValueRow("Version", "${release.versionName} (${release.versionCode})")
                        KeyValueRow("Size", Format.bytes(release.apkSizeBytes))
                        KeyValueRow("Minimum Android", "API ${release.minSdk ?: 1}")
                        KeyValueRow("Target Android", "API ${release.targetSdk ?: "—"}")
                        KeyValueRow("Host", release.apkProvider)
                        KeyValueRow("Review", release.reviewStatus)
                        KeyValueRow("SHA-256", release.apkSha256 ?: "Not published")
                    }
                }
            }
            item { SectionHeader(title = "Your device") }
            item {
                Column {
                    KeyValueRow("Android", "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                    KeyValueRow("ABIs", android.os.Build.SUPPORTED_ABIS.joinToString(", "))
                    KeyValueRow("Installed", com.gothwad.store.core.AppGraph.library
                        .installedVersion(app.packageName)?.toString() ?: "Not installed")
                }
            }
            item {
                Box(Modifier.padding(top = 12.dp)) {
                    Text(
                        "The SHA-256 above is the publisher's checksum. Kiwi Store verifies every " +
                            "download against it before installing (see Download settings).",
                        fontSize = 12.sp,
                        color = GothwadTheme.extra.textTertiary,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}
