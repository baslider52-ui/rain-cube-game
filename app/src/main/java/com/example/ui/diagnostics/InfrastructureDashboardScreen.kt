package com.example.ui.diagnostics

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Games
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.auth.AuthSessionManager
import com.example.auth.AuthenticatedUser
import com.example.core.firebase.AnalyticsTracker
import com.example.engine.EngineArchitectureInfo
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.ErrorCoralContainer
import com.example.ui.theme.MidnightVoid
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.PendingAmberContainer
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldContainer

@Composable
fun InfrastructureDashboardScreen(
    user: AuthenticatedUser,
    viewModel: InfrastructureViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val verificationState by viewModel.verificationState.collectAsStateWithLifecycle()
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val profileSyncError by viewModel.profileSyncError.collectAsStateWithLifecycle()
    val diagnosticsHistory by viewModel.diagnosticsHistory.collectAsStateWithLifecycle()
    val playerStats by viewModel.playerStats.collectAsStateWithLifecycle()
    val analyticsStatus by viewModel.analyticsStatus.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MidnightVoid
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DeepNavy, MidnightVoid)))
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(id = R.string.dashboard_title),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(id = R.string.dashboard_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 1. Authenticated User & UID Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (!user.photoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = user.photoUrl,
                                        contentDescription = user.displayName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .border(
                                                1.5.dp,
                                                MaterialTheme.colorScheme.primary,
                                                CircleShape
                                            )
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(ElevatedSlate),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Person,
                                            contentDescription = user.displayName,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    user.email?.let { email ->
                                        Text(
                                            text = email,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                StatusPill(
                                    text = "Auth Aktif",
                                    tone = StatusTone.VERIFIED
                                )
                            }

                            Surface(
                                color = MidnightVoid,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Firebase UID",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = user.uid,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("user_uid_text")
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    runCatching { AnalyticsTracker(context).logSignOut() }
                                    AuthSessionManager.signOut(
                                        context = context,
                                        scope = scope
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("sign_out_button"),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(text = stringResource(id = R.string.btn_sign_out))
                            }
                        }
                    }
                }

                // 2. Real Firestore Write/Read Connectivity Card
                item {
                    SectionCard(
                        icon = Icons.Outlined.Storage,
                        title = "Cloud Firestore Bağlantı Testi",
                        badgeText = when (verificationState) {
                            is VerificationState.Verified -> "Doğrulandı"
                            is VerificationState.Failed -> "Hata"
                            is VerificationState.Running -> "Test Ediliyor"
                            VerificationState.NotTestedYet -> "Test Bekliyor"
                        },
                        badgeTone = when (verificationState) {
                            is VerificationState.Verified -> StatusTone.VERIFIED
                            is VerificationState.Failed -> StatusTone.ERROR
                            is VerificationState.Running,
                            VerificationState.NotTestedYet -> StatusTone.PENDING
                        }
                    ) {
                        val dbId = viewModel.configSnapshot.firestoreDatabaseId ?: "Tanımsız"
                        DetailLine(label = "Veritabanı ID", value = dbId)

                        val profileStatusText = when (val p = profileState) {
                            is ProfileSyncState.Syncing -> "Senkronize ediliyor…"
                            is ProfileSyncState.Ready ->
                                if (p.profile != null) "Profil /users/{uid} aktif" else "Belge bekleniyor"
                            is ProfileSyncState.Error -> "Hata: ${p.message}"
                        }
                        DetailLine(label = "Profil Senkronizasyonu", value = profileStatusText)

                        profileSyncError?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ErrorCoral
                            )
                        }

                        when (val v = verificationState) {
                            is VerificationState.Verified -> {
                                Surface(
                                    color = VerifiedEmeraldContainer,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Sunucu yazma & okuma (Source.SERVER) başarılı (${v.result.roundTripMs} ms)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = VerifiedEmerald
                                        )
                                        Text(
                                            text = "Belge ID: ${v.result.check.id}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            is VerificationState.Failed -> {
                                Surface(
                                    color = ErrorCoralContainer,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CloudOff,
                                            contentDescription = null,
                                            tint = ErrorCoral
                                        )
                                        Text(
                                            text = v.errorMessage,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }

                            else -> Unit
                        }

                        if (diagnosticsHistory.isNotEmpty()) {
                            Text(
                                text = "Kayıtlı doğrulama sayısı: ${diagnosticsHistory.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { viewModel.runFirestoreVerificationTest() },
                            enabled = verificationState !is VerificationState.Running,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("run_firestore_test_button"),
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (verificationState is VerificationState.Running) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(text = stringResource(id = R.string.btn_running_firestore_test))
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.CloudDone,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(text = stringResource(id = R.string.btn_run_firestore_test))
                            }
                        }
                    }
                }

                // 3. Firebase Analytics Card
                item {
                    SectionCard(
                        icon = Icons.Outlined.Analytics,
                        title = "Firebase Analytics",
                        badgeText = "SDK Aktif",
                        badgeTone = StatusTone.VERIFIED
                    ) {
                        Text(
                            text = analyticsStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { viewModel.sendAnalyticsPing() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("log_analytics_button"),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(text = stringResource(id = R.string.btn_log_analytics_test))
                        }
                    }
                }

                // 4. Security & Cloud Functions Schema Readiness Card
                item {
                    SectionCard(
                        icon = Icons.Outlined.Security,
                        title = "Firestore Şema & Sunucu Güvenliği",
                        badgeText = "Kurallar Hazır",
                        badgeTone = StatusTone.VERIFIED
                    ) {
                        DetailLine(label = "Profil Koleksiyonu", value = "/users/{uid} (Sahip İzolasyonu)")
                        DetailLine(label = "Oyuncu İstatistikleri", value = "/users/{uid}/stats/summary (Cloud Functions)")
                        DetailLine(label = "Genel Sıralama", value = "/leaderboard_global/{uid} (Salt Okunur)")
                        DetailLine(label = "Haftalık Sıralama", value = "/leaderboard_weekly/{weekId}/entries")
                        DetailLine(label = "Oyun Kayıtları", value = "/match_records/{matchId} (Sunucu Doğrulamalı)")
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        DetailLine(
                            label = "Sunucu Onaylı XP / Seviye",
                            value = playerStats?.let { "XP ${it.xp} · Lv ${it.level}" }
                                ?: "Henüz Cloud Functions verisi yok (beklenen)"
                        )
                    }
                }

                // 5. Android & 2D Engine Readiness Card
                item {
                    SectionCard(
                        icon = Icons.Outlined.Games,
                        title = "Android & 2D Oyun Altyapısı",
                        badgeText = "Hazır",
                        badgeTone = StatusTone.VERIFIED
                    ) {
                        DetailLine(label = "Application ID", value = viewModel.configSnapshot.applicationId)
                        DetailLine(label = "Sürüm", value = "${viewModel.configSnapshot.versionName} (${viewModel.configSnapshot.versionCode})")
                        DetailLine(label = "Ekran Yönü", value = EngineArchitectureInfo.ORIENTATION_LOCK)
                        DetailLine(label = "2D Motor Katmanı", value = EngineArchitectureInfo.RENDER_BACKEND)
                        DetailLine(label = "Hedef Kare Hızı", value = "${EngineArchitectureInfo.defaultFrameBudget.targetFps} FPS")
                    }
                }

                // 6. GitHub & CI/CD Summary Card
                item {
                    SectionCard(
                        icon = Icons.Outlined.Code,
                        title = "GitHub & Otomatik APK Derleme",
                        badgeText = "Workflow Hazır",
                        badgeTone = StatusTone.PENDING
                    ) {
                        DetailLine(label = "Hedef Repo", value = "rain-cube-game (branch: main)")
                        DetailLine(label = "CI Pipeline", value = ".github/workflows/android-ci.yml")
                        DetailLine(label = "Release Pipeline", value = ".github/workflows/release-apk.yml")
                        Text(
                            text = "Not: Hassas Firebase dosyaları (.env, google-services.json, keystore) .gitignore ile korunmaktadır. Uzak GitHub push işlemi için kişisel GitHub yetkilendirmeniz gerekir.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private enum class StatusTone {
    VERIFIED,
    PENDING,
    ERROR
}

@Composable
private fun StatusPill(
    text: String,
    tone: StatusTone
) {
    val bgColor = when (tone) {
        StatusTone.VERIFIED -> VerifiedEmeraldContainer
        StatusTone.PENDING -> PendingAmberContainer
        StatusTone.ERROR -> ErrorCoralContainer
    }
    val fgColor = when (tone) {
        StatusTone.VERIFIED -> VerifiedEmerald
        StatusTone.PENDING -> PendingAmber
        StatusTone.ERROR -> ErrorCoral
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (tone == StatusTone.VERIFIED) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = fgColor,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = fgColor
            )
        }
    }
}

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    badgeText: String,
    badgeTone: StatusTone,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                StatusPill(text = badgeText, tone = badgeTone)
            }
            content()
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
