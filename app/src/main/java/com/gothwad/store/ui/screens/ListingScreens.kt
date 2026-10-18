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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.gothwad.store.data.model.StoreApp
import com.gothwad.store.data.model.StoreCategory
import com.gothwad.store.data.model.StoreRelease
import com.gothwad.store.ui.Routes
import com.gothwad.store.ui.StoreViewModel
import com.gothwad.store.ui.UiState
import com.gothwad.store.ui.components.AppGridCard
import com.gothwad.store.ui.components.AppRowItem
import com.gothwad.store.ui.components.BannerTone
import com.gothwad.store.ui.components.AvatarAction
import com.gothwad.store.ui.components.CategoryTile
import com.gothwad.store.ui.components.DividerLine
import com.gothwad.store.ui.components.DownloadsAction
import com.gothwad.store.ui.components.EmptyState
import com.gothwad.store.ui.components.ErrorState
import com.gothwad.store.ui.components.InfoBanner
import com.gothwad.store.ui.components.GothwadScaffold
import com.gothwad.store.ui.components.GothwadTopBar
import com.gothwad.store.ui.components.PlayChip
import com.gothwad.store.ui.components.SearchAction
import com.gothwad.store.ui.components.SegmentedRow
import com.gothwad.store.ui.components.SectionHeader
import com.gothwad.store.ui.components.SkeletonList
import com.gothwad.store.ui.components.rememberInstallAction
import com.gothwad.store.ui.theme.Spacing

@Composable
private fun FdroidStatusNotice(vm: StoreViewModel, onRetry: () -> Unit) {
    val error by vm.fdroidError.collectAsStateWithLifecycle()
    if (error != null) {
        InfoBanner(
            title = "F-Droid catalogue unavailable",
            message = error.orEmpty(),
            tone = BannerTone.Warning,
            action = {
                TextButton(onClick = {
                    vm.retryFdroid()
                    onRetry()
                }) { Text("Retry") }
            },
        )
    }
}

