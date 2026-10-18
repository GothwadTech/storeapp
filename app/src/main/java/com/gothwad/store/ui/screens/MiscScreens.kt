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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.gothwad.store.ui.Routes
import com.gothwad.store.ui.StoreEvent
import com.gothwad.store.ui.StoreViewModel
import com.gothwad.store.ui.components.BannerTone
import com.gothwad.store.ui.components.BrandAvatar
import com.gothwad.store.ui.components.GothwadScaffold
import com.gothwad.store.ui.components.IconTextRow
import com.gothwad.store.ui.components.InfoBanner
import com.gothwad.store.ui.components.KeyValueRow
import com.gothwad.store.ui.components.OutlineButton
import com.gothwad.store.ui.components.PrimaryButton
import com.gothwad.store.ui.components.SectionHeader
import com.gothwad.store.ui.components.SettingRow
import com.gothwad.store.ui.components.SettingsGroup
import com.gothwad.store.ui.components.SubScreenHeader
import com.gothwad.store.ui.theme.GothwadTheme
import com.gothwad.store.ui.theme.Spacing

private fun openUrl(context: android.content.Context, url: String, onFail: (String) -> Unit) {
    val ok = runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
    if (!ok) onFail("No browser is available to open $url")
}

/** console.gothwadtech.com bridge — publishers manage releases there. */
@Composable
fun DeveloperConsoleScreen(vm: StoreViewModel, nav: NavController) {
    val context = LocalContext.current
    val authState by AppGraph.auth.state.collectAsStateWithLifecycle()
    val signedIn = authState as? com.gothwad.store.data.auth.AuthState.SignedIn
    val scope = rememberCoroutineScope()

    GothwadScaffold(
        topBar = { SubScreenHeader(title = "Developer Console", onBack = { nav.popBackStack() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item {
                Column(Modifier.padding(Spacing.screenH)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        BrandAvatar(text = "GC", size = 46.dp)
                        Column {
                            Text("console.gothwadtech.com", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Upload APKs, write listing copy and follow the review queue",
                                fontSize = 12.sp,
                                color = GothwadTheme.extra.textSecondary,
                            )
                        }
                    }
                }
            }
            item {
                InfoBanner(
                    title = if (signedIn != null) "Signed in as ${signedIn.user.displayName}" else "Sign in with the same account",
                    message = if (signedIn != null) {
                        "The console uses the same Gothwad Accounts session you just created here."
                    } else {
                        "The console is a web app: it signs you in through accounts.gothwadtech.com with the " +
                            "same account this app uses."
                    },
                    tone = if (signedIn != null) BannerTone.Info else BannerTone.Warning,
                )
            }
            item { SectionHeader(title = "Open") }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "Developer Console",
                        subtitle = BuildConfig.CONSOLE_URL,
                        icon = Icons.Filled.Dashboard,
                        onClick = {
                            openUrl(context, BuildConfig.CONSOLE_URL) { vm.emit(StoreEvent.Message(it)) }
                        },
                    )
                    SettingRow(
                        title = "My published apps",
                        subtitle = "Filtered to your developer account",
                        icon = Icons.Filled.Storefront,
                        onClick = {
                            openUrl(context, "${BuildConfig.CONSOLE_URL}apps") { vm.emit(StoreEvent.Message(it)) }
                        },
                    )
                    SettingRow(
                        title = "Release review queue",
                        subtitle = "See what reviewers are looking at",
                        icon = Icons.Filled.Security,
                        onClick = {
                            openUrl(context, "${BuildConfig.CONSOLE_URL}releases") { vm.emit(StoreEvent.Message(it)) }
                        },
                    )
                    SettingRow(
                        title = "Upload requirements",
                        subtitle = "GitHub Releases (≤25 MB) or Backblaze B2 for bigger APKs",
                        icon = Icons.Filled.HelpOutline,
                        onClick = {
                            openUrl(context, "${BuildConfig.CONSOLE_URL}docs/upload") { vm.emit(StoreEvent.Message(it)) }
                        },
                    )
                    SettingRow(
                        title = "Store web page",
                        subtitle = BuildConfig.STORE_WEB_URL,
                        icon = Icons.Filled.OpenInNew,
                        onClick = {
                            openUrl(context, BuildConfig.STORE_WEB_URL) { vm.emit(StoreEvent.Message(it)) }
                        },
                    )
                }
            }
            item { SectionHeader(title = "Before you publish") }
            item {
                SettingsGroup {
                    IconTextRow(
                        Icons.Filled.Security,
                        "Every release needs a signed APK. The Worker reads minSdk, targetSdk and permissions " +
                            "straight from the uploaded file.",
                    )
                    IconTextRow(
                        Icons.Filled.Storefront,
                        "Apps go draft → in review → published. Only published releases reach the Android client.",
                    )
                    IconTextRow(
                        Icons.Filled.BugReport,
                        "Uploads over 25 MB are stored in Backblaze B2, everything smaller on GitHub Releases.",
                    )
                    IconTextRow(
                        Icons.Filled.PrivacyTip,
                        "Keep the package name stable — Android treats a new package as a different app.",
                    )
                }
            }
            item {
                Column(Modifier.padding(Spacing.screenH)) {
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        label = "Open Developer Console",
                        onClick = { openUrl(context, BuildConfig.CONSOLE_URL) { vm.emit(StoreEvent.Message(it)) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Opens in your browser. Kiwi Store does not embed the console.",
                        fontSize = 12.sp,
                        color = GothwadTheme.extra.textTertiary,
                    )
                }
            }
        }
    }
}

