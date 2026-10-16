package com.gothwad.store.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gothwad.store.ui.theme.GothwadTheme
import com.gothwad.store.ui.theme.PlayColors

/** App icon with a Play-style rounded square and a coloured fallback. */
@Composable
fun AppIcon(
    iconUrl: String?,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    corner: Dp = 14.dp,
) {
    val fallback = PlayColors.iconPalette[(name.hashCode().let { if (it < 0) -it else it }) % PlayColors.iconPalette.size]
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(fallback.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!iconUrl.isNullOrBlank()) {
            AsyncImage(
                model = iconUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = name.trim().take(1).uppercase().ifBlank { "?" },
                color = fallback,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value / 2.2f).sp,
            )
        }
    }
}

/** Round Gothwad avatar for the top bar and profile screens. */
@Composable
fun BrandAvatar(text: String, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.trim().take(1).uppercase().ifBlank { "G" },
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value / 2.4f).sp,
        )
    }
}

@Composable
fun PillLabel(
    text: String,
    background: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(12.dp))
        }
        Text(text, color = contentColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun VerifiedBadge() = PillLabel(
    text = "Verified",
    background = GothwadTheme.extra.successContainer,
    contentColor = GothwadTheme.extra.success,
    icon = Icons.Filled.CheckCircle,
)

@Composable
fun UpdateBadge() = PillLabel(
    text = "Update",
    background = PlayColors.BlueLight,
    contentColor = PlayColors.Blue,
    icon = Icons.Filled.InstallMobile,
)

@Composable
fun TagBadge(text: String) = PillLabel(
    text = text,
    background = GothwadTheme.extra.chipBackground,
    contentColor = GothwadTheme.extra.textSecondary,
)

/** Small 5-star display; half stars are not needed because ratings are 1 decimal. */
@Composable
fun StarRow(value: Int, size: Dp = 12.dp, tint: Color = GothwadTheme.extra.star) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { index ->
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = if (index < value) tint else GothwadTheme.extra.divider,
                modifier = Modifier.size(size),
            )
        }
    }
}

/** "4.5 ★ · 12K reviews" line used under app names. */
@Composable
fun RatingLine(rating: Double, count: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = com.gothwad.store.core.Format.rating(rating),
            fontSize = 12.sp,
            color = GothwadTheme.extra.textSecondary,
        )
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = GothwadTheme.extra.star,
            modifier = Modifier
                .padding(start = 2.dp)
                .size(12.dp),
        )
        if (count > 0) {
            Text(
                text = " · ${com.gothwad.store.core.Format.reviews(count)}",
                fontSize = 12.sp,
                color = GothwadTheme.extra.textSecondary,
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
) {
    val resolvedLabel = actionLabel ?: if (onSeeAll != null) "See all" else null
    val resolvedAction = onAction ?: onSeeAll
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = GothwadTheme.extra.textPrimary,
            )
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = GothwadTheme.extra.textSecondary)
            }
        }
        if (resolvedLabel != null && resolvedAction != null) {
            TextButton(onClick = resolvedAction) {
                Text(resolvedLabel, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }
        }
    }
}

/** Play's rounded filter chip. */
@Composable
fun PlayChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else GothwadTheme.extra.chipBorder,
        label = "chipBorder",
    )
    val background = animateColorAsState(
        targetValue = if (selected) GothwadTheme.extra.successContainer else Color.Transparent,
        label = "chipBackground",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background.value)
            .border(1.dp, border.value, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .heightIn(min = 32.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else GothwadTheme.extra.textPrimary,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Segmented control used on "Manage downloads" and the update screen. */
@Composable
fun SegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(50))
            .border(1.dp, GothwadTheme.extra.chipBorder, RoundedCornerShape(50)),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) GothwadTheme.extra.successContainer else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp)
                    .then(Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else GothwadTheme.extra.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 42.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun OutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 42.dp),
        shape = RoundedCornerShape(50),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, fontSize = 14.sp)
    }
}

