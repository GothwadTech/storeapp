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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.gothwad.store.core.AppGraph
import com.gothwad.store.core.Format
import com.gothwad.store.data.library.InstalledApp
import com.gothwad.store.data.model.StoreApp
import com.gothwad.store.data.model.StoreRelease
import com.gothwad.store.ui.Routes
import com.gothwad.store.ui.StoreEvent
import com.gothwad.store.ui.StoreViewModel
import com.gothwad.store.ui.components.AppIcon
import com.gothwad.store.ui.components.AppRowItem
import com.gothwad.store.ui.components.DividerLine
import com.gothwad.store.ui.components.EmptyState
import com.gothwad.store.ui.components.GothwadScaffold
import com.gothwad.store.ui.components.GothwadTopBar
import com.gothwad.store.ui.components.InfoBanner
import com.gothwad.store.ui.components.OutlineButton
import com.gothwad.store.ui.components.PillLabel
import com.gothwad.store.ui.components.PrimaryButton
import com.gothwad.store.ui.components.SectionHeader
import com.gothwad.store.ui.components.SegmentedRow
import com.gothwad.store.ui.theme.GothwadTheme
import com.gothwad.store.ui.theme.Spacing

private enum class ManageTab(val label: String) {
    Updates("Updates"),
    Library("Library"),
    OnDevice("On this device"),
}

/**
 * The Play-Store "Manage apps & device" hub, extended with the Gothwad library,
 * wishlist and update-all controls.
 */