@Composable
fun AboutScreen(vm: StoreViewModel, nav: NavController) {
    val context = LocalContext.current

    GothwadScaffold(
        topBar = { SubScreenHeader(title = "About Kiwi Store", onBack = { nav.popBackStack() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item {
                Column(Modifier.padding(Spacing.screenH)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        BrandAvatar(text = "KS", size = 54.dp)
                        Column {
                            Text("Kiwi Store", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                fontSize = 12.sp,
                                color = GothwadTheme.extra.textSecondary,
                            )
                            Text(
                                "Managed by Gothwad Tech & Dark Root",
                                fontSize = 12.sp,
                                color = GothwadTheme.extra.textTertiary,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    InfoBanner(
                        title = "Live data only",
                        message = "Listings, releases and reviews come from the Gothwad Worker " +
                            "(store.gothwadtech.com/api). There is no bundled demo catalogue.",
                    )
                }
            }
            item { SectionHeader(title = "Build") }
            item {
                Column {
                    KeyValueRow("Application id", BuildConfig.APPLICATION_ID)
                    KeyValueRow("Store API", BuildConfig.STORE_API_BASE_URL)
                    KeyValueRow("Accounts API", BuildConfig.ACCOUNTS_API_BASE_URL)
                    KeyValueRow("Console", BuildConfig.CONSOLE_URL)
                    KeyValueRow("OAuth redirect", BuildConfig.OAUTH_REDIRECT_URI)
                }
            }
            item { SectionHeader(title = "Links") }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "Store on the web",
                        subtitle = BuildConfig.STORE_WEB_URL,
                        icon = Icons.Filled.Storefront,
                        onClick = { openUrl(context, BuildConfig.STORE_WEB_URL) { vm.emit(StoreEvent.Message(it)) } },
                    )
                    SettingRow(
                        title = "Terms of service",
                        subtitle = "store.gothwadtech.com/terms",
                        onClick = { openUrl(context, "${BuildConfig.STORE_WEB_URL}terms") { vm.emit(StoreEvent.Message(it)) } },
                    )
                    SettingRow(
                        title = "Privacy policy",
                        subtitle = "store.gothwadtech.com/privacy",
                        icon = Icons.Filled.PrivacyTip,
                        onClick = { openUrl(context, "${BuildConfig.STORE_WEB_URL}privacy") { vm.emit(StoreEvent.Message(it)) } },
                    )
                    SettingRow(
                        title = "Open-source licences",
                        subtitle = "AndroidX, OkHttp, Retrofit, Moshi, Coil, Shizuku API",
                        onClick = { nav.navigate(Routes.LICENSES) },
                    )
                    SettingRow(
                        title = "Send feedback",
                        subtitle = "Reports go to the store team",
                        icon = Icons.Filled.Email,
                        onClick = { nav.navigate(Routes.FEEDBACK) },
                    )
                }
            }
            item {
                Column(Modifier.padding(Spacing.screenH)) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Managed by Gothwad Tech & Dark Root. Not affiliated with Google; Google Play is a trademark of " +
                            "Google LLC.",
                        fontSize = 11.sp,
                        color = GothwadTheme.extra.textTertiary,
                    )
                }
            }
        }
    }
}