@Composable
fun AppsScreen(vm: StoreViewModel, nav: NavController) {
    val state by vm.apps.collectAsStateWithLifecycle()
    val releases by vm.releases.collectAsStateWithLifecycle()
    var chipIndex by rememberSaveable { mutableIntStateOf(0) }
    val chips = listOf("For you", "Top charts", "Categories", "Updates")

    LaunchedEffect(Unit) {
        if (state is UiState.Ready && (state as UiState.Ready).value.apps.isEmpty()) vm.refreshApps()
    }

    GothwadScaffold(
        topBar = {
            GothwadTopBar(
                title = "Apps",
                showBrandMark = true,
                actions = {
                    SearchAction { nav.navigate(Routes.SEARCH) }
                    DownloadsAction { nav.navigate(Routes.DOWNLOADS) }
                    AvatarAction { nav.navigate(Routes.ACCOUNT) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FdroidStatusNotice(vm, onRetry = { vm.refreshApps() })
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.screenH, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(chips.indices.toList()) { index ->
                    PlayChip(
                        label = chips[index],
                        selected = chipIndex == index,
                        onClick = {
                            chipIndex = index
                            when (index) {
                                1 -> nav.navigate(Routes.charts("app"))
                                2 -> nav.navigate(Routes.CATEGORIES)
                                3 -> nav.navigate(Routes.UPDATES)
                                else -> vm.refreshApps()
                            }
                        },
                    )
                }
            }
            when (val current = state) {
                UiState.Loading -> SkeletonList(rows = 7)
                is UiState.Failed -> ErrorState(current.message, onRetry = { vm.refreshApps() })
                is UiState.Ready -> AppList(
                    apps = current.value.apps,
                    releases = releases,
                    vm = vm,
                    nav = nav,
                    emptyTitle = "No apps published yet",
                )
            }
        }
    }
}

/**
 * The Kiwi tab: everything that is live on the Kiwi Store web
 * (kiwistore.gothwadtech.com), listed exactly like the Apps tab.
 *
 * The optional upstream catalogue (F-Droid) is not part of the web store, so
 * it never shows up here — the Worker catalogue is the single source.
 */
@Composable
fun KiwiScreen(vm: StoreViewModel, nav: NavController) {
    val state by vm.kiwi.collectAsStateWithLifecycle()
    val releases by vm.releases.collectAsStateWithLifecycle()
    var kindFilter by rememberSaveable { mutableIntStateOf(0) }
    val filters = listOf("All", "Apps", "Games")

    LaunchedEffect(Unit) {
        if (state is UiState.Ready && (state as UiState.Ready).value.apps.isEmpty()) vm.refreshKiwi()
    }

    GothwadScaffold(
        topBar = {
            GothwadTopBar(
                title = "Kiwi",
                subtitle = "Live on kiwistore.gothwadtech.com",
                showBrandMark = true,
                actions = {
                    SearchAction { nav.navigate(Routes.SEARCH) }
                    DownloadsAction { nav.navigate(Routes.DOWNLOADS) }
                    AvatarAction { nav.navigate(Routes.ACCOUNT) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.screenH, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filters.indices.toList()) { index ->
                    PlayChip(
                        label = filters[index],
                        selected = kindFilter == index,
                        onClick = { kindFilter = index },
                    )
                }
            }
            when (val current = state) {
                UiState.Loading -> SkeletonList(rows = 7)
                is UiState.Failed -> ErrorState(current.message, onRetry = { vm.refreshKiwi() })
                is UiState.Ready -> {
                    val apps = current.value.apps
                        .filter { app -> app.isKiwiStoreListing }
                        .let { all ->
                            when (kindFilter) {
                                1 -> all.filterNot { it.isGame }
                                2 -> all.filter { it.isGame }
                                else -> all
                            }
                        }
                    if (apps.isEmpty()) {
                        EmptyState(
                            title = "Nothing on the Kiwi Store web yet",
                            message = "Apps show up here as soon as a developer publishes them on " +
                                "kiwistore.gothwadtech.com.",
                            icon = Icons.Filled.Storefront,
                            actionLabel = "Open Developer Console",
                            onAction = { nav.navigate(Routes.DEVELOPER_CONSOLE) },
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding)) {
                            item {
                                SectionHeader(
                                    title = "On the Kiwi Store web",
                                    subtitle = "${apps.size} app${if (apps.size == 1) "" else "s"}",
                                )
                            }
                            itemsIndexed(apps, key = { _, app -> app.id }) { index, app ->
                                Column {
                                    if (index > 0) DividerLine(startIndent = 64.dp)
                                    AppRowItem(
                                        app = app,
                                        release = releases[app.id],
                                        onOpen = { nav.navigate(Routes.app(app.id)) },
                                        onAction = { vm.onAppAction(app) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GamesScreen(vm: StoreViewModel, nav: NavController) {
    val state by vm.games.collectAsStateWithLifecycle()
    val releases by vm.releases.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (state is UiState.Ready && (state as UiState.Ready).value.apps.isEmpty()) vm.refreshGames()
    }

    GothwadScaffold(
        topBar = {
            GothwadTopBar(
                title = "Games",
                showBrandMark = true,
                actions = {
                    SearchAction { nav.navigate(Routes.SEARCH) }
                    DownloadsAction { nav.navigate(Routes.DOWNLOADS) }
                    AvatarAction { nav.navigate(Routes.ACCOUNT) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FdroidStatusNotice(vm, onRetry = vm::refreshGames)
            when (val current = state) {
                UiState.Loading -> SkeletonList(rows = 7)
                is UiState.Failed -> ErrorState(current.message, onRetry = { vm.refreshGames() })
                is UiState.Ready -> AppList(
                    apps = current.value.apps,
                    releases = releases,
                    vm = vm,
                    nav = nav,
                    emptyTitle = "No games published yet",
                    emptyMessage = "Games appear here as soon as a developer publishes one from the " +
                        "Gothwad Developer Console.",
                )
            }
        }
    }
}

@Composable
private fun AppList(
    apps: List<StoreApp>,
    releases: Map<String, StoreRelease>,
    vm: StoreViewModel,
    nav: NavController,
    emptyTitle: String,
    emptyMessage: String = "Nothing is published in this section yet.",
) {
    if (apps.isEmpty()) {
        EmptyState(
            title = emptyTitle,
            message = emptyMessage,
            icon = Icons.Filled.NewReleases,
            actionLabel = "Open Developer Console",
            onAction = { nav.navigate(Routes.DEVELOPER_CONSOLE) },
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding)) {
        itemsIndexed(apps, key = { _, app -> app.id }) { index, app ->
            Column {
                if (index > 0) DividerLine(startIndent = 64.dp)
                AppRowItem(
                    app = app,
                    release = releases[app.id],
                    onOpen = { nav.navigate(Routes.app(app.id)) },
                    onAction = { vm.onAppAction(app) },
                )
            }
        }
    }
}

@Composable
fun TopChartsScreen(vm: StoreViewModel, nav: NavController, kind: String) {
    val state by vm.charts.collectAsStateWithLifecycle()
    val releases by vm.releases.collectAsStateWithLifecycle()
    var tab by rememberSaveable(kind) { mutableIntStateOf(0) }

    LaunchedEffect(kind) { vm.refreshCharts(kind.takeIf { it == "game" }) }

    GothwadScaffold(
        topBar = {
            GothwadTopBar(
                title = if (kind == "game") "Top games" else "Top charts",
                onBack = { nav.popBackStack() },
                actions = { DownloadsAction { nav.navigate(Routes.DOWNLOADS) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SegmentedRow(
                options = listOf("Top free", "Trending", "New"),
                selectedIndex = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(horizontal = Spacing.screenH, vertical = 10.dp),
            )
            when (val current = state) {
                UiState.Loading -> SkeletonList(rows = 8)
                is UiState.Failed -> ErrorState(current.message, onRetry = { vm.refreshCharts(kind) })
                is UiState.Ready -> {
                    val apps = when (tab) {
                        1 -> current.value.apps.sortedByDescending { it.downloadsCount }
                        2 -> current.value.apps.sortedByDescending { it.createdAt }
                        else -> current.value.apps
                    }
                    if (apps.isEmpty()) {
                        EmptyState(
                            title = "Charts are empty",
                            message = "No published apps are available for this chart yet.",
                            icon = Icons.Filled.TrendingUp,
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding)) {
                            itemsIndexed(apps, key = { _, app -> app.id }) { index, app ->
                                Column {
                                    if (index > 0) DividerLine(startIndent = 64.dp)
                                    AppRowItem(
                                        app = app,
                                        release = releases[app.id],
                                        onOpen = { nav.navigate(Routes.app(app.id)) },
                                        onAction = { vm.onAppAction(app) },
                                        rank = index + 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoriesScreen(vm: StoreViewModel, nav: NavController) {
    val state by vm.categories.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshCategories() }

    GothwadScaffold(
        topBar = {
            GothwadTopBar(
                title = "Categories",
                onBack = { nav.popBackStack() },
                actions = {
                    SearchAction { nav.navigate(Routes.SEARCH) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                UiState.Loading -> SkeletonList(rows = 8, showIcons = false)
                is UiState.Failed -> ErrorState(current.message, onRetry = { vm.refreshCategories() })
                is UiState.Ready -> CategoryGrid(current.value, nav)
            }
        }
    }
}

@Composable
private fun CategoryGrid(categories: List<StoreCategory>, nav: NavController) {
    if (categories.isEmpty()) {
        EmptyState(
            title = "No categories yet",
            message = "The Store database has no categories configured.",
            icon = Icons.Filled.Category,
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding)) {
        items(categories.chunked(2), key = { row -> "catrow-${row.first().slug}" }) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenH, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { category ->
                    CategoryTile(
                        label = category.name,
                        icon = categoryIcon(category.slug, category.icon),
                        color = categoryColor(category.slug),
                        onClick = { nav.navigate(Routes.category(category.slug, category.kind)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun CategoryDetailScreen(vm: StoreViewModel, nav: NavController, slug: String, kind: String) {
    val state by vm.categoryApps.collectAsStateWithLifecycle()
    val releases by vm.releases.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val name = (categories as? UiState.Ready)?.value?.firstOrNull { it.slug == slug }?.name
        ?: slug.replaceFirstChar { it.uppercase() }

    LaunchedEffect(slug, kind) { vm.loadCategory(slug, kind) }

    GothwadScaffold(
        topBar = {
            GothwadTopBar(
                title = name,
                subtitle = if (kind == "game") "Games" else null,
                onBack = { nav.popBackStack() },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                UiState.Loading -> SkeletonList(rows = 7)
                is UiState.Failed -> ErrorState(current.message, onRetry = { vm.loadCategory(slug, kind) })
                is UiState.Ready -> {
                    val apps = current.value.apps
                    if (apps.isEmpty()) {
                        EmptyState(
                            title = "Nothing in $name yet",
                            message = "No published app matches this category on your device.",
                            icon = Icons.Filled.Search,
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding)) {
                            item {
                                SectionHeader(
                                    title = "$name apps",
                                    subtitle = "${apps.size} published",
                                )
                            }
                            itemsIndexed(apps, key = { _, app -> app.id }) { index, app ->
                                Column {
                                    if (index > 0) DividerLine(startIndent = 64.dp)
                                    AppRowItem(
                                        app = app,
                                        release = releases[app.id],
                                        onOpen = { nav.navigate(Routes.app(app.id)) },
                                        onAction = { vm.onAppAction(app) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PreviewGrid(apps: List<StoreApp>, releases: Map<String, StoreRelease>, vm: StoreViewModel, nav: NavController) {
    Column {
        apps.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                row.forEach { app ->
                    val action = rememberInstallAction(app, releases[app.id])
                    AppGridCard(
                        app = app,
                        action = action,
                        onClick = { nav.navigate(Routes.app(app.id)) },
                        onActionClick = { vm.onAppAction(app) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
