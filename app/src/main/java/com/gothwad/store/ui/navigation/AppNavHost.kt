package com.gothwad.store.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.gothwad.store.ui.Routes
import com.gothwad.store.ui.StoreViewModel
import com.gothwad.store.ui.components.LocalAppSnackbarHost
import com.gothwad.store.ui.components.LocalTabChrome
import com.gothwad.store.ui.components.TabChrome
import com.gothwad.store.ui.screens.AboutScreen
import com.gothwad.store.ui.screens.AccountDetailsScreen
import com.gothwad.store.ui.screens.AccountScreen
import com.gothwad.store.ui.screens.AccountSecurityScreen
import com.gothwad.store.ui.screens.AdbScreen
import com.gothwad.store.ui.screens.AdvancedScreen
import com.gothwad.store.ui.screens.AppDetailScreen
import com.gothwad.store.ui.screens.AppInfoScreen
import com.gothwad.store.ui.screens.AppPermissionsScreen
import com.gothwad.store.ui.screens.AppearanceScreen
import com.gothwad.store.ui.screens.AppsScreen
import com.gothwad.store.ui.screens.CategoriesScreen
import com.gothwad.store.ui.screens.CategoryDetailScreen
import com.gothwad.store.ui.screens.DeveloperConsoleScreen
import com.gothwad.store.ui.screens.DeviceInfoScreen
import com.gothwad.store.ui.screens.DownloadHistoryScreen
import com.gothwad.store.ui.screens.DownloadSettingsScreen
import com.gothwad.store.ui.screens.DownloadsScreen
import com.gothwad.store.ui.screens.FeedbackScreen
import com.gothwad.store.ui.screens.GamesScreen
import com.gothwad.store.ui.screens.HelpScreen
import com.gothwad.store.ui.screens.HomeScreen
import com.gothwad.store.ui.screens.InstallHelpScreen
import com.gothwad.store.ui.screens.InstallMethodScreen
import com.gothwad.store.ui.screens.InstallPermissionScreen
import com.gothwad.store.ui.screens.InstallSettingsScreen
import com.gothwad.store.ui.screens.LegalScreen
import com.gothwad.store.ui.screens.LicensesScreen
import com.gothwad.store.ui.screens.MyAppsScreen
import com.gothwad.store.ui.screens.NetworkScreen
import com.gothwad.store.ui.screens.NotificationsScreen
import com.gothwad.store.ui.screens.OnDeviceScreen
import com.gothwad.store.ui.screens.PrivacyScreen
import com.gothwad.store.ui.screens.ReportAppScreen
import com.gothwad.store.ui.screens.ReviewsScreen
import com.gothwad.store.ui.screens.RootScreen
import com.gothwad.store.ui.screens.ScreenshotsScreen
import com.gothwad.store.ui.screens.SearchScreen
import com.gothwad.store.ui.screens.SettingsScreen
import com.gothwad.store.ui.screens.ShizukuScreen
import com.gothwad.store.ui.screens.SignInScreen
import com.gothwad.store.ui.screens.StorageScreen
import com.gothwad.store.ui.screens.TopChartsScreen
import com.gothwad.store.ui.screens.WelcomeScreen
import com.gothwad.store.ui.screens.WhatsNewScreen
import com.gothwad.store.ui.screens.WishlistScreen
import com.gothwad.store.ui.screens.WriteReviewScreen

