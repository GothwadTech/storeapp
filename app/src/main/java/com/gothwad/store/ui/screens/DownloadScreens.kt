package com.gothwad.store.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.gothwad.store.core.AppGraph
import com.gothwad.store.core.Format
import com.gothwad.store.data.download.DownloadStatus
import com.gothwad.store.data.download.DownloadTask
import com.gothwad.store.data.download.canInstall
import com.gothwad.store.data.download.downloadedLabel
import com.gothwad.store.data.download.isActive
import com.gothwad.store.data.download.isFinished
import com.gothwad.store.data.download.needsRetry
import com.gothwad.store.data.download.progress
import com.gothwad.store.data.download.sizeLabel
import com.gothwad.store.data.install.InstallMethod
import com.gothwad.store.ui.Routes
import com.gothwad.store.ui.StoreEvent
import com.gothwad.store.ui.StoreViewModel
import com.gothwad.store.ui.components.AppIcon
import com.gothwad.store.ui.components.BannerTone
import com.gothwad.store.ui.components.CheckRow
import com.gothwad.store.ui.components.DividerLine
import com.gothwad.store.ui.components.EmptyState
import com.gothwad.store.ui.components.GothwadScaffold
import com.gothwad.store.ui.components.GothwadTopBar
import com.gothwad.store.ui.components.InfoBanner
import com.gothwad.store.ui.components.IndeterminateBar
import com.gothwad.store.ui.components.KeyValueRow
import com.gothwad.store.ui.components.OutlineButton
import com.gothwad.store.ui.components.PillLabel
import com.gothwad.store.ui.components.ProgressBar
import com.gothwad.store.ui.components.RadioSettingRow
import com.gothwad.store.ui.components.SectionHeader
import com.gothwad.store.ui.components.SegmentedRow
import com.gothwad.store.ui.components.SettingRow
import com.gothwad.store.ui.components.SettingsGroup
import com.gothwad.store.ui.components.StatTile
import com.gothwad.store.ui.components.SubScreenHeader
import com.gothwad.store.ui.components.SwitchSettingRow
import com.gothwad.store.ui.theme.GothwadTheme
import com.gothwad.store.ui.theme.Spacing

private enum class DownloadTab(val label: String) {
    Active("Active"),
    Completed("Installed"),
    Failed("Issues"),
}

