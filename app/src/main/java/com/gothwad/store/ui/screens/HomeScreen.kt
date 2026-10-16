package com.gothwad.store.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.gothwad.store.data.model.StoreApp
import com.gothwad.store.data.model.StoreCategory
import com.gothwad.store.data.model.StoreRelease
import com.gothwad.store.ui.CatalogBundle
import com.gothwad.store.ui.Routes
import com.gothwad.store.ui.StoreViewModel
import com.gothwad.store.ui.UiState
import com.gothwad.store.ui.components.AppGridCard
import com.gothwad.store.ui.components.AppRowItem
import com.gothwad.store.ui.components.AvatarAction
import com.gothwad.store.ui.components.BannerCard
import com.gothwad.store.ui.components.CategoryTile
import com.gothwad.store.ui.components.DividerLine
import com.gothwad.store.ui.components.DownloadsAction
import com.gothwad.store.ui.components.EmptyState
import com.gothwad.store.ui.components.ErrorState
import com.gothwad.store.ui.components.GameCard
import com.gothwad.store.ui.components.GothwadScaffold
import com.gothwad.store.ui.components.GothwadTopBar
import com.gothwad.store.ui.components.PlayChip
import com.gothwad.store.ui.components.SectionHeader
import com.gothwad.store.ui.components.SkeletonList
import com.gothwad.store.ui.components.SmallAppCard
import com.gothwad.store.ui.components.rememberInstallAction
import com.gothwad.store.ui.theme.GothwadTheme
import com.gothwad.store.ui.theme.PlayColors
import com.gothwad.store.ui.theme.Spacing
import kotlin.math.abs

