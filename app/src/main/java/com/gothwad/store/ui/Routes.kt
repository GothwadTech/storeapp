package com.gothwad.store.ui

import android.net.Uri

/** Every destination in the app. Plain strings keep deep links debuggable. */
object Routes {
    // Bottom navigation tabs
    const val HOME = "home"
    const val APPS = "apps"
    const val SEARCH = "search"
    const val GAMES = "games"
    const val ACCOUNT = "account"

    val tabs = listOf(HOME, APPS, SEARCH, GAMES, ACCOUNT)

    // Catalogue
    const val CHARTS = "charts/{kind}"
    fun charts(kind: String) = "charts/$kind"
    const val CATEGORIES = "categories"
    const val CATEGORY = "category/{slug}/{kind}"
    fun category(slug: String, kind: String = "both") = "category/${Uri.encode(slug)}/$kind"

    // App detail and its sub-screens
    const val APP_DETAIL = "app/{id}"
    fun app(id: String) = "app/${Uri.encode(id)}"
    const val APP_SCREENSHOTS = "app/{id}/screenshots"
    fun screenshots(id: String) = "app/${Uri.encode(id)}/screenshots"
    const val APP_WHATSNEW = "app/{id}/whatsnew"
    fun whatsNew(id: String) = "app/${Uri.encode(id)}/whatsnew"
    const val APP_PERMISSIONS = "app/{id}/permissions"
    fun permissions(id: String) = "app/${Uri.encode(id)}/permissions"
    const val APP_INFO = "app/{id}/info"
    fun info(id: String) = "app/${Uri.encode(id)}/info"
    const val APP_REVIEWS = "app/{id}/reviews"
    fun reviews(id: String) = "app/${Uri.encode(id)}/reviews"
    const val APP_WRITE_REVIEW = "app/{id}/review"
    fun writeReview(id: String) = "app/${Uri.encode(id)}/review"
    const val APP_REPORT = "app/{id}/report"
    fun report(id: String) = "app/${Uri.encode(id)}/report"

    // Library
    const val MY_APPS = "my-apps"
    const val UPDATES = "updates"
    const val WISHLIST = "wishlist"
    const val ON_DEVICE = "on-device"

    // Downloads
    const val DOWNLOADS = "downloads"
    const val DOWNLOAD_HISTORY = "downloads/history"
    const val DOWNLOAD_SETTINGS = "downloads/settings"
    const val STORAGE = "downloads/storage"

    // Installer
    const val INSTALL_SETTINGS = "install/settings"
    const val INSTALL_METHOD = "install/method"
    const val INSTALL_PERMISSION = "install/permission"
    const val INSTALL_SHIZUKU = "install/shizuku"
    const val INSTALL_ROOT = "install/root"
    const val INSTALL_ADB = "install/adb"
    const val INSTALL_HELP = "install/help"

    // Account + settings
    const val SIGN_IN = "account/sign-in"
    const val ACCOUNT_DETAILS = "account/details"
    const val ACCOUNT_SECURITY = "account/security"
    const val SETTINGS = "settings"
    const val APPEARANCE = "settings/appearance"
    const val NOTIFICATIONS = "settings/notifications"
    const val NETWORK = "settings/network"
    const val PRIVACY = "settings/privacy"
    const val ADVANCED = "settings/advanced"
    const val DEVICE_INFO = "settings/device"
    const val DEVELOPER_CONSOLE = "developer/console"
    const val ABOUT = "about"
    const val LEGAL = "legal"
    const val LICENSES = "licenses"
    const val HELP = "help"
    const val FEEDBACK = "feedback"
    const val WELCOME = "welcome"

    fun appIdOf(routeValue: String?): String = Uri.decode(routeValue.orEmpty())
}
