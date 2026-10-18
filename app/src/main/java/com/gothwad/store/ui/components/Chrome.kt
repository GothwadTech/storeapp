package com.gothwad.store.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gothwad.store.ui.Routes
import com.gothwad.store.ui.theme.GothwadTheme

/**
 * Supplied by the nav host while a bottom-tab destination is on screen, so the
 * tab screens only describe their own content and the bar itself is rendered
 * once, with the correct selected tab.
 */
data class TabChrome(val currentRoute: String, val onTabSelected: (String) -> Unit)

val LocalTabChrome = androidx.compose.runtime.compositionLocalOf<TabChrome?> { null }

/** App-wide snackbar host so the ViewModel can surface messages on any screen. */
val LocalAppSnackbarHost = androidx.compose.runtime.compositionLocalOf<SnackbarHostState?> { null }

/**
 * Page chrome. The bottom navigation is a plain Row instead of Material's
 * NavigationBar so the bar is always exactly as tall as its content plus the
 * system gesture inset — the earlier clipped-tab problem came from a fixed
 * 68dp height fighting the navigation-bar inset.
 */
@Composable
fun GothwadScaffold(
    topBar: @Composable () -> Unit = {},
    showBottomBar: Boolean = LocalTabChrome.current != null,
    currentRoute: String = LocalTabChrome.current?.currentRoute ?: Routes.HOME,
    onTabSelected: (String) -> Unit = LocalTabChrome.current?.onTabSelected ?: {},
    snackbarHostState: SnackbarHostState = sharedSnackbarHost(LocalAppSnackbarHost.current),
    floatingActionButton: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = topBar,
        bottomBar = {
            if (showBottomBar) {
                GothwadBottomBar(currentRoute = currentRoute, onTabSelected = onTabSelected)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = floatingActionButton,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}

@Composable
fun GothwadTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    showBrandMark: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = if (onBack != null) 4.dp else 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = GothwadTheme.extra.textPrimary,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = GothwadTheme.extra.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = GothwadTheme.extra.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                content = actions,
            )
        }
        if (showBrandMark) DividerLine()
    }
}

/** Search-tab top bar: pill search field with back, clear and voice actions. */
@Composable
fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = GothwadTheme.extra.textSecondary,
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .clip(RoundedCornerShape(50))
                .background(GothwadTheme.extra.searchBar)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = GothwadTheme.extra.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        "Search apps & games",
                        color = GothwadTheme.extra.textTertiary,
                        fontSize = 15.sp,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = GothwadTheme.extra.textPrimary,
                        fontSize = 15.sp,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit(query) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_input"),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = "Clear",
                        tint = GothwadTheme.extra.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Voice search",
                    tint = GothwadTheme.extra.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        TextButton(onClick = { onSubmit(query) }) {
            Text("Search", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
        }
    }
}

private data class TabSpec(
    val route: String,
    val label: String,
    val active: ImageVector,
    val inactive: ImageVector,
)

private val TAB_SPECS = listOf(
    TabSpec(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    TabSpec(Routes.APPS, "Apps", Icons.Filled.Apps, Icons.Outlined.Apps),
    TabSpec(Routes.SEARCH, "Search", Icons.Filled.Search, Icons.Outlined.Search),
    TabSpec(Routes.GAMES, "Games", Icons.Filled.SportsEsports, Icons.Outlined.SportsEsports),
    TabSpec(Routes.ACCOUNT, "Profile", Icons.Filled.Person, Icons.Outlined.Person),
)

@Composable
fun GothwadBottomBar(
    currentRoute: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        DividerLine()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .heightIn(min = 80.dp)
                .testTag("bottom_bar"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TAB_SPECS.forEach { tab ->
                BottomTabItem(
                    spec = tab,
                    selected = currentRoute == tab.route,
                    onClick = { onTabSelected(tab.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomTabItem(
    spec: TabSpec,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else GothwadTheme.extra.textSecondary,
        label = "tabTint",
    )
    val pill by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "tabPill",
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .testTag("tab_${spec.route}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(32.dp)
                .clip(CircleShape)
                .background(pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) spec.active else spec.inactive,
                contentDescription = spec.label,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = spec.label,
            fontSize = 12.sp,
            color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** Simple flat section card used across the settings-style screens. */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        if (title != null) {
            Text(
                title,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column { content() }
        }
    }
}

@Composable
fun SheetHeader(title: String, onClose: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (onClose != null) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = GothwadTheme.extra.textSecondary)
            }
        }
    }
}

/** Uses the app-wide snackbar host when one is provided, else a local one. */
@Composable
private fun sharedSnackbarHost(provided: SnackbarHostState?): SnackbarHostState {
    val local = remember { SnackbarHostState() }
    return provided ?: local
}
