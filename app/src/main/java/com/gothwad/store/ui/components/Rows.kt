package com.gothwad.store.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gothwad.store.core.Format
import com.gothwad.store.data.install.InstallPhase
import com.gothwad.store.data.model.StoreApp
import com.gothwad.store.data.model.StoreRelease
import com.gothwad.store.ui.InstallAction
import com.gothwad.store.ui.theme.GothwadTheme
import com.gothwad.store.ui.theme.PlayColors

/**
 * The Play-store style install control: a green rounded button that turns into a
 * progress ring while downloading, then into "Open" once installed.
 */
@Composable
fun InstallButton(
    action: InstallAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    testTag: String? = null,
) {
    val tagModifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
    when (action) {
        is InstallAction.Downloading, is InstallAction.Paused -> {
            val progress = when (action) {
                is InstallAction.Downloading -> action.progress
                is InstallAction.Paused -> action.progress
                else -> 0f
            }
            Box(modifier = modifier.then(tagModifier), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(GothwadTheme.extra.successContainer)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (action is InstallAction.Paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (action is InstallAction.Paused) "Resume download" else "Pause download",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp),
                    )
                    ProgressRing(progress = if (progress <= 0f) 0.02f else progress)
                }
            }
        }

        is InstallAction.Installing -> Box(modifier.then(tagModifier), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(GothwadTheme.extra.successContainer),
                contentAlignment = Alignment.Center,
            ) {
                ProgressRing(progress = action.progress.coerceAtLeast(0.02f))
            }
        }

        InstallAction.Busy -> Box(modifier.then(tagModifier), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(GothwadTheme.extra.successContainer),
                contentAlignment = Alignment.Center,
            ) {
                ProgressRing(progress = 0.02f)
            }
        }

        InstallAction.Retry -> RoundActionButton(
            icon = Icons.Filled.Refresh,
            contentDescription = "Retry download",
            filled = true,
            onClick = onClick,
            modifier = modifier.then(tagModifier),
        )

        InstallAction.Unavailable -> Icon(
            Icons.Filled.Download,
            contentDescription = "No compatible release",
            tint = GothwadTheme.extra.textTertiary,
            modifier = modifier.then(tagModifier).size(20.dp),
        )

        else -> {
            val open = action == InstallAction.Open
            val label = action.label
            if (expanded) {
                Box(
                    modifier = modifier
                        .then(tagModifier)
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (open) GothwadTheme.extra.chipBackground else MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (open) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
            } else {
                Box(
                    modifier = modifier
                        .then(tagModifier)
                        .heightIn(min = 34.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (open) GothwadTheme.extra.chipBackground else MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onClick)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (open) MaterialTheme.colorScheme.primary else Color.White,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (filled) MaterialTheme.colorScheme.primary else GothwadTheme.extra.chipBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (filled) Color.White else GothwadTheme.extra.textSecondary,
            modifier = Modifier.size(19.dp),
        )
    }
}

/** Circular determinate progress indicator (download ring). */
@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier) {
    androidx.compose.material3.CircularProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.size(38.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = GothwadTheme.extra.successContainer,
        strokeWidth = 2.5.dp,
    )
}

/** The main Play-style list row: icon, name, meta, install button. */
@Composable
fun AppRow(
    app: StoreApp,
    release: StoreRelease?,
    action: InstallAction,
    onOpen: () -> Unit,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    iconSize: Dp = 52.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (rank != null) {
            Text(
                "$rank",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = GothwadTheme.extra.textSecondary,
                modifier = Modifier.width(18.dp),
            )
        }
        AppIcon(iconUrl = app.iconUrl, name = app.name, size = iconSize, corner = 14.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                app.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = GothwadTheme.extra.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(subtitle ?: buildMeta(app, release))
                    if (app.source != "Gothwad Store") append(" · From ${app.source}")
                },
                fontSize = 12.sp,
                color = GothwadTheme.extra.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (app.ratingCount > 0 && subtitle == null) {
                RatingLine(rating = app.ratingAvg, count = app.ratingCount)
            }
        }
        if (trailing != null) trailing() else InstallButton(
            action = action,
            onClick = onActionClick,
            testTag = "install_${app.packageName}",
        )
    }
}

/** Compact row used for search suggestions and "trending" lists. */
@Composable
fun CompactAppRow(
    app: StoreApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(iconUrl = app.iconUrl, name = app.name, size = 40.dp, corner = 11.dp)
        Column(Modifier.weight(1f)) {
            Text(app.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append(app.developerName.ifBlank { Format.packageLabel(app.packageName) })
                    if (app.source != "Gothwad Store") append(" · From ${app.source}")
                },
                fontSize = 11.sp,
                color = GothwadTheme.extra.textSecondary,
                maxLines = 1,
            )
        }
        if (badge != null) badge()
    }
}

