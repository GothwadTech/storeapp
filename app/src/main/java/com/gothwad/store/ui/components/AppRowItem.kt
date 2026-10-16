package com.gothwad.store.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gothwad.store.core.AppGraph
import com.gothwad.store.data.install.InstallPhase
import com.gothwad.store.data.model.StoreApp
import com.gothwad.store.data.model.StoreRelease
import com.gothwad.store.ui.InstallAction
import com.gothwad.store.ui.resolveInstallAction

/**
 * Resolves the reactive button state for one app: download progress, install
 * phase and the installed version all feed into a single [InstallAction], so
 * every list in the app shows the same button without extra plumbing.
 */
@Composable
fun rememberInstallAction(app: StoreApp, release: StoreRelease?): InstallAction {
    val tasks by AppGraph.downloader.tasks.collectAsStateWithLifecycle()
    val phases by AppGraph.installer.phases.collectAsStateWithLifecycle()
    val libraryState by AppGraph.library.state.collectAsStateWithLifecycle()
    val installed = libraryState.installed[app.packageName]
    val task = tasks.firstOrNull { it.appId == app.id }
    val phase = phases[app.id] ?: InstallPhase.Idle
    return resolveInstallAction(installed, release, task, phase)
}

/**
 * One listing row: icon, name, meta line, install button. This is the row every
 * catalogue screen uses, so a download started on Home is visible on Search and
 * in the library at the same time.
 */
@Composable
fun AppRowItem(
    app: StoreApp,
    release: StoreRelease?,
    onOpen: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    subtitle: String? = null,
    trailingBadge: (@Composable () -> Unit)? = null,
    iconSize: Dp = 52.dp,
) {
    AppRow(
        app = app,
        release = release,
        action = rememberInstallAction(app, release),
        onOpen = onOpen,
        onActionClick = onAction,
        modifier = modifier,
        rank = rank,
        subtitle = subtitle,
        trailing = trailingBadge,
        iconSize = iconSize,
    )
}
