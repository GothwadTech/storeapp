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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.gothwad.store.BuildConfig
import com.gothwad.store.core.AppGraph
import com.gothwad.store.core.Format
import com.gothwad.store.data.auth.AuthState
import com.gothwad.store.ui.Routes
import com.gothwad.store.ui.StoreEvent
import com.gothwad.store.ui.StoreViewModel
import com.gothwad.store.ui.components.BannerTone
import com.gothwad.store.ui.components.BrandAvatar
import com.gothwad.store.ui.components.DownloadsAction
import com.gothwad.store.ui.components.GothwadScaffold
import com.gothwad.store.ui.components.GothwadTopBar
import com.gothwad.store.ui.components.InfoBanner
import com.gothwad.store.ui.components.KeyValueRow
import com.gothwad.store.ui.components.OutlineButton
import com.gothwad.store.ui.components.PillLabel
import com.gothwad.store.ui.components.PrimaryButton
import com.gothwad.store.ui.components.SectionHeader
import com.gothwad.store.ui.components.SettingRow
import com.gothwad.store.ui.components.SettingsGroup
import com.gothwad.store.ui.components.StatTile
import com.gothwad.store.ui.theme.GothwadTheme
import com.gothwad.store.ui.theme.Spacing

/** Gothwad Accounts profile + the whole app menu, Play-Store style. */
@Composable
fun AccountScreen(vm: StoreViewModel, nav: NavController) {
    val authState by AppGraph.auth.state.collectAsStateWithLifecycle()
    val libraryState by AppGraph.library.state.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val tasks by vm.downloads.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        AppGraph.auth.restoreSession()
        AppGraph.library.load()
    }

    val user = (authState as? AuthState.SignedIn)?.user

    // Profile is a bottom-tab destination, so it needs the shared Scaffold:
    // without it the content started under the status bar and the bottom bar
    // was missing entirely.
    GothwadScaffold(
        topBar = {
            GothwadTopBar(
                title = "Profile",
                showBrandMark = true,
                actions = {
                    DownloadsAction { nav.navigate(Routes.DOWNLOADS) }
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = GothwadTheme.extra.textSecondary,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item {
                ProfileCard(
                    name = user?.displayName ?: settings?.localDisplayName?.takeIf { it.isNotBlank() } ?: "Not signed in",
                    handle = user?.let { if (it.username.isNotBlank()) "@${it.username}" else it.email }
                        ?: "Sign in with Gothwad for cloud library and reviews",
                    picture = user?.picture,
                    signedIn = user != null,
                    onSignIn = { nav.navigate(Routes.SIGN_IN) },
                    onManage = { nav.navigate(Routes.ACCOUNT_DETAILS) },
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screenH, vertical = 6.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { nav.navigate(Routes.MY_APPS) }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatTile(
                        label = "Installed",
                        value = libraryState.installed.size.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Wishlist",
                        value = libraryState.wishlist.size.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Downloads",
                        value = tasks.count { it.isActiveRunning() }.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item { SectionHeader(title = "Your content") }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "My apps & device",
                        subtitle = "Updates, library and everything installed",
                        icon = Icons.Filled.SystemUpdate,
                        onClick = { nav.navigate(Routes.MY_APPS) },
                    )
                    SettingRow(
                        title = "Wishlist",
                        subtitle = "${libraryState.wishlist.size} saved app(s)",
                        icon = Icons.Filled.BookmarkBorder,
                        onClick = { nav.navigate(Routes.WISHLIST) },
                    )
                    SettingRow(
                        title = "Manage downloads",
                        subtitle = "${tasks.size} job(s) · ${Format.bytes(AppGraph.downloader.usedBytes())} on disk",
                        icon = Icons.Filled.Download,
                        onClick = { nav.navigate(Routes.DOWNLOADS) },
                    )
                    SettingRow(
                        title = "Download history",
                        subtitle = "Finished, cancelled and failed APKs",
                        icon = Icons.Filled.Storage,
                        onClick = { nav.navigate(Routes.DOWNLOAD_HISTORY) },
                    )
                    SettingRow(
                        title = "On this device",
                        subtitle = "Every package Android reports",
                        icon = Icons.Filled.PhoneAndroid,
                        onClick = { nav.navigate(Routes.ON_DEVICE) },
                    )
                }
            }

            item { SectionHeader(title = "Gothwad") }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "Gothwad Accounts",
                        subtitle = "Profile, password and connected devices",
                        icon = Icons.Filled.ManageAccounts,
                        onClick = { nav.navigate(Routes.ACCOUNT_DETAILS) },
                    )
                    SettingRow(
                        title = "Developer Console",
                        subtitle = "Publish and manage your own apps at console.gothwadtech.com",
                        icon = Icons.Filled.Dashboard,
                        onClick = { nav.navigate(Routes.DEVELOPER_CONSOLE) },
                        trailingText = "Open",
                    )
                    SettingRow(
                        title = "Sign-in security",
                        subtitle = "Sessions, tokens and revoking access",
                        icon = Icons.Filled.Lock,
                        onClick = { nav.navigate(Routes.ACCOUNT_SECURITY) },
                    )
                }
            }

            item { SectionHeader(title = "Settings") }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "General",
                        subtitle = "Theme, layout and charts",
                        icon = Icons.Filled.Settings,
                        onClick = { nav.navigate(Routes.SETTINGS) },
                    )
                    SettingRow(
                        title = "Appearance",
                        subtitle = "Dark mode, theme mode and list density",
                        icon = Icons.Filled.Palette,
                        onClick = { nav.navigate(Routes.APPEARANCE) },
                    )
                    SettingRow(
                        title = "Notifications",
                        subtitle = "Download, install and update alerts",
                        icon = Icons.Filled.Notifications,
                        onClick = { nav.navigate(Routes.NOTIFICATIONS) },
                    )
                    SettingRow(
                        title = "Network & data",
                        subtitle = "Wi-Fi only, data saver and retries",
                        icon = Icons.Filled.Wifi,
                        onClick = { nav.navigate(Routes.NETWORK) },
                    )
                    SettingRow(
                        title = "Download preferences",
                        subtitle = "Checksums, cleanup and parallel downloads",
                        icon = Icons.Filled.Tune,
                        onClick = { nav.navigate(Routes.DOWNLOAD_SETTINGS) },
                    )
                    SettingRow(
                        title = "Installer",
                        subtitle = "Install permission, Shizuku, root and ADB",
                        icon = Icons.Filled.Security,
                        onClick = { nav.navigate(Routes.INSTALL_SETTINGS) },
                    )
                    SettingRow(
                        title = "Storage",
                        subtitle = "APKs kept and cleanup tools",
                        icon = Icons.Filled.Storage,
                        onClick = { nav.navigate(Routes.STORAGE) },
                    )
                    SettingRow(
                        title = "Privacy",
                        subtitle = "What Kiwi Store collects and stores",
                        icon = Icons.Filled.PrivacyTip,
                        onClick = { nav.navigate(Routes.PRIVACY) },
                    )
                    SettingRow(
                        title = "Advanced",
                        subtitle = "Cache, API host and diagnostics",
                        icon = Icons.Filled.Tune,
                        onClick = { nav.navigate(Routes.ADVANCED) },
                    )
                    SettingRow(
                        title = "About this device",
                        subtitle = "Android version, ABI and app id",
                        icon = Icons.Filled.PhoneAndroid,
                        onClick = { nav.navigate(Routes.DEVICE_INFO) },
                    )
                }
            }

            item { SectionHeader(title = "Support") }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "Help & troubleshoot",
                        subtitle = "Installs, downloads and account problems",
                        icon = Icons.Filled.HelpOutline,
                        onClick = { nav.navigate(Routes.HELP) },
                    )
                    SettingRow(
                        title = "Send feedback",
                        subtitle = "Tell the Gothwad team what to fix",
                        icon = Icons.Filled.Feedback,
                        onClick = { nav.navigate(Routes.FEEDBACK) },
                    )
                    SettingRow(
                        title = "About Kiwi Store",
                        subtitle = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        icon = Icons.Filled.Info,
                        onClick = { nav.navigate(Routes.ABOUT) },
                    )
                    SettingRow(
                        title = "Terms & licences",
                        subtitle = "Legal notices and open-source licences",
                        icon = Icons.Filled.AccountCircle,
                        onClick = { nav.navigate(Routes.LEGAL) },
                    )
                }
            }

            if (user != null) {
                item {
                    Column(Modifier.padding(Spacing.screenH)) {
                        Spacer(Modifier.height(8.dp))
                        OutlineButton(
                            label = "Sign out of this device",
                            icon = Icons.Filled.Logout,
                            onClick = {
                                scope.launch {
                                    AppGraph.auth.signOut()
                                    vm.emit(StoreEvent.Message("Signed out. Your local library stays on this device."))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Kiwi Store ${BuildConfig.VERSION_NAME} · signed in as ${user.username.ifBlank { user.id }}",
                            fontSize = 11.sp,
                            color = GothwadTheme.extra.textTertiary,
                        )
                    }
                }
            } else {
                item {
                    Column(Modifier.padding(Spacing.screenH)) {
                        Spacer(Modifier.height(8.dp))
                        OutlineButton(
                            label = "Not signed in — tap to sign in",
                            onClick = { nav.navigate(Routes.SIGN_IN) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private fun com.gothwad.store.data.download.DownloadTask.isActiveRunning(): Boolean =
    status == com.gothwad.store.data.download.DownloadStatus.RUNNING ||
        status == com.gothwad.store.data.download.DownloadStatus.QUEUED

@Composable
private fun ProfileCard(
    name: String,
    handle: String,
    picture: String?,
    signedIn: Boolean,
    onSignIn: () -> Unit,
    onManage: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.screenH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (picture != null) {
                AsyncImage(
                    model = picture,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                BrandAvatar(text = name, size = 62.dp)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = GothwadTheme.extra.textPrimary)
            Text(handle, fontSize = 12.sp, color = GothwadTheme.extra.textSecondary, lineHeight = 17.sp)
            Spacer(Modifier.height(6.dp))
            if (signedIn) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillLabel(
                        "Gothwad account",
                        GothwadTheme.extra.chipBackground,
                        GothwadTheme.extra.textSecondary,
                    )
                    com.gothwad.store.ui.components.OutlineButton(
                        label = "Manage",
                        onClick = onManage,
                    )
                }
            } else {
                PrimaryButton("Sign in with Gothwad", onClick = onSignIn)
            }
        }
    }
}

@Composable
fun SignInScreen(vm: StoreViewModel, nav: NavController) {
    val authState by AppGraph.auth.state.collectAsStateWithLifecycle()
    val lastError by AppGraph.auth.lastError.collectAsStateWithLifecycle()
    var busy by rememberSaveable { mutableStateOf(false) }

    GothwadScaffoldSimple(title = "Sign in", onBack = { nav.popBackStack() }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.screenH,
                end = Spacing.screenH,
                top = 16.dp,
                bottom = Spacing.bottomContentPadding,
            ),
        ) {
            item {
                Text(
                    "One Gothwad account",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = GothwadTheme.extra.textPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Signing in syncs your library, wishlist and reviews through the Gothwad Worker " +
                        "and lets you install on every device you own.",
                    fontSize = 13.sp,
                    color = GothwadTheme.extra.textSecondary,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(18.dp))
                when (val state = authState) {
                    AuthState.Loading -> Text("Checking your session…", fontSize = 13.sp)
                    is AuthState.SignedIn -> {
                        InfoBanner(
                            title = "Signed in as ${state.user.displayName}",
                            message = "You are already signed in${if (state.developer != null) " as a developer" else ""}.",
                        )
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton(
                            label = "Continue",
                            onClick = { nav.popBackStack() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    AuthState.SignedOut -> {
                        PrimaryButton(
                            label = if (busy) "Opening Gothwad Accounts…" else "Continue with Gothwad",
                            enabled = !busy,
                            onClick = {
                                busy = true
                                AppGraph.auth.startSignIn()
                                    .onFailure {
                                        vm.emit(StoreEvent.Message(it.message ?: "Could not open Gothwad Accounts."))
                                        busy = false
                                    }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlineButton(
                            label = "Use the app without an account",
                            onClick = { nav.popBackStack() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(16.dp))
                        if (lastError != null) {
                            InfoBanner(
                                title = "Gothwad Accounts said",
                                message = lastError.orEmpty(),
                                tone = BannerTone.Error,
                            )
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader(title = "How the flow works")
                SettingsGroup {
                    com.gothwad.store.ui.components.IconTextRow(
                        Icons.Filled.Lock,
                        "Kiwi Store opens the Accounts consent page in a Custom Tab with PKCE " +
                            "(no client secret is stored in the APK).",
                    )
                    com.gothwad.store.ui.components.IconTextRow(
                        Icons.Filled.AccountCircle,
                        "After you approve, Android returns to gothwadstore://oauth/callback and the app " +
                            "exchanges the one-time code for tokens.",
                    )
                    com.gothwad.store.ui.components.IconTextRow(
                        Icons.Filled.PrivacyTip,
                        "Access tokens last an hour; refresh tokens rotate and can be revoked at any time.",
                    )
                }
            }
            item {
                Spacer(Modifier.height(14.dp))
                KeyValueRow("OAuth client", com.gothwad.store.BuildConfig.OAUTH_CLIENT_ID)
                KeyValueRow("Redirect URI", BuildConfig.OAUTH_REDIRECT_URI)
                KeyValueRow("Accounts API", BuildConfig.ACCOUNTS_API_BASE_URL)
            }
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    "No account? Create one at accounts.gothwadtech.com — it is the same login used by " +
                        "the Gothwad site, console and browser.",
                    fontSize = 12.sp,
                    color = GothwadTheme.extra.textTertiary,
                )
            }
        }
    }
}

@Composable
fun AccountDetailsScreen(vm: StoreViewModel, nav: NavController) {
    val authState by AppGraph.auth.state.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var localName by rememberSaveable { mutableStateOf(settings?.localDisplayName.orEmpty()) }
    var localEmail by rememberSaveable { mutableStateOf(settings?.localEmail.orEmpty()) }
    val signedIn = authState as? AuthState.SignedIn

    LaunchedEffect(Unit) { AppGraph.auth.fetchMe(silent = true) }

    GothwadScaffoldSimple(title = "Gothwad Accounts", onBack = { nav.popBackStack() }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            if (signedIn == null) {
                item {
                    InfoBanner(
                        title = "Not signed in",
                        message = "Kiwi Store keeps your library on this device only until you sign in.",
                        tone = BannerTone.Warning,
                        action = { PrimaryButton("Sign in", onClick = { nav.navigate(Routes.SIGN_IN) }) },
                    )
                }
                item { SectionHeader(title = "Offline profile") }
                item {
                    SettingsGroup {
                        SettingRow(title = "Display name", subtitle = settings?.localDisplayName?.ifBlank { "Not set" } ?: "Not set")
                        SettingRow(title = "Contact e-mail", subtitle = settings?.localEmail?.ifBlank { "Not set" } ?: "Not set")
                    }
                }
                item {
                    Column(Modifier.padding(Spacing.screenH)) {
                        OutlinedField(localName, "Display name shown to reviewers") { localName = it }
                        Spacer(Modifier.height(10.dp))
                        OutlinedField(localEmail, "Contact e-mail") { localEmail = it }
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton(
                            label = "Save offline profile",
                            onClick = {
                                scope.launch {
                                    AppGraph.settings.setLocalProfile(localName.trim(), localEmail.trim())
                                    vm.emit(StoreEvent.Message("Offline profile saved."))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.screenH),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BrandAvatar(text = signedIn.user.displayName, size = 56.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                signedIn.user.displayName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                signedIn.user.email.ifBlank { signedIn.user.username },
                                fontSize = 12.sp,
                                color = GothwadTheme.extra.textSecondary,
                            )
                        }
                    }
                }
                item { SectionHeader(title = "Account") }
                item {
                    Column {
                        KeyValueRow("Subject (sub)", signedIn.user.id)
                        KeyValueRow("Username", signedIn.user.username.ifBlank { "—" })
                        KeyValueRow("Display name", signedIn.user.name.ifBlank { "—" })
                        KeyValueRow("E-mail", signedIn.user.email.ifBlank { "—" })
                        KeyValueRow("Developer profile", signedIn.developer?.displayName ?: "Not enrolled")
                        KeyValueRow("Admin", if (signedIn.isAdmin) "Yes" else "No")
                    }
                }
                item { SectionHeader(title = "Account actions") }
                item {
                    SettingsGroup {
                        SettingRow(
                            title = "Refresh profile",
                            subtitle = "Re-read /api/me and /oauth/userinfo",
                            onClick = { scope.launch { AppGraph.auth.fetchMe(silent = false) } },
                        )
                        SettingRow(
                            title = "Sign-in security",
                            subtitle = "Tokens, expiry and revoking access",
                            onClick = { nav.navigate(Routes.ACCOUNT_SECURITY) },
                        )
                        SettingRow(
                            title = "Developer Console",
                            subtitle = "console.gothwadtech.com",
                            onClick = { nav.navigate(Routes.DEVELOPER_CONSOLE) },
                        )
                        SettingRow(
                            title = "Sign out",
                            subtitle = "Clears the tokens stored on this device",
                            onClick = { scope.launch { AppGraph.auth.signOut() } },
                        )
                    }
                }
                item {
                    Column(Modifier.padding(Spacing.screenH)) {
                        InfoBanner(
                            title = "Managed by Gothwad Accounts",
                            message = "Changing your password, e-mail or display name happens on " +
                                "accounts.gothwadtech.com — this app only holds the tokens.",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OutlinedField(value: String, label: String, onChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 80) onChange(it) },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun AccountSecurityScreen(vm: StoreViewModel, nav: NavController) {
    val authState by AppGraph.auth.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    GothwadScaffoldSimple(title = "Sign-in security", onBack = { nav.popBackStack() }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item {
                InfoBanner(
                    title = "Tokens on this device",
                    message = "The access token (1 hour) and the rotating refresh token (30 days) are stored in " +
                        "private app storage. Signing out deletes both.",
                )
            }
            item { SectionHeader(title = "Current session") }
            item {
                Column {
                    val state = authState
                    KeyValueRow("Status", if (state is AuthState.SignedIn) "Signed in" else "Signed out")
                    if (state is AuthState.SignedIn) {
                        KeyValueRow("Account", state.user.username.ifBlank { state.user.id })
                        KeyValueRow("Scopes", "profile email offline_access")
                        KeyValueRow("Storage", "SharedPreferences · gothwad_accounts")
                    }
                }
            }
            item { SectionHeader(title = "Good practice") }
            item {
                SettingsGroup {
                    com.gothwad.store.ui.components.IconTextRow(Icons.Filled.Lock, "Use a password manager; Kiwi Store never sees your password.")
                    com.gothwad.store.ui.components.IconTextRow(Icons.Filled.ManageAccounts, "Revoke access for devices you no longer use from accounts.gothwadtech.com.")
                    com.gothwad.store.ui.components.IconTextRow(Icons.Filled.PrivacyTip, "Public Wi-Fi cannot read the tokens — everything goes over TLS and PKCE keeps the code single-use.")
                }
            }
            item {
                Column(Modifier.padding(Spacing.screenH)) {
                    Spacer(Modifier.height(8.dp))
                    OutlineButton(
                        label = "Refresh session now",
                        onClick = { scope.launch { AppGraph.auth.fetchMe(silent = false) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlineButton(
                        label = "Sign out everywhere on this device",
                        onClick = { confirmSignOut = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Revoking server-side sessions is done on the Accounts website; this button only clears " +
                            "the copy stored inside the app.",
                        fontSize = 12.sp,
                        color = GothwadTheme.extra.textTertiary,
                    )
                }
            }
        }
    }

    if (confirmSignOut) {
        com.gothwad.store.ui.components.ConfirmDialog(
            title = "Sign out?",
            message = "The stored tokens are deleted. Downloads already on the device are kept.",
            confirmLabel = "Sign out",
            onConfirm = {
                confirmSignOut = false
                scope.launch {
                    AppGraph.auth.signOut()
                    vm.emit(StoreEvent.Message("Signed out."))
                }
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}

@Composable
private fun GothwadScaffoldSimple(
    title: String,
    onBack: () -> Unit,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    com.gothwad.store.ui.components.GothwadScaffold(
        topBar = { com.gothwad.store.ui.components.SubScreenHeader(title = title, onBack = onBack) },
        content = content,
    )
}
