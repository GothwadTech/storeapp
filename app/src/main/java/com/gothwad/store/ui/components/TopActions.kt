package com.gothwad.store.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gothwad.store.core.AppGraph
import com.gothwad.store.data.auth.AuthState
import com.gothwad.store.data.download.isActive
import com.gothwad.store.ui.theme.GothwadTheme

@Composable
fun DownloadsAction(onClick: () -> Unit) {
    val tasks by AppGraph.downloader.tasks.collectAsStateWithLifecycle()
    val active = tasks.count { it.isActive }
    Box {
        IconButton(onClick = onClick) {
            Icon(
                Icons.Filled.Download,
                contentDescription = "Downloads",
                tint = GothwadTheme.extra.textSecondary,
            )
        }
        if (active > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    active.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun SearchAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.Filled.Search,
            contentDescription = "Search",
            tint = GothwadTheme.extra.textSecondary,
        )
    }
}

@Composable
fun AvatarAction(onClick: () -> Unit) {
    val authState by AppGraph.auth.state.collectAsStateWithLifecycle()
    val signedIn = authState as? AuthState.SignedIn
    val initial = signedIn?.user?.initial ?: "G"
    val picture = signedIn?.user?.picture

    Row(
        modifier = Modifier
            .padding(end = 8.dp)
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!picture.isNullOrBlank()) {
            AppIcon(
                iconUrl = picture,
                name = initial,
                size = 34.dp,
                corner = 17.dp,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (signedIn != null) MaterialTheme.colorScheme.primary
                        else GothwadTheme.extra.textTertiary,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initial,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