/** "Manage downloads" — the live queue with pause/resume/retry/install controls. */
@Composable
fun DownloadsScreen(vm: StoreViewModel, nav: NavController) {
    val tasks by vm.downloads.collectAsStateWithLifecycle()
    val capabilities by AppGraph.installer.capabilities.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    var tab by rememberSaveable { mutableStateOf(DownloadTab.Active) }
    var confirmCancel by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { AppGraph.downloads.load() }

    val active = tasks.filter { it.isActive || it.status == DownloadStatus.PAUSED }
    val completed = tasks.filter { it.isFinished }
    val failed = tasks.filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }
    val method = settings?.installMethod ?: InstallMethod.Session

    GothwadScaffold(
        topBar = {
            GothwadTopBar(
                title = "Manage downloads",
                subtitle = "${active.size} active · ${Format.bytes(AppGraph.downloader.usedBytes())} on disk",
                onBack = { nav.popBackStack() },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.DOWNLOAD_HISTORY) }) {
                        Icon(Icons.Filled.History, contentDescription = "History", tint = GothwadTheme.extra.textSecondary)
                    }
                    IconButton(onClick = { nav.navigate(Routes.STORAGE) }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Storage", tint = GothwadTheme.extra.textSecondary)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SegmentedRow(
                options = DownloadTab.entries.map { it.label },
                selectedIndex = tab.ordinal,
                onSelect = { tab = DownloadTab.entries[it] },
            )

            if (tasks.isEmpty()) {
                EmptyState(
                    title = "No downloads yet",
                    message = "Install or update an app and Gothwad Store downloads the APK here first, " +
                        "verifies the publisher checksum, then hands it to the installer you picked.",
                    icon = Icons.Filled.Download,
                    actionLabel = "Browse apps",
                    onAction = { nav.navigate(Routes.APPS) },
                )
                return@Column
            }

            val visible = when (tab) {
                DownloadTab.Active -> active
                DownloadTab.Completed -> completed
                DownloadTab.Failed -> failed
            }

            LazyColumn(contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding)) {
                if (visible.isEmpty()) {
                    item {
                        InfoBanner(
                            title = when (tab) {
                                DownloadTab.Active -> "Nothing downloading"
                                DownloadTab.Completed -> "No APK is kept on this device"
                                DownloadTab.Failed -> "No failed downloads"
                            },
                            message = when (tab) {
                                DownloadTab.Active -> "Queued and running downloads show up here with live progress."
                                DownloadTab.Completed -> "Finished APKs are listed here until you clear or delete them."
                                DownloadTab.Failed -> "Anything that broke — network drops, bad checksums, installer refusals — lands here."
                            },
                        )
                    }
                } else {
                    if (active.isNotEmpty() && tab != DownloadTab.Active) {
                        item {
                            Text(
                                "${active.size} download${if (active.size == 1) "" else "s"} still running in the background",
                                modifier = Modifier.padding(Spacing.screenH),
                                fontSize = 12.sp,
                                color = GothwadTheme.extra.textSecondary,
                            )
                        }
                    }
                    items(visible, key = { it.id }) { task ->
                        Column {
                            DownloadTaskCard(
                                task = task,
                                method = method,
                                onOpenApp = { vm.emit(StoreEvent.Message("Opening ${task.appName}…")); vm.openInstalledApp(task.packageName) },
                                onPause = { AppGraph.downloader.pause(task.id) },
                                onResume = { AppGraph.downloader.resume(task.id) },
                                onRetry = { AppGraph.downloader.retry(task.id) },
                                onCancel = { confirmCancel = task.id },
                                onInstall = { vm.installDownloaded(task, method) },
                                onInstallWith = { nav.navigate(Routes.INSTALL_SETTINGS) },
                                onOpenAppDetail = { nav.navigate(Routes.app(task.appId)) },
                                onOpenApk = {
                                    if (!AppGraph.downloader.openApk(task)) {
                                        vm.emit(StoreEvent.Message("No APK file is available to open."))
                                    }
                                },
                                onExport = {
                                    scope.launch {
                                        AppGraph.downloader.exportToDownloads(task)
                                            .onSuccess { vm.emit(StoreEvent.Message(it)) }
                                            .onFailure { vm.emit(StoreEvent.Message(it.message ?: "Export failed.")) }
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        AppGraph.downloader.remove(task.id)
                                        vm.emit(StoreEvent.Message("Removed ${task.appName} from downloads."))
                                    }
                                },
                                onShare = {
                                    if (!AppGraph.downloader.shareApk(task)) {
                                        vm.emit(StoreEvent.Message("Nothing to share — the APK file is gone."))
                                    }
                                },
                            )
                            DividerLine(startIndent = 64.dp)
                        }
                    }
                }

                item {
                    Column(Modifier.padding(Spacing.screenH)) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlineButton(
                                label = "Pause all",
                                onClick = { AppGraph.downloader.pauseAll() },
                                enabled = active.any { it.status == DownloadStatus.RUNNING },
                                modifier = Modifier.weight(1f),
                            )
                            OutlineButton(
                                label = "Resume all",
                                onClick = { AppGraph.downloader.resumeAll() },
                                enabled = active.any { it.status == DownloadStatus.PAUSED },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlineButton(
                            label = "Clear finished downloads",
                            onClick = {
                                scope.launch {
                                    AppGraph.downloader.clearFinished()
                                    vm.emit(StoreEvent.Message("Cleared finished downloads."))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Installer in use: ${method.displayName()}${capabilities.silentMethod?.let { " · silent option: ${it.displayName()}" } ?: ""}",
                            fontSize = 12.sp,
                            color = GothwadTheme.extra.textTertiary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "APKs live in ${AppGraph.downloads.apkDir.absolutePath}",
                            fontSize = 11.sp,
                            color = GothwadTheme.extra.textTertiary,
                        )
                    }
                }
            }
        }
    }

    val cancelId = confirmCancel
    if (cancelId != null) {
        com.gothwad.store.ui.components.ConfirmDialog(
            title = "Cancel this download?",
            message = "The partially downloaded file is deleted and the job disappears from the queue.",
            confirmLabel = "Cancel download",
            dismissLabel = "Keep downloading",
            onConfirm = {
                AppGraph.downloader.cancel(cancelId)
                confirmCancel = null
            },
            onDismiss = { confirmCancel = null },
        )
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTask,
    method: InstallMethod,
    onOpenApp: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onInstallWith: () -> Unit,
    onOpenAppDetail: () -> Unit,
    onOpenApk: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenH, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.clickable(onClick = onOpenAppDetail)) {
                AppIcon(iconUrl = task.iconUrl, name = task.appName, size = 50.dp, corner = 13.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    task.appName.ifBlank { task.fileName },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = GothwadTheme.extra.textPrimary,
                )
                Text(
                    buildString {
                        append("v${task.versionName.ifBlank { "?" }} · ${task.sizeLabel}")
                        if (task.method.displayName() != method.displayName()) append(" · ${task.method.displayName()}")
                    },
                    fontSize = 12.sp,
                    color = GothwadTheme.extra.textSecondary,
                    maxLines = 1,
                )
                Text(
                    when (task.status) {
                        DownloadStatus.QUEUED -> "Waiting for a slot"
                        DownloadStatus.RUNNING -> "${Format.bytes(task.bytesDownloaded)} of ${task.sizeLabel} downloading"
                        DownloadStatus.PAUSED -> "Paused · ${task.downloadedLabel}"
                        DownloadStatus.COMPLETED -> "Ready to install"
                        DownloadStatus.INSTALLED -> "Installed"
                        DownloadStatus.FAILED -> task.error ?: "Download failed"
                        DownloadStatus.CANCELLED -> "Cancelled"
                    },
                    fontSize = 12.sp,
                    color = when (task.status) {
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                        DownloadStatus.COMPLETED, DownloadStatus.INSTALLED -> GothwadTheme.extra.success
                        else -> GothwadTheme.extra.textTertiary
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            when {
                task.isActive || task.status == DownloadStatus.PAUSED -> Row {
                    if (task.status == DownloadStatus.PAUSED) {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Resume", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = GothwadTheme.extra.textSecondary)
                        }
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = GothwadTheme.extra.textSecondary)
                    }
                }
                task.needsRetry -> IconButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Retry", tint = MaterialTheme.colorScheme.primary)
                }
                task.isInstalled -> PillLabel(
                    text = "Installed",
                    background = GothwadTheme.extra.successContainer,
                    contentColor = GothwadTheme.extra.success,
                )
                task.canInstall -> PillLabel(
                    text = "Ready",
                    background = GothwadTheme.extra.chipBackground,
                    contentColor = GothwadTheme.extra.textSecondary,
                )
            }
        }

        when (task.status) {
            DownloadStatus.RUNNING -> {
                Spacer(Modifier.height(10.dp))
                ProgressBar(progress = task.progress.calculateProgress(), modifier = Modifier.fillMaxWidth())
            }
            DownloadStatus.QUEUED -> {
                Spacer(Modifier.height(10.dp))
                IndeterminateBar(modifier = Modifier.fillMaxWidth())
            }
            else -> Unit
        }

        if (task.canInstall) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                com.gothwad.store.ui.components.PrimaryButton(
                    label = if (task.isInstalled) "Open" else "Install",
                    onClick = { if (task.isInstalled) onOpenApp() else onInstall() },
                    modifier = Modifier.weight(1f),
                )
                OutlineButton(label = "Change method", onClick = onInstallWith, modifier = Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButtonMini(label = "Open APK", onClick = onOpenApk, enabled = task.filePath != null)
            TextButtonMini(label = "Share", onClick = onShare, enabled = task.filePath != null)
            TextButtonMini(label = "Save to Downloads", onClick = onExport, enabled = task.filePath != null)
            TextButtonMini(label = "Delete", onClick = onDelete, danger = true)
        }
    }
}

