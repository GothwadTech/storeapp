package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.ApkDownloader
import com.example.data.remote.*
import com.example.ui.components.GothwadBottomBar
import com.example.ui.navigation.Screen
import kotlinx.coroutines.launch
import java.util.Locale

/** Live Worker storefront. Legacy demo screens are intentionally not reachable here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainStoreScreen(model: StoreViewModel = viewModel()) {
    var route by rememberSaveable { mutableStateOf(Screen.Home.route) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val categories by model.categories.collectAsStateWithLifecycle()
    val detail by model.detail.collectAsStateWithLifecycle()
    val kind = when (route) { Screen.Apps.route -> "app"; Screen.Games.route -> "game"; else -> null }
    LaunchedEffect(kind, category, query, route) {
        model.setFilter(kind, category, if (route == Screen.Search.route) query else "")
    }
    LaunchedEffect(selectedId) { selectedId?.let(model::openApp) }
    BackHandler(selectedId != null || route != Screen.Home.route) {
        if (selectedId != null) selectedId = null else { route = Screen.Home.route; category = null }
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pendingDownload by remember { mutableStateOf<Pair<StoreRelease, String>?>(null) }
    val startDownload: (StoreRelease, String) -> Unit = { release, name ->
        try {
            ApkDownloader.enqueue(context, release, name)
            scope.launch { snackbar.showSnackbar("Download requested. Check Downloads for progress or errors.") }
        } catch (_: Exception) {
            scope.launch { snackbar.showSnackbar("Could not start the download. Check that Downloads is enabled and try again.") }
        }
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingDownload?.let { (release, name) ->
            if (granted) startDownload(release, name)
            else scope.launch { snackbar.showSnackbar("Storage permission is required to save APKs on this Android version.") }
        }
        pendingDownload = null
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (selectedId != null) "App details" else "Gothwad Store") },
                navigationIcon = {
                    if (selectedId != null) TextButton(onClick = { selectedId = null }) { Text("Back") }
                },
                actions = {
                    TextButton(onClick = {
                        try { context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
                        catch (_: ActivityNotFoundException) {
                            scope.launch { snackbar.showSnackbar("Open your device's Downloads app.") }
                        }
                    }) { Text("Downloads") }
                })
        },
        bottomBar = {
            GothwadBottomBar(currentRoute = route, onTabSelected = {
                route = it.route; category = null; selectedId = null
            })
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val id = selectedId
            when {
                id != null -> StateContent(detail, onRetry = { model.openApp(id) }) { data ->
                    AppDetails(data, onDownload = { release ->
                        if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            pendingDownload = release to data.app.name
                            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else startDownload(release, data.app.name)
                    })
                }
                route == Screen.Profile.route -> ProfileInfo()
                else -> Column(Modifier.fillMaxSize()) {
                    if (route == Screen.Search.route) {
                        OutlinedTextField(value = query, onValueChange = { query = it },
                            label = { Text("Search apps and games") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    }
                    when (val state = categories) {
                        is LoadState.Ready -> LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item { FilterChip(selected = category == null,
                                onClick = { category = null }, label = { Text("All categories") }) }
                            items(state.value.filter { kind == null || it.kind == kind || it.kind == "both" }, key = { it.slug }) { cat ->
                                FilterChip(selected = category == cat.slug,
                                    onClick = { category = cat.slug }, label = { Text(cat.name) })
                            }
                        }
                        is LoadState.Failed -> TextButton(onClick = model::refreshCategories) { Text("Retry loading categories") }
                        LoadState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    StateContent(catalog, onRetry = model::retry) { apps ->
                        if (apps.isEmpty()) EmptyMessage(if (query.isNotBlank() && route == Screen.Search.route)
                            "No apps match your search." else "No published apps in this selection yet.")
                        else LazyColumn(contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text("${apps.size} apps", style = MaterialTheme.typography.titleMedium)
                                    TextButton(onClick = model::retry) { Text("Refresh") }
                                }
                            }
                            items(apps, key = { it.id }) { app -> AppRow(app) { selectedId = app.id } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> StateContent(state: LoadState<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (state) {
        LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is LoadState.Failed -> Column(Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(state.message)
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text("Try again") }
        }
        is LoadState.Ready -> content(state.value)
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(text) }
}

@Composable
private fun AppIcon(app: StoreApp) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Text(app.name.take(1), style = MaterialTheme.typography.headlineMedium)
            if (app.iconUrl.isNotBlank()) AsyncImage(model = app.iconUrl,
                contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun AppRow(app: StoreApp, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(app)
            Column(Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(app.developerName, style = MaterialTheme.typography.bodySmall)
                Text(app.shortDesc, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                Text("${String.format(Locale.US, "%.1f", app.ratingAvg)} ★ · ${app.downloadsCount} downloads",
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun AppDetails(data: AppDetailResponse, onDownload: (StoreRelease) -> Unit) {
    val app = data.app
    val release = data.downloadableRelease(Build.VERSION.SDK_INT)
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AppIcon(app)
                Column {
                    Text(app.name, style = MaterialTheme.typography.headlineSmall)
                    Text(app.developerName)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text("${String.format(Locale.US, "%.1f", app.ratingAvg)} ★ (${app.ratingCount} ratings) · ${app.downloadsCount} downloads") }
        item {
            if (release != null) {
                Text("Version ${release.versionName} · ${String.format(Locale.US, "%.1f", release.apkSizeBytes / 1048576.0)} MB")
                Button(onClick = { onDownload(release) },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Download APK")
                }
                Text("Open the completed APK from Downloads to install. Android will ask for permission. Downloading does not mean the app is installed.",
                    style = MaterialTheme.typography.bodySmall)
            } else Text("No approved release compatible with this device is available.")
        }
        item {
            Text("About this app", style = MaterialTheme.typography.titleLarge)
            Text(app.fullDesc.ifBlank { app.shortDesc })
        }
        if (app.screenshots.isNotEmpty()) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(app.screenshots) { url ->
                    AsyncImage(model = url, contentDescription = "App screenshot",
                        modifier = Modifier.width(180.dp).height(320.dp))
                }
            }
        }
        if (release != null) {
            if (release.changelog.isNotBlank()) item {
                Text("What's new", style = MaterialTheme.typography.titleLarge)
                Text(release.changelog)
            }
            item {
                Text("Permissions", style = MaterialTheme.typography.titleMedium)
                Text(release.permissions.joinToString("\n").ifBlank { "No permissions listed." })
                release.apkSha256?.takeIf { it.isNotBlank() }?.let {
                    Text("Publisher SHA-256: $it", style = MaterialTheme.typography.bodySmall)
                    Text("Checksum shown for reference; this version does not verify the downloaded file.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text("Reviews", style = MaterialTheme.typography.titleLarge) }
        if (data.reviews.isEmpty()) item { Text("No reviews yet.") }
        items(data.reviews, key = { it.id }) { review ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${review.username} · ${review.rating} ★", fontWeight = FontWeight.Bold)
                    Text(review.body)
                    review.developerReply?.let { Text("Developer reply: $it") }
                }
            }
        }
    }
}

@Composable
private fun ProfileInfo() {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Your Gothwad account", style = MaterialTheme.typography.headlineSmall)
        Text("Browsing and APK downloads do not require sign-in.")
        Text("Native Gothwad Accounts sign-in is not connected yet. Reviews, wishlist and account library will be enabled after the OAuth callback and secure session flow are implemented.")
        Text("Downloads are managed by Android. Use Downloads above to check progress and open completed APKs.")
    }
}
