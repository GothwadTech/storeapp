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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.gothwad.store.BuildConfig
import com.gothwad.store.core.AppGraph
import com.gothwad.store.data.install.InstallMethod
import com.gothwad.store.data.install.InstallerAvailability
import com.gothwad.store.data.install.Installers
import com.gothwad.store.ui.Routes
import com.gothwad.store.ui.StoreEvent
import com.gothwad.store.ui.StoreViewModel
import com.gothwad.store.ui.components.BannerTone
import com.gothwad.store.ui.components.CheckRow
import com.gothwad.store.ui.components.GothwadScaffold
import com.gothwad.store.ui.components.InfoBanner
import com.gothwad.store.ui.components.IconTextRow
import com.gothwad.store.ui.components.KeyValueRow
import com.gothwad.store.ui.components.OutlineButton
import com.gothwad.store.ui.components.PillLabel
import com.gothwad.store.ui.components.RadioSettingRow
import com.gothwad.store.ui.components.SectionHeader
import com.gothwad.store.ui.components.SettingRow
import com.gothwad.store.ui.components.SettingsGroup
import com.gothwad.store.ui.components.SubScreenHeader
import com.gothwad.store.ui.components.SwitchSettingRow
import com.gothwad.store.ui.theme.GothwadTheme
import com.gothwad.store.ui.theme.Spacing

/** Hardware/OS facts the install help screens quote. */
@Composable
private fun DeviceFactsBlock() {
    Column {
        KeyValueRow("Android version", "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        KeyValueRow("Device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        KeyValueRow("Architecture", android.os.Build.SUPPORTED_ABIS.joinToString(", "))
        KeyValueRow("App id", BuildConfig.APPLICATION_ID)
    }
}

@Composable
fun InstallSettingsScreen(vm: StoreViewModel, nav: NavController) {
    val capabilities by AppGraph.installer.capabilities.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val current = settings

    LaunchedEffect(Unit) { AppGraph.installer.refreshCapabilities() }

    GothwadScaffold(
        topBar = {
            SubScreenHeader(
                title = "Installer",
                subtitle = capabilities.healthSummary(),
                onBack = { nav.popBackStack() },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item {
                Column(Modifier.padding(Spacing.screenH)) {
                    InfoBanner(
                        title = "Gothwad Store verifies, Android installs",
                        message = "APKs are downloaded to app storage, hashed with SHA-256 and only then handed " +
                            "to the installer you choose below. Privileged methods (Shizuku/root) skip Android's " +
                            "confirmation dialog; everything else shows it.",
                    )
                }
            }

            item { SectionHeader(title = "Installer method") }
            item {
                SettingsGroup {
                    InstallMethod.selectable.forEach { method ->
                        val availability = capabilities.statusOf(method)
                        RadioSettingRow(
                            title = method.displayName(),
                            subtitle = method.blurb,
                            selected = current?.installMethod == method,
                            badge = { AvailabilityPill(availability) },
                            onClick = {
                                scope.launch {
                                    AppGraph.settings.setInstallMethod(method)
                                    vm.emit(StoreEvent.Message("Installs now use ${method.displayName()}."))
                                }
                            },
                        )
                    }
                }
            }

            item { SectionHeader(title = "Capabilities") }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "Install unknown apps",
                        subtitle = if (capabilities.installPermissionGranted) "Granted" else "Not granted — required for the standard installer",
                        trailingText = if (capabilities.installPermissionGranted) "OK" else "Fix",
                        onClick = { nav.navigate(Routes.INSTALL_PERMISSION) },
                    )
                    SettingRow(
                        title = "Shizuku",
                        subtitle = when {
                            !capabilities.shizukuInstalled -> "Shizuku is not installed on this device"
                            !capabilities.shizukuRunning -> "Installed but the Shizuku service is stopped"
                            capabilities.shizukuPermissionGranted -> "Ready · service uid ${capabilities.shizukuUid}"
                            else -> "Installed and running — permission not granted"
                        },
                        trailingText = capabilities.labelFor(InstallMethod.Shizuku),
                        onClick = { nav.navigate(Routes.INSTALL_SHIZUKU) },
                    )
                    SettingRow(
                        title = "Root",
                        subtitle = if (capabilities.rootChecked) {
                            if (capabilities.rootGranted) "su available at ${capabilities.suPath ?: "su"}" else "No working su binary"
                        } else {
                            "Not checked yet — tap to run the su probe"
                        },
                        trailingText = capabilities.labelFor(InstallMethod.Root),
                        onClick = { nav.navigate(Routes.INSTALL_ROOT) },
                    )
                    SettingRow(
                        title = "ADB / wireless debugging",
                        subtitle = "Install Gothwad Store over ADB or pair it with Shizuku",
                        onClick = { nav.navigate(Routes.INSTALL_ADB) },
                    )
                    SettingRow(
                        title = "How installing works",
                        subtitle = "Every path explained, with troubleshooting",
                        onClick = { nav.navigate(Routes.INSTALL_HELP) },
                    )
                }
            }

            item { SectionHeader(title = "Behaviour") }
            item {
                SettingsGroup {
                    SwitchSettingRow(
                        title = "Confirm before installing",
                        subtitle = "Open the installer sheet for every install",
                        checked = current?.confirmBeforeInstall ?: true,
                        onCheckedChange = { scope.launch { AppGraph.settings.setConfirmBeforeInstall(it) } },
                    )
                    SwitchSettingRow(
                        title = "Verify checksum before install",
                        subtitle = "Refuse to install when the APK does not match the published SHA-256",
                        checked = current?.verifyChecksum ?: true,
                        onCheckedChange = { scope.launch { AppGraph.settings.setVerifyChecksum(it) } },
                    )
                    SwitchSettingRow(
                        title = "Allow downgrades",
                        subtitle = "Only meaningful for root/Shizuku installs (pm install -d)",
                        checked = current?.allowDowngrade ?: false,
                        onCheckedChange = { scope.launch { AppGraph.settings.setAllowDowngrade(it) } },
                    )
                }
            }

            item { SectionHeader(title = "This device") }
            item { DeviceFactsBlock() }
        }
    }
}

