package com.example.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.auth.AuthActionState
import com.example.auth.AuthSessionManager
import com.example.auth.AuthenticatedUser
import com.example.core.firebase.AnalyticsTracker
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightVoid

@Composable
fun AuthGateScreen(
    onAuthenticated: (AuthenticatedUser) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
                // Kullanıcı Google ile Giriş Yap butonunu kullanır
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
                        colors = listOf(Color(0xFF02040D), DeepNavy, Color(0xFF04091A))
                    )
                )
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            AmbientPerspectiveGrid(modifier = Modifier.fillMaxSize())

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .size(144.dp)
                            .shadow(
                                elevation = 24.dp,
                                shape = RoundedCornerShape(34.dp),
                                ambientColor = ElectricCyan.copy(alpha = 0.45f),
                                spotColor = ElectricCyan.copy(alpha = 0.55f)
                            )
                            .clip(RoundedCornerShape(34.dp))
                            .border(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        ElectricCyan.copy(alpha = 0.85f),
                                        Color(0xFF2563EB).copy(alpha = 0.55f)
                                    )
                                ),
                                shape = RoundedCornerShape(34.dp)
                            )
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_rain_cube_brand),
                            contentDescription = stringResource(id = R.string.app_name),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.brand_title),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 36.sp,
                                letterSpacing = 3.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = stringResource(id = R.string.auth_headline),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = stringResource(id = R.string.auth_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
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
                                verticalAlignment = Alignment.CenterVertically
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
                            .height(58.dp)
                            .testTag("google_sign_in_button"),
                        shape = RoundedCornerShape(16.dp),
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
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmbientPerspectiveGrid(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val horizonY = size.height * 0.72f
        val gridColor = ElectricCyan.copy(alpha = 0.08f)
        val glowCenter = Offset(size.width * 0.5f, size.height * 0.34f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ElectricCyan.copy(alpha = 0.16f),
                    Color(0xFF1D4ED8).copy(alpha = 0.06f),
                    Color.Transparent
                ),
                center = glowCenter,
                radius = size.minDimension * 0.65f
            ),
            center = glowCenter,
            radius = size.minDimension * 0.65f
        )

        val columns = 7
        for (i in 0..columns) {
            val fraction = i / columns.toFloat()
            val topX = size.width * (0.2f + fraction * 0.6f)
            val bottomX = size.width * (-0.3f + fraction * 1.6f)
            drawLine(
                color = gridColor,
                start = Offset(topX, horizonY),
                end = Offset(bottomX, size.height),
                strokeWidth = 1.5f
            )
        }

        val rows = 5
        for (j in 0..rows) {
            val t = (j / rows.toFloat())
            val y = horizonY + (size.height - horizonY) * (t * t)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5f
            )
        }
    }
}
