package com.example.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.auth.AuthActionState
import com.example.auth.AuthSessionManager
import com.example.auth.AuthenticatedUser
import com.example.core.config.InfrastructureConfig
import com.example.core.firebase.AnalyticsTracker
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.MidnightVoid
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.PendingAmberContainer
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldContainer

@Composable
fun AuthGateScreen(
    onAuthenticated: (AuthenticatedUser) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configSnapshot = remember(context) { InfrastructureConfig.load(context) }
    var actionState by remember { mutableStateOf<AuthActionState>(AuthActionState.Idle) }

    LaunchedEffect(Unit) {
        AuthSessionManager.attemptSilentSignIn(
            context = context,
            scope = scope,
            onAuthSuccess = { user ->
                runCatching { AnalyticsTracker(context).logGoogleSignInSuccess(user.uid) }
                onAuthenticated(user)
            },
            onUnauthenticated = {
                // Kullanıcı manuel olarak Google ile Giriş Yap butonunu kullanabilir
            }
        )
    }

    Scaffold(
        containerColor = MidnightVoid
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DeepNavy, MidnightVoid)
                    )
                )
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(24.dp)
                        )
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.rain_cube_icon_fg_1794665016337),
                        contentDescription = stringResource(id = R.string.app_name),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "FAZ 1 · ALTYAPI DOĞRULAMA",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = stringResource(id = R.string.auth_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = stringResource(id = R.string.auth_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

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
                        ConfigCheckRow(
                            label = "Paket Kimliği",
                            value = configSnapshot.applicationId,
                            isReady = true
                        )
                        ConfigCheckRow(
                            label = "Firebase Uygulaması",
                            value = if (configSnapshot.isFirebaseAppConfigured) "Bağlı" else "Eksik",
                            isReady = configSnapshot.isFirebaseAppConfigured
                        )
                        ConfigCheckRow(
                            label = "Google OAuth Web Client",
                            value = if (configSnapshot.isWebClientIdConfigured) "Hazır" else "Eksik",
                            isReady = configSnapshot.isWebClientIdConfigured
                        )
                        ConfigCheckRow(
                            label = "Firestore Veritabanı ID",
                            value = if (configSnapshot.firestoreDatabaseId != null) "Yapılandırıldı" else "Eksik",
                            isReady = configSnapshot.firestoreDatabaseId != null
                        )
                    }
                }

                AnimatedVisibility(visible = actionState is AuthActionState.Error) {
                    val errorMessage = (actionState as? AuthActionState.Error)?.message.orEmpty()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_error_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = "Giriş hatası",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        actionState = AuthActionState.Loading
                        AuthSessionManager.signInWithGoogle(
                            context = context,
                            scope = scope,
                            onAuthSuccess = { user ->
                                actionState = AuthActionState.Idle
                                runCatching { AnalyticsTracker(context).logGoogleSignInSuccess(user.uid) }
                                onAuthenticated(user)
                            },
                            onAuthCancelled = { cancelMessage ->
                                actionState = AuthActionState.Error(cancelMessage)
                            },
                            onAuthError = { errorMessage ->
                                actionState = AuthActionState.Error(errorMessage)
                            }
                        )
                    },
                    enabled = actionState !is AuthActionState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("google_sign_in_button"),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (actionState is AuthActionState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(
                            text = stringResource(id = R.string.auth_signing_in),
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        Text(
                            text = stringResource(id = R.string.auth_sign_in_google),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigCheckRow(
    label: String,
    value: String,
    isReady: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            color = if (isReady) VerifiedEmeraldContainer else PendingAmberContainer,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isReady) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = if (isReady) VerifiedEmerald else PendingAmber,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isReady) VerifiedEmerald else PendingAmber
                )
            }
        }
    }
}