/**
 * One NavHost for the whole app. The four tab destinations render the shared
 * Play-style bottom bar; every other destination is pushed with a back arrow.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    vm: StoreViewModel,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    startDestination: String = Routes.HOME,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route.orEmpty()

    val onTab: (String) -> Unit = { route ->
        if (route == Routes.SEARCH) {
            navController.navigate(Routes.SEARCH) { launchSingleTop = true }
        } else {
            navController.navigate(route) {
                popUpTo(Routes.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = {
                slideInHorizontally(animationSpec = tween(220)) { full -> full / 6 } + fadeIn(tween(180))
            },
            exitTransition = { fadeOut(tween(140)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = {
                slideOutHorizontally(animationSpec = tween(200)) { full -> full / 6 } + fadeOut(tween(160))
            },
        ) {
            // ── Tabs ────────────────────────────────────────────────────────
            composable(Routes.HOME) {
                TabShell(currentRoute, onTab, snackbarHostState) { HomeScreen(vm, navController) }
            }
            composable(Routes.APPS) {
                TabShell(currentRoute, onTab, snackbarHostState) { AppsScreen(vm, navController) }
            }
            composable(Routes.GAMES) {
                TabShell(currentRoute, onTab, snackbarHostState) { GamesScreen(vm, navController) }
            }
            composable(Routes.ACCOUNT) {
                TabShell(currentRoute, onTab, snackbarHostState) { AccountScreen(vm, navController) }
            }
            composable(Routes.SEARCH) { SearchScreen(vm, navController) }

            // ── Catalogue ───────────────────────────────────────────────────
            composable(
                route = Routes.CHARTS,
                arguments = listOf(navArgument("kind") { type = NavType.StringType; defaultValue = "apps" }),
            ) { entry ->
                TopChartsScreen(vm, navController, entry.arguments?.getString("kind") ?: "apps")
            }
            composable(Routes.CATEGORIES) { CategoriesScreen(vm, navController) }
            composable(
                route = Routes.CATEGORY,
                arguments = listOf(
                    navArgument("slug") { type = NavType.StringType },
                    navArgument("kind") { type = NavType.StringType; defaultValue = "both" },
                ),
            ) { entry ->
                CategoryDetailScreen(
                    vm = vm,
                    nav = navController,
                    slug = entry.arguments?.getString("slug").orEmpty(),
                    kind = entry.arguments?.getString("kind") ?: "both",
                )
            }

            // ── App detail + sub-screens ────────────────────────────────────
            composable(
                route = Routes.APP_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry -> AppDetailScreen(vm, navController, entry.arguments?.getString("id").orEmpty()) }
            composable(
                route = Routes.APP_SCREENSHOTS,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry -> ScreenshotsScreen(vm, navController, entry.arguments?.getString("id").orEmpty()) }
            composable(
                route = Routes.APP_WHATSNEW,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry -> WhatsNewScreen(vm, navController, entry.arguments?.getString("id").orEmpty()) }
            composable(
                route = Routes.APP_PERMISSIONS,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry -> AppPermissionsScreen(vm, navController, entry.arguments?.getString("id").orEmpty()) }
            composable(
                route = Routes.APP_INFO,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry -> AppInfoScreen(vm, navController, entry.arguments?.getString("id").orEmpty()) }
            composable(
                route = Routes.APP_REVIEWS,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry -> ReviewsScreen(vm, navController, entry.arguments?.getString("id").orEmpty()) }
            composable(
                route = Routes.APP_WRITE_REVIEW,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry -> WriteReviewScreen(vm, navController, entry.arguments?.getString("id").orEmpty()) }
            composable(
                route = Routes.APP_REPORT,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry -> ReportAppScreen(vm, navController, entry.arguments?.getString("id").orEmpty()) }

            // ── Library ─────────────────────────────────────────────────────
            composable(
                route = Routes.MY_APPS,
                arguments = listOf(navArgument("tab") { type = NavType.StringType; defaultValue = "updates" }),
            ) { entry -> MyAppsScreen(vm, navController, entry.arguments?.getString("tab")) }
            composable(Routes.UPDATES) { MyAppsScreen(vm, navController, "updates") }
            composable(Routes.WISHLIST) { WishlistScreen(vm, navController) }
            composable(Routes.ON_DEVICE) { OnDeviceScreen(vm, navController) }

            // ── Downloads ───────────────────────────────────────────────────
            composable(Routes.DOWNLOADS) { DownloadsScreen(vm, navController) }
            composable(Routes.DOWNLOAD_HISTORY) { DownloadHistoryScreen(vm, navController) }
            composable(Routes.DOWNLOAD_SETTINGS) { DownloadSettingsScreen(vm, navController) }
            composable(Routes.STORAGE) { StorageScreen(vm, navController) }

            // ── Installer ───────────────────────────────────────────────────
            composable(Routes.INSTALL_SETTINGS) { InstallSettingsScreen(vm, navController) }
            composable(Routes.INSTALL_METHOD) { InstallMethodScreen(vm, navController) }
            composable(Routes.INSTALL_PERMISSION) { InstallPermissionScreen(vm, navController) }
            composable(Routes.INSTALL_SHIZUKU) { ShizukuScreen(vm, navController) }
            composable(Routes.INSTALL_ROOT) { RootScreen(vm, navController) }
            composable(Routes.INSTALL_ADB) { AdbScreen(vm, navController) }
            composable(Routes.INSTALL_HELP) { InstallHelpScreen(vm, navController) }

            // ── Account ─────────────────────────────────────────────────────
            composable(Routes.SIGN_IN) { SignInScreen(vm, navController) }
            composable(Routes.ACCOUNT_DETAILS) { AccountDetailsScreen(vm, navController) }
            composable(Routes.ACCOUNT_SECURITY) { AccountSecurityScreen(vm, navController) }

            // ── Settings ────────────────────────────────────────────────────
            composable(Routes.SETTINGS) { SettingsScreen(vm, navController) }
            composable(Routes.APPEARANCE) { AppearanceScreen(vm, navController) }
            composable(Routes.NOTIFICATIONS) { NotificationsScreen(vm, navController) }
            composable(Routes.NETWORK) { NetworkScreen(vm, navController) }
            composable(Routes.PRIVACY) { PrivacyScreen(vm, navController) }
            composable(Routes.ADVANCED) { AdvancedScreen(vm, navController) }
            composable(Routes.DEVICE_INFO) { DeviceInfoScreen(vm, navController) }

            // ── Developer + misc ────────────────────────────────────────────
            composable(Routes.DEVELOPER_CONSOLE) { DeveloperConsoleScreen(vm, navController) }
            composable(Routes.ABOUT) { AboutScreen(vm, navController) }
            composable(Routes.LEGAL) { LegalScreen(vm, navController) }
            composable(Routes.LICENSES) { LicensesScreen(vm, navController) }
            composable(Routes.HELP) { HelpScreen(vm, navController) }
            composable(Routes.FEEDBACK) { FeedbackScreen(vm, navController) }
            composable(Routes.WELCOME) { WelcomeScreen(vm, navController) }
        }
    }
}

/**
 * Publishes the bottom-bar configuration to the tab screens. Each of them builds
 * its own scaffold (so the top bar is theirs), and [GothwadScaffold] picks the
 * tab chrome straight out of this composition local.
 */
@Composable
private fun TabShell(
    currentRoute: String,
    onTab: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalTabChrome provides TabChrome(currentRoute = currentRoute, onTabSelected = onTab),
        LocalAppSnackbarHost provides snackbarHostState,
    ) {
        content()
    }
}