@Composable
private fun AvailabilityPill(availability: InstallerAvailability) {
    val (label, bg, fg) = when (availability) {
        InstallerAvailability.Available -> Triple(
            "Ready",
            GothwadTheme.extra.successContainer,
            GothwadTheme.extra.success,
        )
        InstallerAvailability.NeedsPermission -> Triple(
            "Permission",
            GothwadTheme.extra.warningContainer,
            GothwadTheme.extra.warning,
        )
        InstallerAvailability.NotInstalled -> Triple(
            "Not installed",
            GothwadTheme.extra.chipBackground,
            GothwadTheme.extra.textSecondary,
        )
        InstallerAvailability.Unavailable -> Triple(
            "Unavailable",
            GothwadTheme.extra.chipBackground,
            GothwadTheme.extra.textSecondary,
        )
    }
    PillLabel(text = label, background = bg, contentColor = fg)
}

@Composable
fun InstallMethodScreen(vm: StoreViewModel, nav: NavController) {
    val capabilities by AppGraph.installer.capabilities.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { AppGraph.installer.refreshCapabilities() }

    GothwadScaffold(
        topBar = {
            SubScreenHeader(
                title = "Install method",
                subtitle = "Default for new installs",
                onBack = { nav.popBackStack() },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item {
                InfoBanner(
                    title = "Pick the default",
                    message = "You can still override the method for a single install from the sheet that appears " +
                        "before every install, or from Manage downloads.",
                )
            }
            items(InstallMethod.selectable) { method ->
                Column {
                    RadioSettingRow(
                        title = method.displayName(),
                        subtitle = method.blurb,
                        selected = settings?.installMethod == method,
                        badge = { AvailabilityPill(capabilities.statusOf(method)) },
                        onClick = { scope.launch { AppGraph.settings.setInstallMethod(method) } },
                    )
                    when (method) {
                        InstallMethod.Session, InstallMethod.Intent -> SettingRow(
                            title = "Fix install permission",
                            subtitle = "Grant Install unknown apps to Gothwad Store",
                            onClick = { nav.navigate(Routes.INSTALL_PERMISSION) },
                        )
                        InstallMethod.Shizuku -> SettingRow(
                            title = "Set up Shizuku",
                            subtitle = "Install, start and authorise the Shizuku service",
                            onClick = { nav.navigate(Routes.INSTALL_SHIZUKU) },
                        )
                        InstallMethod.Root -> SettingRow(
                            title = "Test root access",
                            subtitle = "Run the su probe and see the result",
                            onClick = { nav.navigate(Routes.INSTALL_ROOT) },
                        )
                        InstallMethod.Ask -> SettingRow(
                            title = "ADB install instead",
                            subtitle = "Hand the APK to adb from your computer",
                            onClick = { nav.navigate(Routes.INSTALL_ADB) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InstallPermissionScreen(vm: StoreViewModel, nav: NavController) {
    val capabilities by AppGraph.installer.capabilities.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { AppGraph.installer.refreshCapabilities() }

    GothwadScaffold(
        topBar = {
            SubScreenHeader(
                title = "Install permission",
                subtitle = if (capabilities.installPermissionGranted) "Granted" else "Action needed",
                onBack = { nav.popBackStack() },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item {
                if (capabilities.installPermissionGranted) {
                    InfoBanner(
                        title = "Gothwad Store may install apps",
                        message = "Android will still show its own confirmation dialog for every APK unless you " +
                            "use Shizuku or root.",
                    )
                } else {
                    InfoBanner(
                        title = "Android is blocking installs",
                        message = "Gothwad Store has to be allowed to install unknown apps. Without this the " +
                            "session install fails with INSTALL_FAILED_USER_RESTRICTED.",
                        tone = BannerTone.Warning,
                    )
                }
            }
            item { SectionHeader(title = "How to grant it") }
            item {
                SettingsGroup {
                    IconTextRow(Icons.Filled.Android, "Tap “Open settings” below. Android jumps straight to the Gothwad Store entry.")
                    IconTextRow(Icons.Filled.Security, "Turn on “Allow from this source”.")
                    IconTextRow(Icons.Filled.VerifiedUser, "Come back here — the status refreshes automatically.")
                }
            }
            item {
                Column(Modifier.padding(Spacing.screenH)) {
                    Spacer(Modifier.height(8.dp))
                    com.gothwad.store.ui.components.PrimaryButton(
                        label = "Open settings",
                        onClick = { Installers.openInstallPermissionSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlineButton(
                        label = "Re-check now",
                        onClick = {
                            AppGraph.installer.refreshCapabilities()
                            vm.emit(StoreEvent.Message("Permission re-checked."))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item { SectionHeader(title = "Status") }
            item {
                Column {
                    KeyValueRow("Install unknown apps", capabilities.installPermissionGranted.yesNo())
                    KeyValueRow("Session installs", capabilities.statusOf(InstallMethod.Session).name)
                    KeyValueRow("Intent installs", capabilities.statusOf(InstallMethod.Intent).name)
                    KeyValueRow("Request install packages", "Declared in the manifest")
                }
            }
        }
    }
}

private fun Boolean.yesNo() = if (this) "Granted" else "Not granted"

@Composable
fun ShizukuScreen(vm: StoreViewModel, nav: NavController) {
    val capabilities by AppGraph.installer.capabilities.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        AppGraph.installer.refreshCapabilities()
        AppGraph.installer.checkRoot()
    }

    GothwadScaffold(
        topBar = {
            SubScreenHeader(
                title = "Shizuku",
                subtitle = capabilities.healthSummary(),
                onBack = { nav.popBackStack() },
                actions = {
                    OutlineButton("Re-check", onClick = { AppGraph.installer.refreshCapabilities() })
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item {
                InfoBanner(
                    title = "Silent installs without root",
                    message = "Shizuku runs a service with ADB (shell) identity. Gothwad Store talks to it over " +
                        "Binder and runs pm install for you — the same thing ADB does from a computer.",
                )
            }
            item { SectionHeader(title = "Status") }
            item {
                Column {
                    KeyValueRow("Shizuku installed", capabilities.shizukuInstalled.yesNoWord())
                    KeyValueRow("Service running", capabilities.shizukuRunning.yesNoWord())
                    KeyValueRow("Permission", capabilities.shizukuPermissionGranted.yesNo())
                    KeyValueRow("Service version", capabilities.shizukuVersion.toString())
                    KeyValueRow(
                        "Service uid",
                        when (capabilities.shizukuUid) {
                            0 -> "0 (root)"
                            2000 -> "2000 (shell/ADB)"
                            else -> capabilities.shizukuUid.toString()
                        },
                    )
                }
            }

            item { SectionHeader(title = "Steps") }
            item {
                SettingsGroup {
                    IconTextRow(Icons.Filled.Android, "1. If Shizuku is missing, install it from shizuku.rikka.app (or add Sui through Magisk/KernelSU).")
                    IconTextRow(Icons.Filled.Bolt, "2. Start Shizuku: Wireless debugging on Android 11+, or adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh on older builds.")
                    IconTextRow(Icons.Filled.Security, "3. Tap “Request permission” here and allow Gothwad Store.")
                    IconTextRow(Icons.Filled.VerifiedUser, "4. Pick “Shizuku / Sui (ADB)” as the default install method.")
                }
            }

            item {
                Column(Modifier.padding(Spacing.screenH)) {
                    Spacer(Modifier.height(6.dp))
                    com.gothwad.store.ui.components.PrimaryButton(
                        label = "Request permission",
                        onClick = {
                            scope.launch {
                                AppGraph.installer.requestShizukuPermission()
                                AppGraph.installer.refreshCapabilities()
                            }
                        },
                        enabled = capabilities.shizukuRunning,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlineButton(
                        label = "Open Shizuku",
                        onClick = {
                            val launch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                            if (launch != null) {
                                context.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                            } else {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(BuildConfig.SHIZUKU_DOWNLOAD_URL),
                                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Shizuku needs Android 6.0 or newer. On Android 11+ enable Developer options → " +
                            "Wireless debugging before starting the service.",
                        fontSize = 12.sp,
                        color = GothwadTheme.extra.textTertiary,
                    )
                }
            }
        }
    }
}

private fun Boolean.yesNoWord() = if (this) "Yes" else "No"

@Composable
fun RootScreen(vm: StoreViewModel, nav: NavController) {
    val capabilities by AppGraph.installer.capabilities.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var lastLog by remember { mutableStateOf<String?>(null) }
    var probing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { AppGraph.installer.checkRoot() }

    GothwadScaffold(
        topBar = {
            SubScreenHeader(
                title = "Root installs",
                subtitle = if (capabilities.rootGranted) "su granted" else "Not granted",
                onBack = { nav.popBackStack() },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item {
                InfoBanner(
                    title = "Root is optional",
                    message = "With root Gothwad Store can run pm install silently and even downgrade apps. " +
                        "The app never asks for root unless you pick this method.",
                    tone = if (capabilities.rootGranted) BannerTone.Info else BannerTone.Warning,
                )
            }
            item { SectionHeader(title = "Probe") }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "Run su probe",
                        subtitle = capabilities.suPath?.let { "Found: $it" } ?: "Executes su -c id and reads the reply",
                        trailingText = if (probing) "…" else "Run",
                        onClick = {
                            probing = true
                            scope.launch {
                                val result = AppGraph.installer.probeRoot()
                                lastLog = result
                                probing = false
                            }
                        },
                    )
                    SettingRow(
                        title = "Open Magisk / KernelSU",
                        subtitle = "Grant Gothwad Store superuser access there",
                        onClick = {
                            val context = AppGraph.context()
                            listOf("com.topjohnwu.magisk", "me.weishu.kernelsu", "eu.chainfire.supersu").forEach { pkg ->
                                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                                if (intent != null) {
                                    context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                                    return@SettingRow
                                }
                            }
                            vm.emit(StoreEvent.Message("No root manager app was found."))
                        },
                    )
                }
            }
            if (lastLog != null) {
                item { SectionHeader(title = "Probe output") }
                item {
                    Column(Modifier.padding(horizontal = Spacing.screenH)) {
                        Text(
                            lastLog.orEmpty(),
                            fontSize = 12.sp,
                            color = GothwadTheme.extra.textSecondary,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        )
                    }
                }
            }
            item { SectionHeader(title = "What root can do here") }
            item {
                SettingsGroup {
                    IconTextRow(Icons.Filled.Terminal, "pm install -r -d <apk> as uid 0, silently")
                    IconTextRow(Icons.Filled.Storage, "Read the real APK paths of installed packages")
                    IconTextRow(Icons.Filled.Security, "Downgrades and split APK installs without the session dialog")
                }
            }
            item { SectionHeader(title = "Device") }
            item { DeviceFactsBlock() }
        }
    }
}

@Composable
fun AdbScreen(vm: StoreViewModel, nav: NavController) {
    val context = LocalContext.current
    val packageName = BuildConfig.APPLICATION_ID

    GothwadScaffold(
        topBar = { SubScreenHeader(title = "Install over ADB", onBack = { nav.popBackStack() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item {
                InfoBanner(
                    title = "ADB is the most reliable silent path",
                    message = "If Shizuku or root are not available, install the APK straight from a computer. " +
                        "Gothwad Store hands you the file and the exact command.",
                )
            }
            item { SectionHeader(title = "Commands") }
            item {
                Column(Modifier.padding(horizontal = Spacing.screenH)) {
                    CommandBlock("adb install -r gothwadstore.apk")
                    CommandBlock("adb shell pm install -r /sdcard/Download/gothwadstore.apk")
                    CommandBlock("adb shell pm list packages | grep gothwad")
                    CommandBlock("adb uninstall $packageName")
                }
            }
            item { SectionHeader(title = "Wireless debugging") }
            item {
                SettingsGroup {
                    IconTextRow(Icons.Filled.Bolt, "Android 11+: Developer options → Wireless debugging → Pair device with pairing code.")
                    IconTextRow(Icons.Filled.Terminal, "adb pair <ip:pair-port> then adb connect <ip:port>.")
                    IconTextRow(Icons.Filled.Security, "Shizuku can use the same Wireless debugging session for in-app silent installs.")
                }
            }
            item {
                Column(Modifier.padding(Spacing.screenH)) {
                    Spacer(Modifier.height(8.dp))
                    OutlineButton(
                        label = "Open Developer options",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlineButton(
                        label = "Save APK from downloads",
                        onClick = { nav.navigate(Routes.DOWNLOADS) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item { SectionHeader(title = "This device") }
            item { DeviceFactsBlock() }
        }
    }
}

@Composable
private fun CommandBlock(command: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            command,
            fontSize = 13.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = GothwadTheme.extra.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        )
    }
}

@Composable
fun InstallHelpScreen(vm: StoreViewModel, nav: NavController) {
    val capabilities by AppGraph.installer.capabilities.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    GothwadScaffold(
        topBar = { SubScreenHeader(title = "Install help", onBack = { nav.popBackStack() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item {
                InfoBanner(
                    title = "How a Gothwad Store install works",
                    message = "Download → SHA-256 check → install method → PackageInstaller → " +
                        "InstallResultReceiver → library update.",
                )
            }
            item { SectionHeader(title = "Common errors") }
            item {
                SettingsGroup {
                    ErrorHelpRow(
                        "INSTALL_FAILED_USER_RESTRICTED",
                        "Android blocked the install. Open Install permission and allow Gothwad Store, then retry.",
                        onClick = { nav.navigate(Routes.INSTALL_PERMISSION) },
                    )
                    ErrorHelpRow(
                        "INSTALL_FAILED_UPDATE_INCOMPATIBLE",
                        "The installed copy was signed with a different key. Uninstall it first (your data will be lost), then install again.",
                    )
                    ErrorHelpRow(
                        "INSTALL_FAILED_VERSION_DOWNGRADE",
                        "The release is older than what you have. Enable “Allow downgrades” and use root or Shizuku.",
                        onClick = { nav.navigate(Routes.INSTALL_SETTINGS) },
                    )
                    ErrorHelpRow(
                        "INSTALL_PARSE_FAILED_NO_CERTIFICATES",
                        "The APK is corrupt or was not fully downloaded. Delete the download and fetch it again.",
                        onClick = { nav.navigate(Routes.DOWNLOADS) },
                    )
                    ErrorHelpRow(
                        "Checksum mismatch",
                        "The APK on disk does not match apk_sha256 from the Worker. The file is deleted automatically; retry the download.",
                    )
                    ErrorHelpRow(
                        "Shizuku not responding",
                        "Start the Shizuku service again (it dies when wireless debugging stops) and re-grant permission.",
                        onClick = { nav.navigate(Routes.INSTALL_SHIZUKU) },
                    )
                }
            }
            item { SectionHeader(title = "Current device") }
            item {
                Column {
                    KeyValueRow("Install permission", capabilities.installPermissionGranted.yesNo())
                    KeyValueRow("Shizuku", capabilities.healthSummary())
                    KeyValueRow("Preferred method", AppGraph.settings.current.installMethod.displayName())
                }
            }
            item {
                Column(Modifier.padding(Spacing.screenH)) {
                    Spacer(Modifier.height(8.dp))
                    OutlineButton(
                        label = "Run a health check",
                        onClick = {
                            AppGraph.installer.refreshCapabilities()
                            scope.launch {
                                AppGraph.installer.checkRoot()
                                vm.emit(StoreEvent.Message("Capabilities refreshed."))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorHelpRow(title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    SettingRow(title = title, subtitle = subtitle, showChevron = onClick != null, onClick = onClick)
}