@Composable
fun LegalScreen(vm: StoreViewModel, nav: NavController) {
    val context = LocalContext.current

    GothwadScaffold(
        topBar = { SubScreenHeader(title = "Legal", onBack = { nav.popBackStack() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item {
                SettingsGroup(title = "Documents") {
                    SettingRow(
                        title = "Terms of service",
                        subtitle = "Rules for using the store",
                        onClick = { openUrl(context, "${BuildConfig.STORE_WEB_URL}terms") { vm.emit(StoreEvent.Message(it)) } },
                    )
                    SettingRow(
                        title = "Privacy policy",
                        subtitle = "What the Worker stores and why",
                        onClick = { openUrl(context, "${BuildConfig.STORE_WEB_URL}privacy") { vm.emit(StoreEvent.Message(it)) } },
                    )
                    SettingRow(
                        title = "Developer agreement",
                        subtitle = "Publishing rules for developers",
                        onClick = { openUrl(context, "${BuildConfig.STORE_WEB_URL}developers") { vm.emit(StoreEvent.Message(it)) } },
                    )
                    SettingRow(
                        title = "Open-source licences",
                        subtitle = "Every third-party library in this APK",
                        onClick = { nav.navigate(Routes.LICENSES) },
                    )
                }
            }
            item {
                Column(Modifier.padding(Spacing.screenH)) {
                    InfoBanner(
                        title = "Reviews and reports",
                        message = "Reviews you post and reports you file are stored against your Gothwad " +
                            "account id. Deleting a review removes it from the public listing immediately.",
                    )
                }
            }
        }
    }
}

@Composable
fun LicensesScreen(vm: StoreViewModel, nav: NavController) {
    GothwadScaffold(
        topBar = { SubScreenHeader(title = "Licences", onBack = { nav.popBackStack() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item {
                InfoBanner(
                    title = "Apache 2.0 and friends",
                    message = "Kiwi Store builds on the Android Open Source Project and the libraries below. " +
                        "Full texts ship with the APK on the store page.",
                )
            }
            item {
                SettingsGroup(title = "Libraries") {
                    LICENSE_ENTRIES.forEach { (name, licence) ->
                        SettingRow(title = name, subtitle = licence, showChevron = false)
                    }
                }
            }
        }
    }
}

private val LICENSE_ENTRIES: List<Pair<String, String>> = listOf(
    "AndroidX / Jetpack Compose" to "Apache License 2.0",
    "Material 3 components" to "Apache License 2.0",
    "Kotlin & kotlinx.coroutines" to "Apache License 2.0",
    "OkHttp & Retrofit" to "Apache License 2.0",
    "Moshi" to "Apache License 2.0",
    "Coil image loader" to "Apache License 2.0",
    "AndroidX DataStore" to "Apache License 2.0",
    "AndroidX Browser (Custom Tabs)" to "Apache License 2.0",
    "Shizuku API" to "Apache License 2.0",
    "Material Icons (extended)" to "Apache License 2.0",
)

@Composable
fun HelpScreen(vm: StoreViewModel, nav: NavController) {
    val capabilities by AppGraph.installer.capabilities.collectAsStateWithLifecycle()
    val context = LocalContext.current

    GothwadScaffold(
        topBar = { SubScreenHeader(title = "Help", onBack = { nav.popBackStack() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item {
                InfoBanner(
                    title = "Start here",
                    message = capabilities.healthSummary(),
                    tone = if (capabilities.silentMethod != null || capabilities.installPermissionGranted) {
                        BannerTone.Info
                    } else {
                        BannerTone.Warning
                    },
                )
            }
            item { SectionHeader(title = "Installing") }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "Installs fail with “blocked”",
                        subtitle = "Allow Kiwi Store to install unknown apps",
                        onClick = { nav.navigate(Routes.INSTALL_PERMISSION) },
                    )
                    SettingRow(
                        title = "Silent installs",
                        subtitle = "Shizuku (no root) or root",
                        onClick = { nav.navigate(Routes.INSTALL_SHIZUKU) },
                    )
                    SettingRow(
                        title = "Every install error explained",
                        subtitle = "USER_RESTRICTED, UPDATE_INCOMPATIBLE, downgrades…",
                        onClick = { nav.navigate(Routes.INSTALL_HELP) },
                    )
                    SettingRow(
                        title = "Install from a computer",
                        subtitle = "ADB and wireless debugging commands",
                        onClick = { nav.navigate(Routes.INSTALL_ADB) },
                    )
                }
            }
            item { SectionHeader(title = "Downloads") }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "A download is stuck",
                        subtitle = "Pause and resume, or clear and retry",
                        onClick = { nav.navigate(Routes.DOWNLOADS) },
                    )
                    SettingRow(
                        title = "Checksum mismatch",
                        subtitle = "Why the APK gets deleted and how to retry safely",
                        onClick = { nav.navigate(Routes.INSTALL_HELP) },
                    )
                    SettingRow(
                        title = "Free up space",
                        subtitle = "Prune APKs and orphan files",
                        onClick = { nav.navigate(Routes.STORAGE) },
                    )
                }
            }
            item { SectionHeader(title = "Account") }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "Sign-in problems",
                        subtitle = "PKCE, redirect URI and token refresh",
                        onClick = { nav.navigate(Routes.SIGN_IN) },
                    )
                    SettingRow(
                        title = "Library not syncing",
                        subtitle = "Check the session and re-sync",
                        onClick = { nav.navigate(Routes.ACCOUNT_DETAILS) },
                    )
                }
            }
            item { SectionHeader(title = "Still stuck?") }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "Open the store help pages",
                        subtitle = "${BuildConfig.STORE_WEB_URL}help",
                        icon = Icons.Filled.HelpOutline,
                        onClick = { openUrl(context, "${BuildConfig.STORE_WEB_URL}help") { vm.emit(StoreEvent.Message(it)) } },
                    )
                    SettingRow(
                        title = "Report a bug in this app",
                        subtitle = "Send the diagnostics from Advanced",
                        icon = Icons.Filled.BugReport,
                        onClick = { nav.navigate(Routes.FEEDBACK) },
                    )
                }
            }
        }
    }
}

