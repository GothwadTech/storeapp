package com.gothwad.store.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import com.gothwad.store.core.AppGraph
import com.gothwad.store.core.Format
import com.gothwad.store.data.download.DownloadTask
import com.gothwad.store.data.download.canInstall
import com.gothwad.store.data.install.InstallMethod
import com.gothwad.store.data.model.StoreApp
import com.gothwad.store.ui.components.InstallMethodSheet
import com.gothwad.store.ui.components.LoadingState
import com.gothwad.store.ui.components.LocalAppSnackbarHost
import com.gothwad.store.ui.navigation.AppNavHost

/**
 * Root composable: owns the NavController, the single SnackbarHostState and the
 * Play-style install sheet, so a download started on one screen is announced
 * everywhere and survives navigation.
 */
@Composable
fun KiwiApp() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val vm: StoreViewModel = viewModel()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val bootstrapped = settings != null

    LaunchedEffect(Unit) {
        AppGraph.library.load()
        AppGraph.downloads.load()
        AppGraph.installer.refreshCapabilities()
        // Home and the categories are already requested by the ViewModel's
        // init block; asking again here would only duplicate the traffic.
        vm.refreshApps()
        vm.refreshGames()
        // The Kiwi tab is the web store: only the Worker catalogue, no F-Droid.
        vm.refreshKiwi()
        // The optional F-Droid catalogue is downloaded in the background and
        // shows up as its own shelf once it arrives.
        vm.loadFdroid()
    }

    val startDestination = when {
        !bootstrapped -> Routes.HOME
        settings?.onboardingDone == true -> Routes.HOME
        else -> Routes.WELCOME
    }

    CompositionLocalProvider(LocalAppSnackbarHost provides snackbarHostState) {
        if (!bootstrapped) {
            LoadingState(label = "Starting Kiwi Store…")
            return@CompositionLocalProvider
        }
        Box(Modifier.fillMaxSize()) {
            AppNavHost(
                navController = navController,
                vm = vm,
                snackbarHostState = snackbarHostState,
                startDestination = startDestination,
            )
            EventPump(vm, navController, snackbarHostState)
        }
    }
}

/**
 * One place that reacts to [StoreEvent]s, notification deep links and the
 * "install" action from the download notification.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventPump(
    vm: StoreViewModel,
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
) {
    val event by vm.events.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val capabilities by AppGraph.installer.capabilities.collectAsStateWithLifecycle()
    val navigationRequest by AppGraph.navigationRequest.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var sheetApp by remember { mutableStateOf<StoreApp?>(null) }
    var sheetTask by remember { mutableStateOf<DownloadTask?>(null) }

    // Notification taps and MainActivity intent extras.
    LaunchedEffect(navigationRequest) {
        val request = navigationRequest ?: return@LaunchedEffect
        when (request.route) {
            "install" -> {
                val task = request.taskId?.let { id -> vm.downloads.value.firstOrNull { it.id == id } }
                    ?: vm.downloads.value.firstOrNull { it.canInstall }
                if (task != null) sheetTask = task else navController.navigate(Routes.DOWNLOADS)
            }
            "downloads" -> navController.navigate(Routes.DOWNLOADS)
            else -> navController.navigate(request.route) { launchSingleTop = true }
        }
        AppGraph.consumeNavigation()
    }

    LaunchedEffect(event) {
        when (val current = event) {
            null -> Unit
            is StoreEvent.Message -> {
                snackbarHostState.showSnackbar(current.text)
                vm.consumeEvent()
            }
            is StoreEvent.NeedsSignIn -> {
                snackbarHostState.showSnackbar(current.reason)
                vm.consumeEvent()
                navController.navigate(Routes.SIGN_IN)
            }
            StoreEvent.NeedsInstallPermission -> {
                snackbarHostState.showSnackbar("Kiwi Store needs permission to install apps.")
                vm.consumeEvent()
                navController.navigate(Routes.INSTALL_PERMISSION)
            }
            is StoreEvent.ShowInstallSheet -> {
                sheetApp = current.app
                sheetTask = AppGraph.downloader.taskFor(current.app.id)
                vm.consumeEvent()
            }
        }
    }

    val app = sheetApp
    val task = sheetTask
    if (app != null || task != null) {
        ModalBottomSheet(
            onDismissRequest = {
                sheetApp = null
                sheetTask = null
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = null,
        ) {
            InstallMethodSheet(
                appName = app?.name ?: task?.appName.orEmpty(),
                versionLabel = app?.let { vm.releaseFor(it)?.versionName } ?: task?.versionName.orEmpty(),
                sizeLabel = app?.let { vm.releaseFor(it)?.let { release -> Format.bytes(release.apkSizeBytes) } }
                    ?: Format.bytes(task?.totalBytes ?: 0L),
                selected = settings?.installMethod ?: InstallMethod.Session,
                capabilities = capabilities,
                onPick = { picked ->
                    val currentApp = app
                    val currentTask = task
                    sheetApp = null
                    sheetTask = null
                    scope.launch {
                        if (picked != InstallMethod.Ask) AppGraph.settings.setInstallMethod(picked)
                        when {
                            vm.installerNeedsPermission(picked) ->
                                vm.emit(
                                    StoreEvent.Message(
                                        "${picked.displayName()} needs a one-time setup first.",
                                    ),
                                )
                            currentTask != null && !currentTask.filePath.isNullOrBlank() ->
                                vm.installDownloaded(currentTask, picked)
                            currentApp != null -> vm.startInstallWith(currentApp, picked)
                            else -> Unit
                        }
                    }
                },
                onDismiss = {
                    sheetApp = null
                    sheetTask = null
                },
            )
        }
    }
}