/** White card used everywhere a Play surface would appear. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        content = content,
    )
}

enum class BannerTone { Info, Warning, Error, Success }

@Composable
fun InfoBanner(
    title: String,
    message: String,
    tone: BannerTone = BannerTone.Info,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val (container, accent) = when (tone) {
        BannerTone.Info -> GothwadTheme.extra.successContainer to GothwadTheme.extra.success
        BannerTone.Warning -> GothwadTheme.extra.warningContainer to GothwadTheme.extra.warning
        BannerTone.Error -> PlayColors.ErrorContainer to MaterialTheme.colorScheme.error
        BannerTone.Success -> GothwadTheme.extra.successContainer to GothwadTheme.extra.success
    }
    val icon = when (tone) {
        BannerTone.Error -> Icons.Filled.ErrorOutline
        BannerTone.Warning -> Icons.Filled.CloudOff
        else -> Icons.Filled.CheckCircle
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GothwadTheme.extra.textPrimary)
            Text(
                message,
                fontSize = 12.sp,
                color = GothwadTheme.extra.textSecondary,
                lineHeight = 17.sp,
            )
            if (action != null) {
                Spacer(Modifier.height(8.dp))
                action()
            }
        }
    }
}

@Composable
fun LoadingState(label: String = "Loading…", modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
            Spacer(Modifier.height(12.dp))
            Text(label, fontSize = 13.sp, color = GothwadTheme.extra.textSecondary)
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = GothwadTheme.extra.textTertiary,
                modifier = Modifier.size(38.dp),
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = GothwadTheme.extra.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            fontSize = 13.sp,
            color = GothwadTheme.extra.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            OutlineButton(label = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        title = "Could not load",
        message = message,
        actionLabel = if (onRetry != null) "Try again" else null,
        onAction = onRetry,
        modifier = modifier,
    )
}

/** Determinate progress bar, drawn by hand so it matches Play's thin bars. */
@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
) {
    val fraction = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(GothwadTheme.extra.divider),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
fun IndeterminateBar(modifier: Modifier = Modifier) {
    androidx.compose.material3.LinearProgressIndicator(
        modifier = modifier.fillMaxWidth().height(4.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = GothwadTheme.extra.divider,
    )
}

/** Grey placeholder rows while the Worker answers. */
@Composable
fun SkeletonList(rows: Int = 6, modifier: Modifier = Modifier, showIcons: Boolean = true) {
    Column(modifier.fillMaxWidth()) {
        repeat(rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showIcons) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(GothwadTheme.extra.chipBackground),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.55f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(GothwadTheme.extra.chipBackground),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.35f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(GothwadTheme.extra.chipBackground),
                    )
                }
            }
        }
    }
}

@Composable
fun DividerLine(startIndent: Dp = 0.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(1.dp)
            .background(GothwadTheme.extra.divider),
    )
}

/** label / value pair used by every "details" screen. */
@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            color = GothwadTheme.extra.textSecondary,
        )
        Text(
            value,
            modifier = Modifier.weight(1.3f),
            fontSize = 13.sp,
            color = GothwadTheme.extra.textPrimary,
            textAlign = TextAlign.End,
        )
    }
}

/** One user review with an optional developer reply. */
@Composable
fun ReviewRow(
    username: String,
    rating: Int,
    body: String,
    whenLabel: String,
    developerReply: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(GothwadTheme.extra.chipBackground),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    username.trim().take(1).uppercase().ifBlank { "U" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GothwadTheme.extra.textSecondary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(username.ifBlank { "Gothwad user" }, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StarRow(value = rating, size = 11.dp)
                    Text(whenLabel, fontSize = 11.sp, color = GothwadTheme.extra.textTertiary)
                }
            }
            Text("⋮", fontSize = 16.sp, color = GothwadTheme.extra.textTertiary)
        }
        if (body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(body, fontSize = 13.sp, color = GothwadTheme.extra.textPrimary, lineHeight = 19.sp)
        }
        if (!developerReply.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GothwadTheme.extra.chipBackground)
                    .padding(12.dp),
            ) {
                Text("Developer reply", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(developerReply, fontSize = 12.sp, color = GothwadTheme.extra.textSecondary, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = { Text(message, fontSize = 13.sp, lineHeight = 19.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}
