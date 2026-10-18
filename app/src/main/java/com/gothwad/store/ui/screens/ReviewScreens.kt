package com.gothwad.store.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.gothwad.store.core.AppGraph
import com.gothwad.store.core.Format
import com.gothwad.store.data.auth.AuthState
import com.gothwad.store.data.install.REPORT_REASONS
import com.gothwad.store.data.remote.StoreException
import com.gothwad.store.ui.Routes
import com.gothwad.store.ui.StoreEvent
import com.gothwad.store.ui.StoreViewModel
import com.gothwad.store.ui.UiState
import com.gothwad.store.ui.components.BannerTone
import com.gothwad.store.ui.components.DividerLine
import com.gothwad.store.ui.components.EmptyState
import com.gothwad.store.ui.components.ErrorState
import com.gothwad.store.ui.components.GothwadScaffold
import com.gothwad.store.ui.components.GothwadTopBar
import com.gothwad.store.ui.components.InfoBanner
import com.gothwad.store.ui.components.PrimaryButton
import com.gothwad.store.ui.components.RadioSettingRow
import com.gothwad.store.ui.components.ReviewRow
import com.gothwad.store.ui.components.SkeletonList
import com.gothwad.store.ui.components.StarRow
import com.gothwad.store.ui.theme.GothwadTheme
import com.gothwad.store.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun ReviewsScreen(vm: StoreViewModel, nav: NavController, appId: String) {
    val state by vm.detail.collectAsStateWithLifecycle()
    val authState by AppGraph.auth.state.collectAsStateWithLifecycle()
    LaunchedEffect(appId) { if (state !is UiState.Ready) vm.openApp(appId) }

    GothwadScaffold(
        topBar = {
            GothwadTopBar(
                title = "Ratings & reviews",
                onBack = { nav.popBackStack() },
                actions = { com.gothwad.store.ui.components.DownloadsAction { nav.navigate(Routes.DOWNLOADS) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                UiState.Loading -> SkeletonList(rows = 6)
                is UiState.Failed -> ErrorState(current.message, onRetry = { vm.openApp(appId) })
                is UiState.Ready -> {
                    val detail = current.value
                    val reviews = detail.reviews
                    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding)) {
                        item {
                            Column(Modifier.padding(Spacing.screenH)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            Format.rating(detail.app.ratingAvg),
                                            fontSize = 42.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        StarRow(detail.app.ratingAvg.toInt(), size = 15.dp)
                                        Text(
                                            Format.reviews(detail.app.ratingCount),
                                            fontSize = 11.sp,
                                            color = GothwadTheme.extra.textTertiary,
                                            modifier = Modifier.padding(top = 6.dp),
                                        )
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        (5 downTo 1).forEach { star ->
                                            val count = reviews.count { it.rating == star }
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text("$star", fontSize = 11.sp, color = GothwadTheme.extra.textTertiary)
                                                com.gothwad.store.ui.components.ProgressBar(
                                                    progress = if (reviews.isEmpty()) 0f else count.toFloat() / reviews.size,
                                                    modifier = Modifier.weight(1f),
                                                    height = 5.dp,
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                                com.gothwad.store.ui.components.OutlineButton(
                                    label = if (authState is AuthState.SignedIn) "Write a review" else "Sign in to review",
                                    onClick = {
                                        if (authState is AuthState.SignedIn) nav.navigate(Routes.writeReview(appId))
                                        else vm.emit(StoreEvent.NeedsSignIn("Sign in with Gothwad to review apps."))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        if (reviews.isEmpty()) {
                            item {
                                EmptyState(
                                    title = "No reviews yet",
                                    message = "Nobody has rated ${detail.app.name} yet. Your review would be the first.",
                                    icon = Icons.Filled.Star,
                                )
                            }
                        } else {
                            items(reviews, key = { it.id }) { review ->
                                Column {
                                    DividerLine(startIndent = 58.dp)
                                    ReviewRow(
                                        username = review.username,
                                        rating = review.rating,
                                        body = review.body,
                                        whenLabel = Format.relative(review.createdAt),
                                        developerReply = review.developerReply,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WriteReviewScreen(vm: StoreViewModel, nav: NavController, appId: String) {
    val state by vm.detail.collectAsStateWithLifecycle()
    val authState by AppGraph.auth.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var rating by rememberSaveable { mutableIntStateOf(5) }
    var body by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val signedIn = authState as? AuthState.SignedIn

    LaunchedEffect(appId) { if (state !is UiState.Ready) vm.openApp(appId) }

    GothwadScaffold(
        topBar = { GothwadTopBar(title = "Write a review", onBack = { nav.popBackStack() }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.screenH),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (signedIn == null) {
                InfoBanner(
                    title = "Sign in required",
                    message = "Reviews are tied to your Gothwad account so developers can reply and so " +
                        "one person cannot rate an app twice.",
                    tone = BannerTone.Warning,
                )
                PrimaryButton("Sign in with Gothwad", onClick = { nav.navigate(Routes.SIGN_IN) })
                return@Column
            }

            val app = (state as? UiState.Ready)?.value?.app
            InfoBanner(
                title = signedIn.user.displayName,
                message = "Your review is posted publicly on ${app?.name ?: "this listing"} as ${signedIn.user.username.ifBlank { signedIn.user.displayName }}.",
            )

            Column {
                Text("Your rating", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { star ->
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "$star star",
                            tint = if (star <= rating) GothwadTheme.extra.star else GothwadTheme.extra.chipBorder,
                            modifier = Modifier
                                .size(34.dp)
                                .clickable { rating = star },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = body,
                onValueChange = { if (it.length <= 900) body = it },
                label = { Text("Describe your experience (optional)") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${body.length}/900", fontSize = 11.sp, color = GothwadTheme.extra.textTertiary)

            if (error != null) {
                InfoBanner(title = "Could not post the review", message = error.orEmpty(), tone = BannerTone.Error)
            }

            PrimaryButton(
                label = if (busy) "Posting…" else "Post review",
                enabled = !busy,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        runCatching { AppGraph.store.submitReview(appId, rating, body.trim()) }
                            .onSuccess {
                                vm.emit(StoreEvent.Message("Thanks! Your review is live."))
                                vm.openApp(appId)
                                nav.popBackStack()
                            }
                            .onFailure { failure ->
                                error = if (failure is StoreException && failure.kind == StoreException.Kind.UNAUTHORIZED) {
                                    "Your Gothwad session expired. Sign in again and retry."
                                } else {
                                    failure.message ?: "The Store rejected the review."
                                }
                            }
                        busy = false
                    }
                },
            )

            Text(
                "One review per account per app. Posting again updates your existing review.",
                fontSize = 12.sp,
                color = GothwadTheme.extra.textTertiary,
            )
        }
    }
}

@Composable
fun ReportAppScreen(vm: StoreViewModel, nav: NavController, appId: String) {
    val state by vm.detail.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var reason by rememberSaveable { mutableStateOf(REPORT_REASONS.first()) }
    var details by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(appId) { if (state !is UiState.Ready) vm.openApp(appId) }

    GothwadScaffold(
        topBar = { GothwadTopBar(title = "Report an app", onBack = { nav.popBackStack() }) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = Spacing.screenH,
                end = Spacing.screenH,
                top = 12.dp,
                bottom = Spacing.bottomContentPadding,
            ),
        ) {
            item {
                InfoBanner(
                    title = "Reports go to the Kiwi Store team",
                    message = "Every report is stored with your account so reviewers can follow up. " +
                        "The app stays available until a reviewer decides.",
                )
            }
            item { com.gothwad.store.ui.components.SectionHeader(title = "Why are you reporting this?") }
            items(REPORT_REASONS) { option ->
                RadioSettingRow(
                    title = option,
                    selected = reason == option,
                    onClick = { reason = option },
                )
            }
            item {
                Column(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = details,
                        onValueChange = { if (it.length <= 600) details = it },
                        label = { Text("Extra details (optional)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))
                    if (error != null) {
                        InfoBanner(title = "Report failed", message = error.orEmpty(), tone = BannerTone.Error)
                        Spacer(Modifier.height(12.dp))
                    }
                    PrimaryButton(
                        label = if (busy) "Sending…" else "Send report",
                        enabled = !busy,
                        onClick = {
                            busy = true
                            error = null
                            scope.launch {
                                runCatching { AppGraph.store.report(appId, reason, details.trim()) }
                                    .onSuccess {
                                        vm.emit(StoreEvent.Message("Report sent. Thank you."))
                                        nav.popBackStack()
                                    }
                                    .onFailure { failure ->
                                        error = if (failure is StoreException && failure.kind == StoreException.Kind.UNAUTHORIZED) {
                                            "Sign in with Gothwad before reporting an app."
                                        } else {
                                            failure.message ?: "The Store could not accept the report."
                                        }
                                    }
                                busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