@Composable
fun HomeScreen(vm: StoreViewModel, nav: NavController) {
    val state by vm.home.collectAsStateWithLifecycle()
    val releases by vm.releases.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()

    GothwadScaffold(
        topBar = {
            GothwadTopBar(
                title = "Gothwad Store",
                showBrandMark = true,
                actions = {
                    DownloadsAction { nav.navigate(Routes.DOWNLOADS) }
                    AvatarAction { nav.navigate(Routes.ACCOUNT) }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            UiState.Loading -> Column(Modifier.padding(padding)) { SkeletonList(rows = 7) }
            is UiState.Failed -> Column(Modifier.padding(padding)) {
                ErrorState(message = current.message, onRetry = { vm.refreshHome() })
            }
            is UiState.Ready -> HomeContent(
                bundle = current.value,
                categories = (categories as? UiState.Ready)?.value.orEmpty(),
                vm = vm,
                nav = nav,
                releases = releases,
                bottomPadding = padding.calculateBottomPadding(),
            )
        }
    }
}

@Composable
private fun HomeContent(
    bundle: CatalogBundle,
    categories: List<StoreCategory>,
    vm: StoreViewModel,
    nav: NavController,
    releases: Map<String, StoreRelease>,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val apps = bundle.apps
    if (apps.isEmpty()) {
        Column(Modifier.fillMaxWidth()) {
            EmptyState(
                title = "Nothing published yet",
                message = "Gothwad Store is connected to the live catalogue, but no app or game has " +
                    "been published in it yet. Developers publish from the Gothwad Developer Console.",
                icon = Icons.Filled.Storefront,
                actionLabel = "Open Developer Console",
                onAction = { nav.navigate(Routes.DEVELOPER_CONSOLE) },
            )
        }
        return
    }

    val games = apps.filter { it.isGame }
    val nonGames = apps.filterNot { it.isGame }
    val suggested = (nonGames + games).take(12)
    val trending = apps.sortedByDescending { it.downloadsCount }.take(5)
    val topFree = nonGames.ifEmpty { apps }.sortedByDescending { it.downloadsCount }.take(9)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding + Spacing.bottomContentPadding),
    ) {
        item {
            BannerCard(
                title = "New & updated",
                message = "Fresh builds straight from Gothwad Tech developers.",
                actionLabel = "Explore",
                onAction = { nav.navigate(Routes.charts("app")) },
                modifier = Modifier.padding(horizontal = Spacing.screenH),
            )
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.screenH, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { PlayChip("For you", true, onClick = { }) }
                item { PlayChip("Top charts", false, onClick = { nav.navigate(Routes.charts("app")) }) }
                item { PlayChip("Games", false, onClick = { nav.navigate(Routes.GAMES) }) }
                item { PlayChip("Categories", false, onClick = { nav.navigate(Routes.CATEGORIES) }) }
                item { PlayChip("Updates", false, onClick = { nav.navigate(Routes.UPDATES) }) }
            }
        }

        if (suggested.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Suggested for you",
                    subtitle = "From the live Gothwad catalogue",
                    onSeeAll = { nav.navigate(Routes.charts("app")) },
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.screenH),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(suggested, key = { "suggested-${it.id}" }) { app ->
                        val action = rememberInstallAction(app, releases[app.id])
                        SmallAppCard(
                            app = app,
                            action = action,
                            onClick = { nav.navigate(Routes.app(app.id)) },
                            onActionClick = { vm.onAppAction(app) },
                        )
                    }
                }
            }
        }

        if (topFree.isNotEmpty()) {
            item { SectionHeader(title = "Top free", onSeeAll = { nav.navigate(Routes.charts("app")) }) }
            items(topFree.chunked(3), key = { row -> "grid-${row.first().id}" }) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
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
        }

        if (games.isNotEmpty()) {
            item {
                SectionHeader(title = "Recommended games", onSeeAll = { nav.navigate(Routes.GAMES) })
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.screenH),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(games, key = { "game-${it.id}" }) { app ->
                        GameCard(
                            app = app,
                            action = rememberInstallAction(app, releases[app.id]),
                            onClick = { nav.navigate(Routes.app(app.id)) },
                            onActionClick = { vm.onAppAction(app) },
                        )
                    }
                }
            }
        }

        if (trending.isNotEmpty()) {
            item { SectionHeader(title = "Trending now", onSeeAll = { nav.navigate(Routes.charts("app")) }) }
            itemsIndexed(trending, key = { _, app -> "trend-${app.id}" }) { index, app ->
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

        if (categories.isNotEmpty()) {
            item {
                SectionHeader(title = "Browse by category", onSeeAll = { nav.navigate(Routes.CATEGORIES) })
            }
            items(categories.chunked(2), key = { row -> "cat-${row.first().slug}" }) { row ->
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

        item {
            Column(Modifier.padding(Spacing.screenH)) {
                DividerLine()
                Text(
                    "Live catalogue · store.gothwadtech.com",
                    modifier = Modifier.padding(top = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = GothwadTheme.extra.textTertiary,
                )
            }
        }
    }
}

private val CATEGORY_ICONS: Map<String, ImageVector> = mapOf(
    "social" to Icons.Filled.Groups,
    "communication" to Icons.Filled.ChatBubbleOutline,
    "tools" to Icons.Filled.Build,
    "games" to Icons.Filled.SportsEsports,
    "game" to Icons.Filled.SportsEsports,
    "photography" to Icons.Filled.PhotoCamera,
    "education" to Icons.Filled.School,
    "finance" to Icons.Filled.AccountBalance,
    "health" to Icons.Filled.FavoriteBorder,
    "shopping" to Icons.Filled.ShoppingBag,
    "music" to Icons.Filled.MusicNote,
    "video" to Icons.Filled.PlayCircle,
    "news" to Icons.Filled.Newspaper,
    "productivity" to Icons.Filled.Work,
    "books" to Icons.Filled.MenuBook,
    "maps" to Icons.Filled.Place,
    "travel" to Icons.Filled.Flight,
    "food" to Icons.Filled.Restaurant,
    "entertainment" to Icons.Filled.Movie,
)

internal fun categoryIcon(slug: String, iconHint: String = ""): ImageVector =
    CATEGORY_ICONS[slug.lowercase()]
        ?: CATEGORY_ICONS[iconHint.lowercase()]
        ?: Icons.Filled.Apps

internal fun categoryColor(slug: String) =
    PlayColors.iconPalette[abs(slug.hashCode()) % PlayColors.iconPalette.size]

internal fun visibleApps(apps: List<StoreApp>): List<StoreApp> = apps
