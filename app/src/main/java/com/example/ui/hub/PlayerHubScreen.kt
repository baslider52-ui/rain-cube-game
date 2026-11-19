package com.example.ui.hub

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.R
import com.example.auth.AuthSessionManager
import com.example.auth.AuthenticatedUser
import com.example.core.firebase.AnalyticsTracker
import com.example.data.model.MatchRecord
import com.example.game.GameMode
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.MidnightVoid
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.PendingAmberContainer
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldContainer
import com.example.update.GitHubUpdateChecker

@Composable
fun PlayerHubScreen(
    user: AuthenticatedUser,
    viewModel: PlayerHubViewModel,
    onPlayMode: (GameMode) -> Unit
) {
    val context = LocalContext.current
    var isSigningOut by remember { mutableStateOf(false) }
    val hubState by viewModel.hubState.collectAsStateWithLifecycle()
    val stats by viewModel.playerStats.collectAsStateWithLifecycle()
    val recentMatches by viewModel.recentMatches.collectAsStateWithLifecycle()
    val syncNotice by viewModel.syncNotice.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val repoSlug by viewModel.repoSlug.collectAsStateWithLifecycle()

    val liveProfile = (hubState as? PlayerHubUiState.Ready)?.profile
    val displayName = liveProfile?.displayName?.takeIf { it.isNotBlank() } ?: user.displayName
    val photoUrl = liveProfile?.photoUrl?.takeIf { it.isNotBlank() } ?: user.photoUrl

    val displayBestScore = maxOf(
        stats.bestScore,
        recentMatches.maxOfOrNull { it.score } ?: 0L
    )
    val displayMatchesPlayed = maxOf(
        stats.matchesPlayed,
        recentMatches.size.toLong()
    )

    Scaffold(
        containerColor = MidnightVoid
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF02040D), DeepNavy, MidnightVoid)
                    )
                )
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_rain_cube_brand),
                                contentDescription = stringResource(id = R.string.app_name),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Column {
                                Text(
                                    text = stringResource(id = R.string.brand_title),
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        letterSpacing = 1.8.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = GitHubUpdateChecker.formatVersionTag(BuildConfig.VERSION_NAME),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    when (val currentUpdate = updateState) {
                                        is UpdateUiState.UpdateAvailable -> {
                                            Surface(
                                                color = PendingAmberContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "Yeni: ${GitHubUpdateChecker.formatVersionTag(currentUpdate.release.tagName)}",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = PendingAmber,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        is UpdateUiState.UpToDate -> {
                                            Surface(
                                                color = VerifiedEmeraldContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(id = R.string.update_up_to_date),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = VerifiedEmerald,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        else -> Unit
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                if (!isSigningOut) {
                                    isSigningOut = true
                                    runCatching { AnalyticsTracker(context).logSignOut() }
                                    AuthSessionManager.signOut(
                                        context = context,
                                        onSignOutComplete = { isSigningOut = false }
                                    )
                                }
                            },
                            enabled = !isSigningOut,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = stringResource(id = R.string.btn_sign_out),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Player Profile Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (!photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, ElectricCyan, CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(ElevatedSlate)
                                        .border(1.5.dp, ElectricCyan.copy(alpha = 0.6f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Person,
                                        contentDescription = displayName,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    color = VerifiedEmeraldContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Verified,
                                            contentDescription = null,
                                            tint = VerifiedEmerald,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = stringResource(id = R.string.hub_status_ready),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VerifiedEmerald
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                syncNotice?.let { notice ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = PendingAmberContainer
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = notice,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PendingAmber,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { viewModel.syncPlayerProfile() },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("sync_retry_button")
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.btn_retry),
                                        color = ElectricCyan
                                    )
                                }
                            }
                        }
                    }
                }

                // Oynanabilir Modlar
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(id = R.string.hub_modes_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        // Klasik Arena (Ana Mod)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.5.dp,
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            ElectricCyan.copy(alpha = 0.85f),
                                            PendingAmber.copy(alpha = 0.45f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(22.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = ElevatedSlate
                            ),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.hub_classic_title),
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        color = VerifiedEmeraldContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "3 CAN • 1x",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = VerifiedEmerald,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = stringResource(id = R.string.hub_classic_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = { onPlayMode(GameMode.CLASSIC) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("play_classic_button"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PlayArrow,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text(
                                        text = stringResource(id = R.string.hub_play_now),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        // Hız Fırtınası Modu
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = PendingAmber.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(20.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.FlashOn,
                                            contentDescription = null,
                                            tint = PendingAmber
                                        )
                                        Text(
                                            text = stringResource(id = R.string.hub_rush_title),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Surface(
                                        color = PendingAmberContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "2 CAN • 2x SKOR",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = PendingAmber,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = stringResource(id = R.string.hub_rush_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                OutlinedButton(
                                    onClick = { onPlayMode(GameMode.RUSH) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("play_rush_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(
                                        text = "Fırtınaya Gir",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = PendingAmber
                                    )
                                }
                            }
                        }
                    }
                }

                // Player Summary Stats Grid (2x2)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(id = R.string.hub_stats_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatTile(
                                icon = Icons.Outlined.StarOutline,
                                label = "Seviye",
                                value = "Lv ${stats.level}",
                                accent = ElectricCyan,
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                icon = Icons.Outlined.Bolt,
                                label = "Toplam XP",
                                value = "${stats.xp}",
                                accent = PendingAmber,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatTile(
                                icon = Icons.Outlined.EmojiEvents,
                                label = "En Yüksek Skor",
                                value = "$displayBestScore",
                                accent = VerifiedEmerald,
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                icon = Icons.Outlined.SportsEsports,
                                label = "Oynanan Maç",
                                value = "$displayMatchesPlayed",
                                accent = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Sürüm ve GitHub Güncellemeleri
                item {
                    GitHubUpdateCard(
                        updateState = updateState,
                        repoSlug = repoSlug,
                        onCheckUpdates = { viewModel.checkForUpdates() },
                        onConnectRepo = { input -> viewModel.connectAndCheckRepo(input) },
                        onOpenUrl = { url -> openExternalUrl(context, url) }
                    )
                }

                // Son Maçlar
                item {
                    Text(
                        text = stringResource(id = R.string.hub_recent_matches_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (recentMatches.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.hub_recent_matches_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                } else {
                    items(recentMatches, key = { it.id }) { match ->
                        RecentMatchRow(match = match)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentMatchRow(match: MatchRecord) {
    val modeLabel = if (match.mode == GameMode.RUSH.id) {
        stringResource(id = R.string.hub_rush_title)
    } else {
        stringResource(id = R.string.hub_classic_title)
    }
    val durationSec = (match.durationMs / 1000L).coerceAtLeast(1L)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "$durationSec sn • ${GitHubUpdateChecker.formatVersionTag(match.clientVersion)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "${match.score} puan",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = ElectricCyan
            )
        }
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GitHubUpdateCard(
    updateState: UpdateUiState,
    repoSlug: String,
    onCheckUpdates: () -> Unit,
    onConnectRepo: (String) -> Boolean,
    onOpenUrl: (String) -> Unit
) {
    var isEditingRepo by remember(updateState is UpdateUiState.NeedsRepoConfig) {
        mutableStateOf(updateState is UpdateUiState.NeedsRepoConfig)
    }
    var repoInput by remember(repoSlug) { mutableStateOf(repoSlug) }

    LaunchedEffect(updateState) {
        if (updateState is UpdateUiState.UpdateAvailable ||
            updateState is UpdateUiState.UpToDate ||
            updateState is UpdateUiState.NoReleaseYet
        ) {
            isEditingRepo = false
        }
    }

    val hasAvailableUpdate = updateState is UpdateUiState.UpdateAvailable

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("github_update_card")
            .then(
                if (hasAvailableUpdate) {
                    Modifier.border(
                        width = 1.5.dp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(PendingAmber, ElectricCyan)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = ElectricCyan.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SystemUpdate,
                        contentDescription = null,
                        tint = if (hasAvailableUpdate) PendingAmber else ElectricCyan
                    )
                    Column {
                        Text(
                            text = stringResource(id = R.string.update_section_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val installedLabel = GitHubUpdateChecker.formatVersionTag(BuildConfig.VERSION_NAME)
                        val subtitle = if (repoSlug.isNotBlank()) {
                            "$installedLabel • $repoSlug"
                        } else {
                            "Yüklü Sürüm: $installedLabel"
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (repoSlug.isNotBlank()) {
                    TextButton(
                        onClick = { isEditingRepo = !isEditingRepo },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("edit_repo_button")
                    ) {
                        Text(
                            text = stringResource(id = R.string.update_edit_repo),
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                }
            }

            if (isEditingRepo || updateState is UpdateUiState.NeedsRepoConfig) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(id = R.string.update_connect_repo),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = repoInput,
                        onValueChange = { repoInput = it },
                        singleLine = true,
                        label = { Text(stringResource(id = R.string.update_repo_hint)) },
                        placeholder = { Text("kullanici/rain-cube") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_repo_input")
                    )
                    Button(
                        onClick = {
                            if (onConnectRepo(repoInput)) {
                                isEditingRepo = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_repo_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(stringResource(id = R.string.update_save_and_check))
                    }
                }
            }

            when (updateState) {
                UpdateUiState.NeedsRepoConfig -> Unit

                is UpdateUiState.Checking -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = ElectricCyan
                        )
                        Text(
                            text = stringResource(id = R.string.update_checking),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is UpdateUiState.UpdateAvailable -> {
                    val release = updateState.release
                    Surface(
                        color = PendingAmberContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${stringResource(id = R.string.update_available_badge)}: ${GitHubUpdateChecker.formatVersionTag(release.tagName)}",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = PendingAmber
                            )
                            val sizeLabel = GitHubUpdateChecker.formatApkSize(release.apkSizeBytes)
                            if (sizeLabel.isNotBlank()) {
                                Text(
                                    text = sizeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PendingAmber
                                )
                            }
                        }
                    }

                    if (!release.apkFileName.isNullOrBlank()) {
                        Text(
                            text = "APK: ${release.apkFileName}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = ElectricCyan
                        )
                    }

                    if (release.releaseNotes.isNotBlank()) {
                        Text(
                            text = release.releaseNotes.replace("#", "").trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = {
                            onOpenUrl(release.apkDownloadUrl ?: release.releasePageUrl)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("download_update_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PendingAmber,
                            contentColor = MidnightVoid
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "${stringResource(id = R.string.update_download_install)} (${GitHubUpdateChecker.formatVersionTag(release.tagName)})",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onOpenUrl(release.releasePageUrl) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("open_releases_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = stringResource(id = R.string.update_open_releases),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = onCheckUpdates,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("check_update_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(id = R.string.update_check_now),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                is UpdateUiState.UpToDate -> {
                    val release = updateState.release
                    Surface(
                        color = VerifiedEmeraldContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Verified,
                                contentDescription = null,
                                tint = VerifiedEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "En güncel sürümü kullanıyorsunuz (${GitHubUpdateChecker.formatVersionTag(release.tagName)}).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VerifiedEmerald
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCheckUpdates,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("check_update_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = "Yeniden Kontrol Et",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                onOpenUrl(release.apkDownloadUrl ?: release.releasePageUrl)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("open_releases_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = "APK İndir",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                is UpdateUiState.NoReleaseYet -> {
                    Text(
                        text = "GitHub deposunda (${updateState.repoSlug}) henüz yayınlanmış bir APK sürümü bulunamadı.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCheckUpdates,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("check_update_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(stringResource(id = R.string.update_check_now))
                        }
                        OutlinedButton(
                            onClick = {
                                onOpenUrl(GitHubUpdateChecker.releasesPageUrl(updateState.repoSlug))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("open_releases_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(stringResource(id = R.string.update_open_releases))
                        }
                    }
                }

                is UpdateUiState.Error -> {
                    Text(
                        text = updateState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PendingAmber
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCheckUpdates,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("check_update_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(stringResource(id = R.string.btn_retry))
                        }
                        if (repoSlug.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    onOpenUrl(GitHubUpdateChecker.releasesPageUrl(repoSlug))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("open_releases_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(stringResource(id = R.string.update_open_releases))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openExternalUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(
            context,
            "Bağlantı açılamadı. Lütfen tarayıcı ayarlarınızı kontrol edin.",
            Toast.LENGTH_SHORT
        ).show()
    }
}