/** Horizontal "Suggested for you" card. */
@Composable
fun SmallAppCard(
    app: StoreApp,
    action: InstallAction,
    onClick: () -> Unit,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(116.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(iconUrl = app.iconUrl, name = app.name, size = 72.dp, corner = 18.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            app.name,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = GothwadTheme.extra.textPrimary,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        InstallButton(action = action, onClick = onActionClick, expanded = false, testTag = "install_card_${app.packageName}")
    }
}

/** Three-per-row grid cell used by "Top free" grids. */
@Composable
fun AppGridCard(
    app: StoreApp,
    action: InstallAction,
    onClick: () -> Unit,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(iconUrl = app.iconUrl, name = app.name, size = 76.dp, corner = 20.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            app.name,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = GothwadTheme.extra.textPrimary,
            fontWeight = FontWeight.Medium,
        )
        if (app.ratingCount > 0) {
            RatingLine(rating = app.ratingAvg, count = app.ratingCount)
        }
        Spacer(Modifier.height(6.dp))
        InstallButton(action = action, onClick = onActionClick, expanded = true, testTag = "install_grid_${app.packageName}")
    }
}

/** Game card with a wider feature graphic feel. */
@Composable
fun GameCard(
    app: StoreApp,
    action: InstallAction,
    onClick: () -> Unit,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(148.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Box(
            Modifier
                .size(width = 148.dp, height = 92.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(GothwadTheme.extra.bannerStart, GothwadTheme.extra.bannerEnd),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(iconUrl = app.iconUrl, name = app.name, size = 60.dp, corner = 16.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            app.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            app.developerName.ifBlank { "Gothwad Tech" },
            fontSize = 11.sp,
            color = GothwadTheme.extra.textSecondary,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        InstallButton(action = action, onClick = onActionClick, testTag = "install_game_${app.packageName}")
    }
}

/** Hero banner at the top of Home. */
@Composable
fun BannerCard(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(PlayColors.BannerStart, PlayColors.BannerEnd),
                ),
            )
            .padding(18.dp),
    ) {
        Column {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(message, fontSize = 12.sp, color = Color.White.copy(alpha = 0.92f), lineHeight = 17.sp)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable(onClick = onAction)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(actionLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Category tile on the Home / Categories screens. */
@Composable
fun CategoryTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, GothwadTheme.extra.chipBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(
            label,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = GothwadTheme.extra.textPrimary,
        )
    }
}

/** "Editors' choice" row with a Play-like orange star. */
@Composable
fun EditorChoiceRow(
    app: StoreApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIcon(iconUrl = app.iconUrl, name = app.name, size = 48.dp, corner = 13.dp)
        Column(Modifier.weight(1f)) {
            Text(app.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = GothwadTheme.extra.star,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    " Editors' choice",
                    fontSize = 11.sp,
                    color = GothwadTheme.extra.textSecondary,
                )
            }
        }
        if (app.ratingCount > 0) RatingLine(rating = app.ratingAvg, count = app.ratingCount)
    }
}

/** Updates / downloads row with per-job controls. */
@Composable
fun DownloadJobRow(
    title: String,
    subtitle: String,
    meta: String,
    progress: Float?,
    primaryIcon: androidx.compose.ui.graphics.vector.ImageVector,
    primaryDescription: String,
    onPrimary: () -> Unit,
    onOpen: () -> Unit,
    onSecondary: (() -> Unit)? = null,
    secondaryIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Cancel,
    secondaryDescription: String = "Cancel",
    iconUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppIcon(iconUrl = iconUrl, name = title, size = 48.dp, corner = 13.dp)
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 12.sp, color = GothwadTheme.extra.textSecondary, maxLines = 1)
                Text(meta, fontSize = 11.sp, color = GothwadTheme.extra.textTertiary, maxLines = 1)
            }
            IconButton(onClick = onPrimary) {
                Icon(primaryIcon, contentDescription = primaryDescription, tint = MaterialTheme.colorScheme.primary)
            }
            if (onSecondary != null) {
                IconButton(onClick = onSecondary) {
                    Icon(secondaryIcon, contentDescription = secondaryDescription, tint = GothwadTheme.extra.textSecondary)
                }
            }
        }
        if (progress != null) {
            ProgressBar(
                progress = progress,
                modifier = Modifier.padding(start = 78.dp, end = 16.dp, bottom = 8.dp),
            )
        }
    }
}

/** Update-available row used by "My apps & device". */
@Composable
fun UpdateRow(
    app: StoreApp,
    currentVersion: String?,
    newVersion: String,
    action: InstallAction,
    onOpen: () -> Unit,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIcon(iconUrl = app.iconUrl, name = app.name, size = 52.dp, corner = 14.dp)
        Column(Modifier.weight(1f)) {
            Text(app.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                currentVersion?.let { "Installed $it · new $newVersion" } ?: "Version $newVersion",
                fontSize = 12.sp,
                color = GothwadTheme.extra.textSecondary,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            UpdateBadge()
        }
        InstallButton(action = action, onClick = onActionClick, testTag = "update_${app.packageName}")
    }
}

private fun buildMeta(app: StoreApp, release: StoreRelease?): String = buildString {
    append(app.developerName.ifBlank { "Gothwad Tech" })
    if (release != null) {
        append(" · ")
        append(if (release.apkSizeBytes > 0) Format.bytes(release.apkSizeBytes) else "size unknown")
    }
    if (app.kind == "game") append(" · Game")
}

/** Small helper used by the download screens to describe an install phase. */
fun InstallPhase.describe(): String = when (this) {
    InstallPhase.Idle -> ""
    InstallPhase.Preparing -> "Preparing…"
    is InstallPhase.Installing -> label
    is InstallPhase.AwaitingUser -> label
    is InstallPhase.Completed -> message
    is InstallPhase.Failed -> message
}
