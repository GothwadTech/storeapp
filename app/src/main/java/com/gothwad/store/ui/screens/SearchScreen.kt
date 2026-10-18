package com.gothwad.store.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.gothwad.store.core.AppGraph
import com.gothwad.store.data.model.StoreApp
import com.gothwad.store.data.model.StoreRelease
import com.gothwad.store.ui.Routes
import com.gothwad.store.ui.StoreViewModel
import com.gothwad.store.ui.UiState
import com.gothwad.store.ui.components.AppRowItem
import com.gothwad.store.ui.components.DividerLine
import com.gothwad.store.ui.components.EmptyState
import com.gothwad.store.ui.components.ErrorState
import com.gothwad.store.ui.components.GothwadScaffold
import com.gothwad.store.ui.components.PlayChip
import com.gothwad.store.ui.components.SearchTopBar
import com.gothwad.store.ui.components.SectionHeader
import com.gothwad.store.ui.components.SkeletonList
import com.gothwad.store.ui.theme.GothwadTheme
import com.gothwad.store.ui.theme.Spacing

/**
 * Search is not a tab any more: it is opened from the search action in every
 * tab header and pushed as a sub-screen, so the back arrow returns to the tab
 * the user came from.
 *
 * Idle screen order (top → bottom): recent searches, five suggested apps, then
 * "Recently viewed" at the very bottom — exactly the Play-style stacking.
 */
@Composable
fun SearchScreen(vm: StoreViewModel, nav: NavController) {
    var query by rememberSaveable { mutableStateOf("") }
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val releases by vm.releases.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val knownApps by vm.knownApps.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) { vm.search(query) }

    GothwadScaffold(
        topBar = {
            SearchTopBar(
                query = query,
                onQueryChange = { query = it },
                onBack = {
                    // Back returns to the previous tab; with an empty back stack
                    // fall back to Home instead of stacking a second Home entry.
                    if (!nav.popBackStack()) {
                        nav.navigate(Routes.HOME) { launchSingleTop = true }
                    }
                },
                onSubmit = { term ->
                    val clean = term.trim()
                    if (clean.isNotBlank()) {
                        query = clean
                        scope.launch { AppGraph.settings.addSearchTerm(clean) }
                    }
                },
            )
        },
    ) { padding ->
        // The tab bar is gone on this screen, so the list has to keep clear of
        // the system navigation bar itself.
        Column(Modifier.fillMaxSize().padding(padding).navigationBarsPadding()) {
            if (query.isBlank()) {
                val catalogue = when (val state = home) {
                    is UiState.Ready -> state.value.apps
                    else -> (apps as? UiState.Ready)?.value?.apps.orEmpty()
                }
                SearchIdleContent(
                    history = settings?.searchHistory.orEmpty(),
                    suggestions = catalogue.take(5),
                    recentlyViewed = settings?.recentlyViewed.orEmpty(),
                    knownApps = knownApps,
                    releases = releases,
                    vm = vm,
                    nav = nav,
                    onTermClick = { query = it },
                    onClearHistory = { scope.launch { AppGraph.settings.clearSearchHistory() } },
                    onClearViewed = { scope.launch { AppGraph.settings.clearRecentlyViewed() } },
                )
            } else {
                when (val current = results) {
                    UiState.Loading -> SkeletonList(rows = 6)
                    is UiState.Failed -> ErrorState(current.message, onRetry = { vm.search(query, 0) })
                    is UiState.Ready -> {
                        val found = current.value.apps
                        if (found.isEmpty()) {
                            EmptyState(
                                title = "No results for “$query”",
                                message = "Check the spelling, or try a different app, game or developer name.",
                                icon = Icons.Filled.Search,
                            )
                        } else {
                            LazyColumn(contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding)) {
                                item {
                                    SectionHeader(
                                        title = "Results",
                                        subtitle = "${found.size} match${if (found.size == 1) "" else "es"}",
                                    )
                                }
                                itemsIndexed(found, key = { _, app -> app.id }) { index, app ->
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
}

@Composable
private fun SearchIdleContent(
    history: List<String>,
    suggestions: List<StoreApp>,
    recentlyViewed: List<String>,
    knownApps: Map<String, StoreApp>,
    releases: Map<String, StoreRelease>,
    vm: StoreViewModel,
    nav: NavController,
    onTermClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    onClearViewed: () -> Unit,
) {
    // The ids are persisted, the listings may not be in this session's cache —
    // fall back to the known-app map and silently drop anything unknown.
    val visited = recentlyViewed.mapNotNull { knownApps[it] }.distinctBy { it.id }.take(8)

    if (history.isEmpty() && suggestions.isEmpty() && visited.isEmpty()) {
        EmptyState(
            title = "Search Kiwi Store",
            message = "Find apps and games by name, developer or package name.",
            icon = Icons.Filled.Search,
        )
        return
    }

    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding)) {
        if (history.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screenH, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        tint = GothwadTheme.extra.textSecondary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        "Recent searches",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Clear",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clickable(onClick = onClearHistory)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.screenH),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(history.take(5)) { term ->
                        PlayChip(term, false, onClick = { onTermClick(term) })
                    }
                }
            }
        }

        if (suggestions.isNotEmpty()) {
            item { SectionHeader(title = "Suggested apps", subtitle = "Popular right now") }
            itemsIndexed(suggestions, key = { _, app -> "discover-${app.id}" }) { index, app ->
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

        if (visited.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recently viewed",
                    actionLabel = "Clear",
                    onAction = onClearViewed,
                )
            }
            itemsIndexed(visited, key = { _, app -> "visited-${app.id}" }) { index, app ->
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