@Composable
fun FeedbackScreen(vm: StoreViewModel, nav: NavController) {
    val context = LocalContext.current
    val capabilities by AppGraph.installer.capabilities.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var message by rememberSaveable { mutableStateOf("") }
    var includeDiagnostics by rememberSaveable { mutableStateOf(true) }
    var sent by rememberSaveable { mutableStateOf(false) }

    val diagnostics = buildString {
        appendLine("app=Kiwi Store ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("android=${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        appendLine("device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("abi=${android.os.Build.SUPPORTED_ABIS.joinToString(",")}")
        appendLine("installPermission=${capabilities.installPermissionGranted}")
        appendLine("shizuku=${capabilities.shizukuInstalled}/${capabilities.shizukuRunning}/${capabilities.shizukuPermissionGranted}")
        appendLine("root=${capabilities.rootGranted}")
        append("api=${BuildConfig.STORE_API_BASE_URL}")
    }

    GothwadScaffold(
        topBar = { SubScreenHeader(title = "Send feedback", onBack = { nav.popBackStack() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.screenH, 12.dp, Spacing.screenH, Spacing.bottomContentPadding),
        ) {
            item {
                OutlinedTextField(
                    value = message,
                    onValueChange = { if (it.length <= 1200) message = it },
                    label = { Text("What should the Gothwad team fix?") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text("${message.length}/1200", fontSize = 11.sp, color = GothwadTheme.extra.textTertiary)
            }
            item {
                Spacer(Modifier.height(6.dp))
                com.gothwad.store.ui.components.CheckRow(
                    title = "Attach device diagnostics",
                    subtitle = "Android version, ABI, installer capability snapshot",
                    checked = includeDiagnostics,
                    onClick = { includeDiagnostics = !includeDiagnostics },
                )
            }
            if (includeDiagnostics) {
                item {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text(
                            diagnostics,
                            fontSize = 11.sp,
                            color = GothwadTheme.extra.textSecondary,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    label = "Send by e-mail",
                    enabled = message.isNotBlank(),
                    onClick = {
                        val body = if (includeDiagnostics) "$message\n\n---\n$diagnostics" else message
                        val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                            data = android.net.Uri.parse("mailto:support@gothwadtech.com")
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Kiwi Store feedback (${BuildConfig.VERSION_NAME})")
                            putExtra(android.content.Intent.EXTRA_TEXT, body)
                        }
                        val ok = runCatching {
                            context.startActivity(android.content.Intent.createChooser(intent, "Send feedback"))
                        }.isSuccess
                        if (ok) {
                            sent = true
                        } else {
                            vm.emit(StoreEvent.Message("No e-mail app is installed. Copy the text instead."))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlineButton(
                    label = "Copy the report",
                    enabled = message.isNotBlank(),
                    onClick = {
                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(
                            android.content.ClipData.newPlainText(
                                "Kiwi Store feedback",
                                if (includeDiagnostics) "$message\n\n---\n$diagnostics" else message,
                            ),
                        )
                        vm.emit(StoreEvent.Message("Report copied to the clipboard."))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (sent) {
                    Spacer(Modifier.height(12.dp))
                    InfoBanner(title = "Thanks!", message = "Your report went to support@gothwadtech.com.")
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Feedback is plain e-mail — the app never uploads anything on its own.",
                    fontSize = 12.sp,
                    color = GothwadTheme.extra.textTertiary,
                )
            }
        }
    }
}

@Composable
fun WelcomeScreen(vm: StoreViewModel, nav: NavController) {
    val scope = rememberCoroutineScope()

    GothwadScaffold(showBottomBar = false) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.screenH, 24.dp, Spacing.screenH, Spacing.bottomContentPadding),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BrandAvatar(text = "GS", size = 64.dp)
                    Text(
                        "Welcome to Kiwi Store",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = GothwadTheme.extra.textPrimary,
                    )
                    Text(
                        "A Play-style storefront for Gothwad apps: real releases, verified downloads and an " +
                            "installer that works on stock Android, with Shizuku or with root.",
                        fontSize = 14.sp,
                        color = GothwadTheme.extra.textSecondary,
                        lineHeight = 20.sp,
                    )
                }
            }
            item {
                Spacer(Modifier.height(22.dp))
                SettingsGroup(title = "Three things to set up") {
                    IconTextRow(Icons.Filled.Security, "Allow Kiwi Store to install unknown apps (or connect Shizuku).")
                    IconTextRow(Icons.Filled.Email, "Sign in with Gothwad so your library follows your account.")
                    IconTextRow(Icons.Filled.Storefront, "Pick an installer method — session, Shizuku, root or ADB.")
                }
            }
            item {
                Spacer(Modifier.height(20.dp))
                PrimaryButton(
                    label = "Set up now",
                    onClick = { nav.navigate(Routes.INSTALL_SETTINGS) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlineButton(
                    label = "Start browsing",
                    onClick = {
                        scope.launch { AppGraph.settings.setOnboardingDone(true) }
                        nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlineButton(
                    label = "Sign in with Gothwad",
                    onClick = { nav.navigate(Routes.SIGN_IN) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "You can change all of this later in Settings.",
                    fontSize = 12.sp,
                    color = GothwadTheme.extra.textTertiary,
                )
            }
        }
    }
}