@Composable
fun MyAppsScreen(vm: StoreViewModel, nav: NavController, initialTab: String? = null) {
    val libraryState by AppGraph.library.state.collectAsStateWithLifecycle()
    val known by vm.knownApps.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable {
        mutableStateOf(
            when (initialTab?.lowercase()) {
                "updates" -> ManageTab.Updates
                "on_device", "device" -> ManageTab.OnDevice
                else -> ManageTab.Updates
            },
        )
    }

    LaunchedEffect(Unit) {
        AppGraph.library.load()
        vm.refreshApps()
    }

    val storeApps = known.values.toList()
    val installed = libraryState.installed
    val releases by vm.releases.collectAsStateWithLifecycle()
    val updates = remember(storeApps, installed, releases) {
        storeApps.mapNotNull { app ->
            val installedVersion = installed[app.packageName] ?: return@mapNotNull null
            val newest = releases[app.id] ?: return@mapNotNull null
            if (newest.versionCode > installedVersion) app to newest else null
        }
    }
    val libraryApps = remember(storeApps, libraryState.remoteLibrary) {
        val ids = libraryState.remoteLibrary.ifEmpty { storeApps.map { it.id } }
        storeApps.filter { it.id in ids }
    }

    GothwadScaffold(
        topBar = { GothwadTopBar(title = "Manage apps & device", subtitle = "Kiwi Store library") },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SegmentedRow(
                options = ManageTab.entries.map { it.label },
                selectedIndex = tab.ordinal,
                onSelect = { tab = ManageTab.entries[it] },
            )

            when (tab) {
                ManageTab.Updates -> UpdatesContent(
                    updates = updates,
                    installedCount = installed.size,
                    onOpen = { nav.navigate(Routes.app(it)) },
                    onAction = { vm.onAppAction(it) },
                    onUpdateAll = {
                        updates.forEach { (app, _) -> vm.onAppAction(app) }
                        vm.emit(StoreEvent.Message("Queued ${updates.size} update${if (updates.size == 1) "" else "s"}."))
                    },
                    onManageDownloads = { nav.navigate(Routes.DOWNLOADS) },
                    onOpenLibrary = { nav.navigate(Routes.WISHLIST) },
                    vm = vm,
                )

                ManageTab.Library -> LazyColumn(
                    contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
                ) {
                    if (libraryApps.isEmpty()) {
                        item {
                            EmptyState(
                                title = "Your library is empty",
                                message = "Apps you install or add to your wishlist from Kiwi Store appear here.",
                                icon = Icons.Filled.BookmarkBorder,
                                actionLabel = "Browse apps",
                                onAction = { nav.navigate(Routes.APPS) },
                            )
                        }
                    } else {
                        itemsIndexed(libraryApps, key = { _, app -> app.id }) { index, app ->
                            Column {
                                if (index > 0) DividerLine(startIndent = 64.dp)
                                AppRowItem(
                                    app = app,
                                    release = vm.releaseFor(app),
                                    onOpen = { nav.navigate(Routes.app(app.id)) },
                                    onAction = { vm.onAppAction(app) },
                                    trailingBadge = if (installed.containsKey(app.packageName)) {
                                        { PillLabel("Installed", GothwadTheme.extra.chipBackground, GothwadTheme.extra.textSecondary) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }

                ManageTab.OnDevice -> OnDeviceContent(
                    installable = false,
                    onOpenDownloads = { nav.navigate(Routes.DOWNLOADS) },
                )
            }
        }
    }
}

@Composable
private fun UpdatesContent(
    updates: List<Pair<StoreApp, StoreRelease>>,
    installedCount: Int,
    onOpen: (String) -> Unit,
    onAction: (StoreApp) -> Unit,
    onUpdateAll: () -> Unit,
    onManageDownloads: () -> Unit,
    onOpenLibrary: () -> Unit,
    vm: StoreViewModel,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding)) {
        item {
            Column(Modifier.padding(Spacing.screenH)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .background(GothwadTheme.extra.successContainer, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = GothwadTheme.extra.success)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("$installedCount app${if (installedCount == 1) "" else "s"} on this device", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(
                            if (updates.isEmpty()) "Everything is up to date"
                            else "${updates.size} update${if (updates.size == 1) "" else "s"} available",
                            fontSize = 12.sp,
                            color = GothwadTheme.extra.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        label = if (updates.isEmpty()) "Check for updates" else "Update all",
                        onClick = { if (updates.isEmpty()) vm.refreshApps() else onUpdateAll() },
                        enabled = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlineButton(
                        label = "Downloads",
                        onClick = onManageDownloads,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        if (updates.isEmpty()) {
            item {
                InfoBanner(
                    title = "No updates waiting",
                    message = "Kiwi Store compares the version code your device reported with the newest " +
                        "approved release on the Worker. Updates appear here automatically.",
                    action = { OutlineButton("Open library", onClick = onOpenLibrary) },
                )
            }
        } else {
            item { SectionHeader(title = "Pending updates") }
            itemsIndexed(updates, key = { _, pair -> pair.first.id }) { index, (app, release) ->
                Column {
                    if (index > 0) DividerLine(startIndent = 64.dp)
                    AppRowItem(
                        app = app,
                        release = release,
                        onOpen = { onOpen(app.id) },
                        onAction = { onAction(app) },
                        subtitle = "Version ${release.versionName} · ${Format.bytes(release.apkSizeBytes)}",
                        trailingBadge = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnDeviceContent(installable: Boolean, onOpenDownloads: () -> Unit) {
    var includeSystem by rememberSaveable { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(includeSystem) {
        loading = true
        apps = AppGraph.library.installedApps(includeSystem)
        loading = false
    }

    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding)) {
        item {
            Column(Modifier.padding(Spacing.screenH)) {
                Text(
                    "${apps.size} package${if (apps.size == 1) "" else "s"} found",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                Text(
                    "Kiwi Store only reports what Android tells it about installed packages.",
                    fontSize = 12.sp,
                    color = GothwadTheme.extra.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton(
                        label = if (includeSystem) "Hide system apps" else "Show system apps",
                        onClick = { includeSystem = !includeSystem },
                        modifier = Modifier.weight(1f),
                    )
                    OutlineButton(
                        label = "Downloaded APKs",
                        onClick = onOpenDownloads,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (loading) {
            item { SkeletonRowsInline() }
        } else if (apps.isEmpty()) {
            item {
                EmptyState(
                    title = "Nothing to show",
                    message = "Android returned no packages for this filter.",
                    icon = Icons.Filled.PhoneAndroid,
                )
            }
        } else {
            items(apps, key = { it.packageName }) { entry ->
                Column {
                    DividerLine(startIndent = 64.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.screenH, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AppIcon(iconUrl = null, name = entry.label, size = 44.dp, corner = 12.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = GothwadTheme.extra.textPrimary,
                            )
                            Text(
                                entry.packageName,
                                fontSize = 11.sp,
                                color = GothwadTheme.extra.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (entry.versionName.isNotBlank()) {
                                Text(
                                    "v${entry.versionName} (${entry.versionCode})",
                                    fontSize = 11.sp,
                                    color = GothwadTheme.extra.textTertiary,
                                )
                            }
                        }
                        PillLabel(
                            text = if (entry.system) "System" else "User",
                            background = GothwadTheme.extra.chipBackground,
                            contentColor = GothwadTheme.extra.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonRowsInline() {
    com.gothwad.store.ui.components.SkeletonList(rows = 6)
}

@Composable
fun OnDeviceScreen(vm: StoreViewModel, nav: NavController) {
    GothwadScaffold(
        topBar = {
            GothwadTopBar(
                title = "On this device",
                onBack = { nav.popBackStack() },
                actions = { com.gothwad.store.ui.components.DownloadsAction { nav.navigate(Routes.DOWNLOADS) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OnDeviceContent(installable = true, onOpenDownloads = { nav.navigate(Routes.DOWNLOADS) })
        }
    }
}

@Composable
fun WishlistScreen(vm: StoreViewModel, nav: NavController) {
    val libraryState by AppGraph.library.state.collectAsStateWithLifecycle()
    val known by vm.knownApps.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        AppGraph.library.load()
        AppGraph.library.syncRemote()
        vm.refreshApps()
    }

    val wishlistApps = known.values.filter { it.id in libraryState.wishlist }

    GothwadScaffold(
        topBar = {
            GothwadTopBar(
                title = "Wishlist",
                subtitle = "${wishlistApps.size} saved",
                onBack = { nav.popBackStack() },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            if (wishlistApps.isEmpty()) {
                item {
                    EmptyState(
                        title = "Your wishlist is empty",
                        message = "Tap the bookmark button on any listing to keep it here, on every device " +
                            "signed in to your Gothwad account.",
                        icon = Icons.Filled.BookmarkBorder,
                        actionLabel = "Browse games",
                        onAction = { nav.navigate(Routes.GAMES) },
                    )
                }
                item {
                    InfoBanner(
                        title = "How the wishlist syncs",
                        message = "Kiwi Store posts to /api/store/wishlist with your Bearer token. Without a " +
                            "session the list stays on this device only.",
                        action = { OutlineButton("Sync now", onClick = { scope.launch { AppGraph.library.syncRemote() } }) },
                    )
                }
            } else {
                item { SectionHeader(title = "Saved apps") }
                itemsIndexed(wishlistApps, key = { _, app -> app.id }) { index, app ->
                    Column {
                        if (index > 0) DividerLine(startIndent = 64.dp)
                        AppRowItem(
                            app = app,
                            release = vm.releaseFor(app),
                            onOpen = { nav.navigate(Routes.app(app.id)) },
                            onAction = { vm.onAppAction(app) },
                        )
                    }
                }
                item {
                    Column(Modifier.padding(Spacing.screenH)) {
                        OutlineButton(
                            label = "Remove all from wishlist",
                            onClick = {
                                scope.launch {
                                    wishlistApps.forEach { AppGraph.library.toggleWishlist(it.id) }
                                    vm.emit(StoreEvent.Message("Wishlist cleared on this device."))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
