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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.launch
import androidx.navigation.NavController
import com.gothwad.store.core.AppGraph
import com.gothwad.store.core.Format
import com.gothwad.store.ui.Routes
import com.gothwad.store.ui.StoreViewModel
import com.gothwad.store.ui.UiState
import com.gothwad.store.ui.components.AppRowItem
import com.gothwad.store.ui.components.AppIcon
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

@Composable
fun SearchScreen(vm: StoreViewModel, nav: NavController) {
    var query by rememberSaveable { mutableStateOf("") }
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val releases by vm.releases.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) { vm.search(query) }

    GothwadScaffold(
        topBar = {
            SearchTopBar(
                query = query,
                onQueryChange = { query = it },
                onBack = { nav.navigate(Routes.HOME) },
                onSubmit = { term ->
                    if (term.isNotBlank()) {
                        query = term
                        scope.launch { AppGraph.settings.addSearchTerm(term) }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (query.isBlank()) {
                val history = settings?.searchHistory.orEmpty()
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
                                        .clickable { scope.launch { AppGraph.settings.clearSearchHistory() } }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = Spacing.screenH),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(history) { term ->
                                    PlayChip(term, false, onClick = { query = term })
                                }
                            }
                        }
                    }
                    val suggestions = (home as? UiState.Ready)?.value?.apps.orEmpty().take(8)
                    if (suggestions.isNotEmpty()) {
                        item { SectionHeader(title = "Discover", subtitle = "Popular right now") }
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
                    } else {
                        item {
                            EmptyState(
                                title = "Search Kiwi Store",
                                message = "Find apps and games by name, developer or package name.",
                                icon = Icons.Filled.Search,
                            )
                        }
                    }
                }
            } else {
                when (val current = results) {
                    UiState.Loading -> SkeletonList(rows = 6)
                    is UiState.Failed -> ErrorState(current.message, onRetry = { vm.search(query, 0) })
                    is UiState.Ready -> {
                        val apps = current.value.apps
                        if (apps.isEmpty()) {
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
                                        subtitle = "${apps.size} match${if (apps.size == 1) "" else "es"}",
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
}
