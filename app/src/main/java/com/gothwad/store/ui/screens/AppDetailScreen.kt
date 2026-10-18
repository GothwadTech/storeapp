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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.gothwad.store.core.AppGraph
import com.gothwad.store.core.Format
import com.gothwad.store.data.auth.AuthState
import com.gothwad.store.data.install.InstallMethod
import com.gothwad.store.data.install.InstallPhase
import com.gothwad.store.data.model.AppDetailResponse
import com.gothwad.store.data.model.StoreApp
import com.gothwad.store.data.model.StoreRelease
import com.gothwad.store.ui.InstallAction
import com.gothwad.store.ui.Routes
import com.gothwad.store.ui.StoreViewModel
import com.gothwad.store.ui.UiState
import com.gothwad.store.ui.components.AppIcon
import com.gothwad.store.ui.components.DividerLine
import com.gothwad.store.ui.components.ErrorState
import com.gothwad.store.ui.components.GothwadScaffold
import com.gothwad.store.ui.components.GothwadTopBar
import com.gothwad.store.ui.components.InfoBanner
import com.gothwad.store.ui.components.InstallButton
import com.gothwad.store.ui.components.KeyValueRow
import com.gothwad.store.ui.components.PillLabel
import com.gothwad.store.ui.components.ProgressBar
import com.gothwad.store.ui.components.SectionHeader
import com.gothwad.store.ui.components.SkeletonList
import com.gothwad.store.ui.components.StarRow
import com.gothwad.store.ui.components.TagBadge
import com.gothwad.store.ui.components.VerifiedBadge
import com.gothwad.store.ui.components.rememberInstallAction
import com.gothwad.store.ui.theme.GothwadTheme
import com.gothwad.store.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun AppDetailScreen(vm: StoreViewModel, nav: NavController, appId: String) {
    val state by vm.detail.collectAsStateWithLifecycle()
    val phases by vm.installPhases.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val libraryState by AppGraph.library.state.collectAsStateWithLifecycle()
    val authState by AppGraph.auth.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(appId) { vm.openApp(appId) }

    GothwadScaffold(
        topBar = {
            GothwadTopBar(
                title = (state as? UiState.Ready)?.value?.app?.name ?: "App details",
                onBack = { nav.popBackStack() },
                actions = {
                    val app = (state as? UiState.Ready)?.value?.app
                    if (app != null) {
                        IconButton(onClick = { shareApp(app) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share", tint = GothwadTheme.extra.textSecondary)
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            UiState.Loading -> Column(Modifier.padding(padding)) { SkeletonList(rows = 6) }
            is UiState.Failed -> Column(Modifier.padding(padding)) {
                ErrorState(current.message, onRetry = { vm.retryDetail(appId) })
            }
            is UiState.Ready -> {
                val detail = current.value
                val app = detail.app
                val release = detail.downloadableRelease(android.os.Build.VERSION.SDK_INT)
                val installed = libraryState.installed[app.packageName]
                val task = downloads.firstOrNull { it.appId == app.id }
                val taskImage = task
                if (taskImage != null) {
                    LaunchedEffect(taskImage.status, taskImage.error) {
                        if (taskImage.status == com.gothwad.store.data.download.DownloadStatus.FAILED) {
                            vm.emit(com.gothwad.store.ui.StoreEvent.Message(taskImage.error ?: "Download failed."))
                        }
                    }
                }
                val action = resolveDetailAction(installed, release, task, phases[app.id] ?: InstallPhase.Idle)
                val wishlisted = app.id in libraryState.wishlist
                DetailContent(
                    detail = detail,
                    release = release,
                    action = action,
                    installed = installed,
                    wishlisted = wishlisted,
                    signedIn = authState is AuthState.SignedIn,
                    bottomPadding = padding.calculateBottomPadding(),
                    onAction = { vm.onAppAction(app) },
                    onWishlist = {
                        if (authState is AuthState.SignedIn) {
                            scope.launch {
                                val added = AppGraph.library.toggleWishlist(app.id)
                                vm.emit(
                                    com.gothwad.store.ui.StoreEvent.Message(
                                        if (added) "${app.name} added to your wishlist." else "Removed from wishlist.",
                                    ),
                                )
                            }
                        } else {
                            vm.emit(
                                com.gothwad.store.ui.StoreEvent.NeedsSignIn(
                                    "Sign in with Gothwad to keep a wishlist across devices.",
                                ),
                            )
                        }
                    },
                    onOpenScreenshots = { nav.navigate(Routes.screenshots(app.id)) },
                    onOpenReviews = { nav.navigate(Routes.reviews(app.id)) },
                    onWriteReview = {
                        if (authState is AuthState.SignedIn) nav.navigate(Routes.writeReview(app.id))
                        else vm.emit(com.gothwad.store.ui.StoreEvent.NeedsSignIn("Sign in to write a review."))
                    },
                    onOpenPermissions = { nav.navigate(Routes.permissions(app.id)) },
                    onOpenInfo = { nav.navigate(Routes.info(app.id)) },
                    onOpenWhatsNew = { nav.navigate(Routes.whatsNew(app.id)) },
                    onReport = { nav.navigate(Routes.report(app.id)) },
                    onOpenDeveloper = { nav.navigate(Routes.DEVELOPER_CONSOLE) },
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    detail: AppDetailResponse,
    release: StoreRelease?,
    action: InstallAction,
    installed: Long?,
    wishlisted: Boolean,
    signedIn: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onAction: () -> Unit,
    onWishlist: () -> Unit,
    onOpenScreenshots: () -> Unit,
    onOpenReviews: () -> Unit,
    onWriteReview: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenWhatsNew: () -> Unit,
    onReport: () -> Unit,
    onOpenDeveloper: () -> Unit,
) {
    val app = detail.app
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding + Spacing.bottomContentPadding),
    ) {
        item { HeaderSection(detail, release, installed) }

        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenH)) {
                Spacer(Modifier.height(6.dp))
                InstallButton(
                    action = action,
                    onClick = onAction,
                    expanded = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    DetailAction(Icons.Filled.BookmarkBorder.takeIf { !wishlisted } ?: Icons.Filled.Bookmark, if (wishlisted) "Wishlisted" else "Wishlist", onWishlist)
                    DetailAction(Icons.Filled.Share, "Share", { shareApp(app) })
                    DetailAction(Icons.Filled.Flag, "Report", onReport)
                }
            }
        }

        if (action is InstallAction.Unavailable) {
            item {
                Column(Modifier.padding(Spacing.screenH)) {
                    InfoBanner(
                        title = "Not installable on this device",
                        message = "This listing has no approved release that supports Android " +
                            "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}). " +
                            "Minimum Android for the newest build is API ${release?.minSdk ?: "unknown"}.",
                        tone = com.gothwad.store.ui.components.BannerTone.Warning,
                    )
                }
            }
        }

        if (detail.screenshots.isNotEmpty()) {
            item {
                LazyRow(
                    modifier = Modifier.padding(top = 18.dp),
                    contentPadding = PaddingValues(horizontal = Spacing.screenH),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(detail.screenshots) { url ->
                        Box(
                            modifier = Modifier
                                .size(width = 140.dp, height = 250.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(onClick = onOpenScreenshots),
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = "${app.name} screenshot",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

        item { SectionHeader(title = "About this app") }
        item {
            Column(Modifier.padding(horizontal = Spacing.screenH)) {
                Text(
                    app.fullDesc.ifBlank { app.shortDesc.ifBlank { "No description provided yet." } },
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = GothwadTheme.extra.textSecondary,
                )
                if (app.tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        app.tags.take(4).forEach { TagBadge(it) }
                    }
                }
            }
        }

        if (release != null && release.changelog.isNotBlank()) {
            item {
                SectionHeader(title = "What's new", subtitle = "Version ${release.versionName}", onSeeAll = onOpenWhatsNew)
            }
            item {
                Text(
                    release.changelog,
                    modifier = Modifier.padding(horizontal = Spacing.screenH),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = GothwadTheme.extra.textSecondary,
                )
            }
        }

        item {
            SectionHeader(title = "Data & permissions")
        }
        item {
            Column(Modifier.padding(horizontal = Spacing.screenH)) {
                DetailRow(
                    icon = Icons.Filled.Security,
                    title = "${release?.permissions?.size ?: 0} permissions",
                    subtitle = "See exactly what this build can access",
                    onClick = onOpenPermissions,
                )
                DetailRow(
                    icon = Icons.Filled.VerifiedUser,
                    title = if (action is InstallAction.Open) "Installed from Kiwi Store" else "Checksum verified download",
                    subtitle = release?.apkSha256?.take(18)?.plus("…") ?: "No published checksum",
                )
                DetailRow(
                    icon = Icons.Filled.Info,
                    title = "Technical details",
                    subtitle = "${app.packageName} · ${if (release != null) Format.bytes(release.apkSizeBytes) else "size unknown"}",
                    onClick = onOpenInfo,
                )
            }
        }

        item {
            SectionHeader(
                title = "Ratings & reviews",
                subtitle = Format.reviews(app.ratingCount),
                onSeeAll = onOpenReviews,
            )
        }
        item { RatingSummary(detail) }
        if (detail.reviews.isEmpty()) {
            item {
                Text(
                    "No reviews yet. Be the first to review ${app.name}.",
                    modifier = Modifier.padding(horizontal = Spacing.screenH, vertical = 8.dp),
                    fontSize = 13.sp,
                    color = GothwadTheme.extra.textTertiary,
                )
            }
        } else {
            items(detail.reviews.take(3), key = { it.id }) { review ->
                Column {
                    DividerLine(startIndent = 58.dp)
                    com.gothwad.store.ui.components.ReviewRow(
                        username = review.username,
                        rating = review.rating,
                        body = review.body,
                        whenLabel = Format.relative(review.createdAt),
                        developerReply = review.developerReply,
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(Spacing.screenH)) {
                com.gothwad.store.ui.components.OutlineButton(
                    label = if (signedIn) "Write a review" else "Sign in to review",
                    onClick = onWriteReview,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item { SectionHeader(title = "Developer") }
        item {
            Column(Modifier.padding(horizontal = Spacing.screenH)) {
                DetailRow(
                    icon = Icons.Filled.OpenInNew,
                    title = app.developerName.ifBlank { "Unknown developer" },
                    subtitle = app.supportEmail.ifBlank { "Open the Gothwad Developer Console" },
                    onClick = onOpenDeveloper,
                )
                if (!app.website.isNullOrBlank()) {
                    DetailRow(
                        icon = Icons.Filled.OpenInNew,
                        title = "Website",
                        subtitle = app.website,
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(app.website),
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    )
                }
                if (!app.privacyPolicyUrl.isNullOrBlank()) {
                    DetailRow(
                        icon = Icons.Filled.Description,
                        title = "Privacy policy",
                        subtitle = app.privacyPolicyUrl,
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(app.privacyPolicyUrl),
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    )
                }
            }
        }

        item { DividerLine(modifier = Modifier.padding(vertical = 14.dp)) }
        item {
            Text(
                "Published on Kiwi Store · ${Format.isoDate(app.updatedAt)}",
                modifier = Modifier.padding(horizontal = Spacing.screenH),
                fontSize = 12.sp,
                color = GothwadTheme.extra.textTertiary,
            )
        }
    }
}

@Composable
private fun HeaderSection(detail: AppDetailResponse, release: StoreRelease?, installed: Long?) {
    val app = detail.app
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenH, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppIcon(iconUrl = app.iconUrl, name = app.name, size = 76.dp, corner = 18.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    app.name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = GothwadTheme.extra.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    app.developerName.ifBlank { "Unknown developer" },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                if (app.source != "Kiwi Store") {
                    Text("From ${app.source}", fontSize = 11.sp, color = GothwadTheme.extra.textSecondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (app.developerVerified == true) VerifiedBadge()
                    if (app.category.isNotBlank()) TagBadge(app.category.replaceFirstChar { it.uppercase() })
                    if (installed != null) {
                        PillLabel(
                            text = "Installed",
                            background = GothwadTheme.extra.successContainer,
                            contentColor = GothwadTheme.extra.success,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatColumn("${Format.rating(app.ratingAvg)} ★", Format.reviews(app.ratingCount).replace(" ratings", ""), modifier = Modifier.weight(1f))
            StatDivider()
            StatColumn(Format.installs(app.downloadsCount), "Downloads", modifier = Modifier.weight(1f))
            StatDivider()
            StatColumn(Format.bytes(release?.apkSizeBytes ?: 0), "Size", modifier = Modifier.weight(1f))
            StatDivider()
            StatColumn(
                value = release?.minSdk?.let { "API $it" } ?: "—",
                label = "Min Android",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = GothwadTheme.extra.textPrimary)
        Text(label, fontSize = 11.sp, color = GothwadTheme.extra.textSecondary, maxLines = 1)
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(26.dp)
            .background(GothwadTheme.extra.divider),
    )
}

@Composable
private fun DetailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
        Text(label, fontSize = 12.sp, color = GothwadTheme.extra.textSecondary)
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GothwadTheme.extra.textPrimary)
            Text(
                subtitle,
                fontSize = 12.sp,
                color = GothwadTheme.extra.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = GothwadTheme.extra.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun RatingSummary(detail: AppDetailResponse) {
    val app = detail.app
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenH)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    Format.rating(app.ratingAvg),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = GothwadTheme.extra.textPrimary,
                )
                StarRow(value = app.ratingAvg.toInt(), size = 14.dp)
                Text(
                    Format.reviews(app.ratingCount),
                    fontSize = 11.sp,
                    color = GothwadTheme.extra.textTertiary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (5 downTo 1).forEach { star ->
                    val count = detail.reviews.count { it.rating == star }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("$star", fontSize = 11.sp, color = GothwadTheme.extra.textTertiary)
                        ProgressBar(
                            progress = if (detail.reviews.isEmpty()) 0f else count.toFloat() / detail.reviews.size,
                            modifier = Modifier.weight(1f),
                            height = 5.dp,
                        )
                    }
                }
            }
        }
    }
}

private fun shareApp(app: StoreApp) {
    val context = AppGraph.context()
    val url = "${com.gothwad.store.BuildConfig.STORE_WEB_URL}app/${android.net.Uri.encode(app.id)}"
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, app.name)
        putExtra(android.content.Intent.EXTRA_TEXT, "${app.name} on Kiwi Store: $url")
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val chooser = android.content.Intent.createChooser(intent, "Share ${app.name}")
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(chooser) }
}

/** Detail screens reuse the list-level resolver but with the fetched release. */
private fun resolveDetailAction(
    installedVersion: Long?,
    release: StoreRelease?,
    task: com.gothwad.store.data.download.DownloadTask?,
    phase: InstallPhase,
) = com.gothwad.store.ui.resolveInstallAction(installedVersion, release, task, phase)