@Composable
private fun TextButtonMini(label: String, onClick: () -> Unit, enabled: Boolean = true, danger: Boolean = false) {
    Text(
        label,
        fontSize = 12.sp,
        color = when {
            !enabled -> GothwadTheme.extra.textTertiary
            danger -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        },
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

private fun Float.calculateProgress(): Float = coerceIn(0f, 1f)

@Composable
fun DownloadHistoryScreen(vm: StoreViewModel, nav: NavController) {
    val tasks by vm.downloads.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    GothwadScaffold(
        topBar = {
            GothwadTopBar(
                title = "Download history",
                onBack = { nav.popBackStack() },
                actions = {
                    IconButton(onClick = { scope.launch { AppGraph.downloader.clearFinished() } }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear", tint = GothwadTheme.extra.textSecondary)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            if (settings?.historyEnabled == false) {
                item {
                    InfoBanner(
                        title = "History is off",
                        message = "Turn on “Keep download history” to record every APK Gothwad Store fetches.",
                        tone = BannerTone.Warning,
                        action = {
                            OutlineButton("Open download settings", onClick = { nav.navigate(Routes.DOWNLOAD_SETTINGS) })
                        },
                    )
                }
            }
            if (tasks.isEmpty()) {
                item {
                    EmptyState(
                        title = "Nothing in the history",
                        message = "Downloads you completed, cancelled or failed are logged here with timestamps.",
                        icon = Icons.Filled.History,
                    )
                }
            } else {
                items(tasks.sortedByDescending { it.updatedAt }, key = { it.id }) { task ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenH, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppIcon(iconUrl = task.iconUrl, name = task.appName, size = 40.dp, corner = 11.dp)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    task.appName.ifBlank { task.fileName },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "v${task.versionName} · ${Format.bytes(task.totalBytes)} · ${task.method.displayName()}",
                                    fontSize = 11.sp,
                                    color = GothwadTheme.extra.textTertiary,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                PillLabel(
                                    text = task.status.name.lowercase().replaceFirstChar { it.uppercase() },
                                    background = GothwadTheme.extra.chipBackground,
                                    contentColor = GothwadTheme.extra.textSecondary,
                                )
                                Text(
                                    Format.relative(task.updatedAt.takeIf { it > 0 } ?: task.downloadedAt),
                                    fontSize = 10.sp,
                                    color = GothwadTheme.extra.textTertiary,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButtonMini("Details", onClick = { nav.navigate(Routes.app(task.appId)) }, enabled = task.appId.isNotBlank())
                            TextButtonMini("Save to Downloads", onClick = {
                                scope.launch {
                                    AppGraph.downloader.exportToDownloads(task)
                                        .onSuccess { vm.emit(StoreEvent.Message(it)) }
                                        .onFailure { vm.emit(StoreEvent.Message(it.message ?: "Export failed.")) }
                                }
                            }, enabled = task.filePath != null)
                            TextButtonMini("Remove", danger = true, onClick = {
                                scope.launch { AppGraph.downloader.remove(task.id) }
                            })
                        }
                    }
                    DividerLine(startIndent = 64.dp)
                }
            }
        }
    }
}

@Composable
fun DownloadSettingsScreen(vm: StoreViewModel, nav: NavController) {
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val capabilities by AppGraph.installer.capabilities.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val current = settings ?: return

    GothwadScaffold(
        topBar = {
            SubScreenHeader(
                title = "Download preferences",
                subtitle = "Networks, checksums and cleanup",
                onBack = { nav.popBackStack() },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item { SectionHeader(title = "Network") }
            item {
                SettingsGroup {
                    SwitchSettingRow(
                        title = "Download over Wi-Fi only",
                        subtitle = "Mobile data is never used for APKs",
                        checked = current.downloadOnWifiOnly,
                        onCheckedChange = { scope.launch { AppGraph.settings.setWifiOnly(it) } },
                    )
                    SwitchSettingRow(
                        title = "Data saver",
                        subtitle = "Pause large downloads on metered networks",
                        checked = current.dataSaver,
                        onCheckedChange = { scope.launch { AppGraph.settings.setDataSaver(it) } },
                    )
                    SwitchSettingRow(
                        title = "Wi-Fi only for updates",
                        subtitle = "Auto-updates wait for an unmetered connection",
                        checked = current.wifiOnlyUpdates,
                        onCheckedChange = { scope.launch { AppGraph.settings.setWifiOnlyUpdates(it) } },
                    )
                }
            }

            item { SectionHeader(title = "Parallel downloads") }
            item {
                SettingsGroup {
                    (1..3).forEach { slots ->
                        RadioSettingRow(
                            title = "$slots at a time",
                            subtitle = when (slots) {
                                1 -> "Gentlest on battery and bandwidth"
                                2 -> "Balanced"
                                else -> "Fastest — three APKs at once"
                            },
                            selected = current.downloadParallel == slots,
                            onClick = { scope.launch { AppGraph.settings.setParallel(slots) } },
                        )
                    }
                }
            }

            item { SectionHeader(title = "After the download") }
            item {
                SettingsGroup {
                    SwitchSettingRow(
                        title = "Verify SHA-256 checksum",
                        subtitle = "Compare the APK with the publisher's hash before installing",
                        checked = current.verifyChecksum,
                        onCheckedChange = { scope.launch { AppGraph.settings.setVerifyChecksum(it) } },
                    )
                    SwitchSettingRow(
                        title = "Install automatically when finished",
                        subtitle = if (capabilities.silentMethod != null) {
                            "Uses ${capabilities.silentMethod?.displayName()} for a silent install"
                        } else {
                            "Android still shows its confirmation dialog"
                        },
                        checked = current.autoInstallAfterDownload,
                        onCheckedChange = { scope.launch { AppGraph.settings.setAutoInstall(it) } },
                    )
                    SwitchSettingRow(
                        title = "Confirm before installing",
                        subtitle = "Show the installer sheet so you can change method per app",
                        checked = current.confirmBeforeInstall,
                        onCheckedChange = { scope.launch { AppGraph.settings.setConfirmBeforeInstall(it) } },
                    )
                    SwitchSettingRow(
                        title = "Delete APK after installing",
                        subtitle = "Frees storage as soon as Android finishes",
                        checked = current.deleteApkAfterInstall,
                        onCheckedChange = {
                            scope.launch {
                                AppGraph.settings.setDeleteApkAfterInstall(it)
                                if (it) AppGraph.settings.setKeepApk(false)
                            }
                        },
                    )
                    SwitchSettingRow(
                        title = "Keep APK on device",
                        subtitle = "Leave the file so you can reinstall offline",
                        checked = current.keepApkAfterInstall,
                        onCheckedChange = {
                            scope.launch {
                                AppGraph.settings.setKeepApk(it)
                                if (it) AppGraph.settings.setDeleteApkAfterInstall(false)
                            }
                        },
                    )
                    SwitchSettingRow(
                        title = "Allow downgrades",
                        subtitle = "Install an older version over a newer one (pm install -d)",
                        checked = current.allowDowngrade,
                        onCheckedChange = { scope.launch { AppGraph.settings.setAllowDowngrade(it) } },
                    )
                    SwitchSettingRow(
                        title = "Keep download history",
                        subtitle = "Remember finished and failed jobs",
                        checked = current.historyEnabled,
                        onCheckedChange = { scope.launch { AppGraph.settings.setHistoryEnabled(it) } },
                    )
                }
            }

            item { SectionHeader(title = "Notifications") }
            item {
                SettingsGroup {
                    SwitchSettingRow(
                        title = "Download notifications",
                        subtitle = "Progress, completion and install results",
                        checked = current.notifyDownloads,
                        onCheckedChange = { scope.launch { AppGraph.settings.setNotifyDownloads(it) } },
                    )
                    SwitchSettingRow(
                        title = "Update notifications",
                        subtitle = "Tell me when an installed app has a new release",
                        checked = current.notifyUpdates,
                        onCheckedChange = { scope.launch { AppGraph.settings.setNotifyUpdates(it) } },
                    )
                }
            }

            item { SectionHeader(title = "Installer") }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "Install method",
                        subtitle = current.installMethod.displayName(),
                        onClick = { nav.navigate(Routes.INSTALL_METHOD) },
                    )
                    SettingRow(
                        title = "Installer health",
                        subtitle = capabilities.healthSummary(),
                        onClick = { nav.navigate(Routes.INSTALL_SETTINGS) },
                    )
                    SettingRow(
                        title = "Storage used by APKs",
                        subtitle = Format.bytes(AppGraph.downloader.usedBytes()),
                        onClick = { nav.navigate(Routes.STORAGE) },
                    )
                }
            }
        }
    }
}

@Composable
fun StorageScreen(vm: StoreViewModel, nav: NavController) {
    val tasks by vm.downloads.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var orphans by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) { AppGraph.downloads.load() }

    val onDisk = tasks.filter { it.filePath != null }
    val used = onDisk.sumOf { task -> task.totalBytes.takeIf { it > 0 } ?: task.bytesDownloaded }

    GothwadScaffold(
        topBar = {
            SubScreenHeader(
                title = "Storage",
                subtitle = "APKs kept by Gothwad Store",
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
                    StatTile(label = "APKs kept", value = onDisk.size.toString(), modifier = Modifier.weight(1f))
                    StatTile(label = "Space used", value = Format.bytes(used), modifier = Modifier.weight(1f))
                    StatTile(label = "Orphan files", value = Format.bytes(orphans), modifier = Modifier.weight(1f))
                }
            }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "Prune orphan files",
                        subtitle = "Delete APKs in the download folder that no job references",
                        onClick = { orphans = AppGraph.downloader.pruneOrphans() },
                    )
                    SettingRow(
                        title = "Delete every kept APK",
                        subtitle = "Keeps the history rows, removes the files",
                        onClick = {
                            scope.launch {
                                onDisk.forEach { AppGraph.downloader.deleteTaskFile(it) }
                                vm.emit(StoreEvent.Message("Deleted ${onDisk.size} APK file(s)."))
                            }
                        },
                    )
                    SettingRow(
                        title = "Clear finished history",
                        subtitle = "Removes completed and installed rows",
                        onClick = { scope.launch { AppGraph.downloader.clearFinished() } },
                    )
                    SettingRow(
                        title = "Open download folder",
                        subtitle = AppGraph.downloads.apkDir.absolutePath,
                    )
                }
            }
            item { SectionHeader(title = "Kept APKs") }
            if (onDisk.isEmpty()) {
                item {
                    EmptyState(
                        title = "No APK files",
                        message = "Either nothing has been downloaded yet, or cleanup already removed the files.",
                        icon = Icons.Filled.DeleteSweep,
                    )
                }
            } else {
                items(onDisk, key = { it.id }) { task ->
                    Column {
                        KeyValueRow(task.appName, task.sizeLabel)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.screenH),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            TextButtonMini("Save to Downloads", onClick = {
                                scope.launch {
                                    AppGraph.downloader.exportToDownloads(task)
                                        .onSuccess { vm.emit(StoreEvent.Message(it)) }
                                        .onFailure { vm.emit(StoreEvent.Message(it.message ?: "Export failed.")) }
                                }
                            })
                            TextButtonMini("Delete file", danger = true, onClick = {
                                scope.launch { AppGraph.downloader.deleteTaskFile(task) }
                            })
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(Spacing.screenH)) {
                    InfoBanner(
                        title = "Where APKs live",
                        message = "Files sit in the app's private external folder, so Android's " +
                            "file manager will not list them. Export an APK to make it visible in Downloads.",
                    )
                }
            }
        }
    }
}
