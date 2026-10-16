package com.gothwad.store.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gothwad.store.ui.theme.GothwadTheme

@Composable
fun SettingsGroup(title: String? = null, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
        if (title != null) {
            Text(
                title,
                modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        SettingsCard { content() }
    }
}

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailingText: String? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = GothwadTheme.extra.textSecondary, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, color = GothwadTheme.extra.textPrimary, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = GothwadTheme.extra.textSecondary, lineHeight = 17.sp)
            }
        }
        if (trailingText != null) {
            Text(trailingText, fontSize = 13.sp, color = GothwadTheme.extra.textSecondary)
        }
        trailing?.invoke()
        if (showChevron && onClick != null && trailing == null && trailingText == null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = GothwadTheme.extra.textTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun SwitchSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = GothwadTheme.extra.textSecondary, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                fontSize = 15.sp,
                color = if (enabled) GothwadTheme.extra.textPrimary else GothwadTheme.extra.textTertiary,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = GothwadTheme.extra.textSecondary, lineHeight = 17.sp)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
fun RadioSettingRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    badge: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = GothwadTheme.extra.textSecondary, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = GothwadTheme.extra.textPrimary)
                badge?.invoke()
            }
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = GothwadTheme.extra.textSecondary, lineHeight = 17.sp)
            }
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
fun CheckRow(
    title: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(22.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = GothwadTheme.extra.textPrimary)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = GothwadTheme.extra.textSecondary)
            }
        }
    }
}

@Composable
fun SectionSpacer(height: Int = 8) {
    Spacer(Modifier.height(height.dp))
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, hint: String? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GothwadTheme.extra.textPrimary)
        Text(label, fontSize = 12.sp, color = GothwadTheme.extra.textSecondary)
        if (hint != null) {
            Text(hint, fontSize = 11.sp, color = GothwadTheme.extra.textTertiary)
        }
    }
}

@Composable
fun IconTextRow(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint ?: MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp).padding(top = 1.dp),
        )
        Text(text, fontSize = 13.sp, color = GothwadTheme.extra.textSecondary, lineHeight = 19.sp)
    }
}

@Composable
fun SubScreenHeader(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    subtitle: String? = null,
) {
    GothwadTopBar(title = title, subtitle = subtitle, onBack = onBack) { actions() }
}
