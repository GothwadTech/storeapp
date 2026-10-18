package com.gothwad.store.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gothwad.store.data.install.InstallCapabilities
import com.gothwad.store.data.install.InstallMethod
import com.gothwad.store.data.install.InstallerAvailability
import com.gothwad.store.ui.theme.GothwadTheme

/**
 * The "how should Kiwi Store install this?" sheet. Shown when the user picked
 * the Ask method, when a notification asks for an install, or from downloads.
 */
@Composable
fun InstallMethodSheet(
    appName: String,
    versionLabel: String,
    sizeLabel: String,
    selected: InstallMethod,
    capabilities: InstallCapabilities,
    onPick: (InstallMethod) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Box(
                Modifier
                    .padding(top = 6.dp, bottom = 14.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(GothwadTheme.extra.divider, RoundedCornerShape(50)),
            )
            Text("Install $appName", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(
                    versionLabel.takeIf { it.isNotBlank() }?.let { "Version $it" },
                    sizeLabel.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                fontSize = 12.sp,
                color = GothwadTheme.extra.textSecondary,
            )
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(contentPadding = PaddingValues(bottom = 18.dp)) {
            items(InstallMethod.selectable) { method ->
                val availability = capabilities.statusOf(method)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(method) }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        iconFor(method),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(21.dp),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(method.displayName(), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            method.blurb,
                            fontSize = 12.sp,
                            color = GothwadTheme.extra.textSecondary,
                            lineHeight = 17.sp,
                        )
                        if (availability != InstallerAvailability.Available) {
                            Text(
                                availabilityMessage(availability),
                                fontSize = 11.sp,
                                color = when (availability) {
                                    InstallerAvailability.NeedsPermission -> GothwadTheme.extra.warning
                                    else -> GothwadTheme.extra.textTertiary
                                },
                            )
                        }
                    }
                    RadioButton(selected = selected == method, onClick = { onPick(method) })
                }
            }

            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    InfoBanner(
                        title = "Verified before installing",
                        message = "The APK is hashed with SHA-256 and compared with the publisher's checksum. " +
                            "Kiwi Store deletes the file and stops if the two differ.",
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlineButton("Cancel install", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun iconFor(method: InstallMethod): ImageVector = when (method) {
    InstallMethod.Session -> Icons.Filled.VerifiedUser
    InstallMethod.Intent -> Icons.Filled.Android
    InstallMethod.Shizuku -> Icons.Filled.Terminal
    InstallMethod.Root -> Icons.Filled.Bolt
    InstallMethod.Ask -> Icons.Filled.Security
}

private fun availabilityMessage(availability: InstallerAvailability): String = when (availability) {
    InstallerAvailability.Available -> ""
    InstallerAvailability.NeedsPermission -> "Permission needed — you can grant it in the next step"
    InstallerAvailability.NotInstalled -> "Shizuku is not installed on this device"
    InstallerAvailability.Unavailable -> "Not available right now"
}
