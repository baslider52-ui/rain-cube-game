package com.example.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.firebase.AnalyticsTracker
import com.example.game.FallingEntityKind
import com.example.game.GameMode
import com.example.game.GameStatus
import com.example.game.RainCubeGameState
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.MidnightVoid
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.PendingAmberContainer
import com.example.ui.theme.SkyPulse
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldContainer

@Composable
fun RainCubeGameScreen(
    viewModel: RainCubeGameViewModel,
    bestPreviousScore: Long,
    onExitToHub: () -> Unit
) {
    val context = LocalContext.current
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val textMeasurer = rememberTextMeasurer()

    BackHandler {
        when (gameState.status) {
            GameStatus.RUNNING -> viewModel.pause()
            GameStatus.PAUSED, GameStatus.READY, GameStatus.GAME_OVER -> onExitToHub()
        }
    }

    LaunchedEffect(gameState.mode) {
        runCatching { AnalyticsTracker(context).logGameStarted(gameState.mode.id) }
    }

    LaunchedEffect(gameState.status) {
        if (gameState.status == GameStatus.GAME_OVER) {
            runCatching {
                AnalyticsTracker(context).logGameFinished(
                    mode = gameState.mode.id,
                    score = gameState.score,
                    wave = gameState.wave,
                    durationMs = gameState.durationMs
                )
            }
        }
    }

    LaunchedEffect(gameState.status) {
        if (gameState.status != GameStatus.RUNNING) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val deltaSeconds = (now - lastFrameNanos) / 1_000_000_000f
            lastFrameNanos = now
            viewModel.stepFrame(deltaSeconds)
        }
    }

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
                .padding(innerPadding)
        ) {
            // Ana 60fps Oyun Alanı + Dokunmatik Sürükleme Kontrolü
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("rain_cube_arena_canvas")
                    .pointerInput(gameState.status) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (gameState.status == GameStatus.RUNNING && size.width > 0) {
                                viewModel.movePlayerBy(dragAmount.x / size.width.toFloat())
                            }
                        }
                    }
                    .pointerInput(gameState.status) {
                        detectTapGestures { offset ->
                            if (gameState.status == GameStatus.RUNNING && size.width > 0) {
                                viewModel.movePlayerTo(offset.x / size.width.toFloat())
                            } else if (gameState.status == GameStatus.READY) {
                                viewModel.startOrResume()
                            }
                        }
                    }
            ) {
                drawArenaGrid(gameState.gridScrollOffset)
                drawTrail(gameState)
                drawEntities(gameState)
                drawPlayerCube(gameState)
                drawParticles(gameState)
                drawFloatingCallouts(gameState, textMeasurer)

                if (gameState.hitFlashAlpha > 0f) {
                    drawRect(
                        color = ErrorCoral.copy(alpha = gameState.hitFlashAlpha * 0.25f),
                        size = size
                    )
                }
            }

            // Üst HUD
            GameHudOverlay(
                state = gameState,
                onPauseClick = {
                    if (gameState.status == GameStatus.RUNNING) {
                        viewModel.pause()
                    } else if (gameState.status == GameStatus.PAUSED) {
                        viewModel.startOrResume()
                    }
                },
                onBackClick = {
                    if (gameState.status == GameStatus.RUNNING) {
                        viewModel.pause()
                    } else {
                        onExitToHub()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )

            // Alt kontrol ipucu (ilk saniyeler)
            if (gameState.status == GameStatus.RUNNING && gameState.elapsedSeconds < 4.5f) {
                Surface(
                    color = DeepNavy.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 18.dp)
                ) {
                    Text(
                        text = "Küpü yönlendirmek için parmağını sağa/sola kaydır",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ElectricCyan,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Duraklatma Ekranı
            if (gameState.status == GameStatus.PAUSED) {
                PauseOverlay(
                    onResume = { viewModel.startOrResume() },
                    onRestart = { viewModel.restart() },
                    onExit = onExitToHub
                )
            }

            // Oyun Bitti Ekranı + Bulut Kayıt Durumu
            if (gameState.status == GameStatus.GAME_OVER) {
                GameOverOverlay(
                    state = gameState,
                    saveState = saveState,
                    isNewRecord = gameState.score > bestPreviousScore && gameState.score > 0L,
                    onPlayAgain = { viewModel.restart() },
                    onRetrySave = { viewModel.retrySaveMatch() },
                    onExit = onExitToHub
                )
            }
        }
    }
}

@Composable
private fun GameHudOverlay(
    state: RainCubeGameState,
    onPauseClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("game_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(id = R.string.game_back_to_hub),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Column {
                    Text(
                        text = state.score.toString(),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = ElectricCyan
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Dalga ${state.wave}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.combo > 1) {
                            Surface(
                                color = PendingAmberContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "x${state.combo}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = PendingAmber,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (i in 1..state.maxLives) {
                        val alive = i <= state.lives
                        Icon(
                            imageVector = if (alive) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Can $i",
                            tint = if (alive) ErrorCoral else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onPauseClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("game_pause_button")
                ) {
                    Icon(
                        imageVector = if (state.status == GameStatus.PAUSED) {
                            Icons.Outlined.PlayArrow
                        } else {
                            Icons.Outlined.Pause
                        },
                        contentDescription = stringResource(id = R.string.game_pause),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        if (state.hasShield) {
            Surface(
                color = PendingAmberContainer.copy(alpha = 0.9f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = PendingAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "TAÇ KALKANI 2x • ${String.format("%.1f", state.shieldSeconds)}s",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = PendingAmber
                    )
                }
            }
        }
    }
}

@Composable
private fun PauseOverlay(
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightVoid.copy(alpha = 0.78f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = ElevatedSlate),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.game_paused_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Button(
                    onClick = onResume,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("game_resume_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(id = R.string.game_resume))
                }

                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("game_restart_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(id = R.string.game_restart))
                }

                TextButton(
                    onClick = onExit,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("pause_exit_button")
                ) {
                    Text(stringResource(id = R.string.game_back_to_hub))
                }
            }
        }
    }
}

@Composable
private fun GameOverOverlay(
    state: RainCubeGameState,
    saveState: MatchSaveState,
    isNewRecord: Boolean,
    onPlayAgain: () -> Unit,
    onRetrySave: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightVoid.copy(alpha = 0.84f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp)
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(listOf(ElectricCyan, PendingAmber)),
                    shape = RoundedCornerShape(24.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = ElevatedSlate),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.game_over_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (isNewRecord) {
                    Surface(
                        color = PendingAmberContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "YENİ REKOR!",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = PendingAmber,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = state.score.toString(),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 44.sp
                    ),
                    color = ElectricCyan
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ResultMetric("Dalga", "${state.wave}")
                    ResultMetric("Maks Kombo", "x${state.maxCombo}")
                    ResultMetric("Süre", "${state.elapsedSeconds.toInt()} sn")
                }

                // Bulut Kayıt Durumu
                Surface(
                    color = when (saveState) {
                        is MatchSaveState.Saved -> VerifiedEmeraldContainer
                        is MatchSaveState.Error -> PendingAmberContainer
                        else -> DeepNavy
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            when (saveState) {
                                MatchSaveState.Saving, MatchSaveState.Idle -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = ElectricCyan
                                    )
                                    Text(
                                        text = stringResource(id = R.string.game_saving_score),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                is MatchSaveState.Saved -> {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudDone,
                                        contentDescription = null,
                                        tint = VerifiedEmerald,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(id = R.string.game_score_saved),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VerifiedEmerald
                                    )
                                }
                                is MatchSaveState.Error -> {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudOff,
                                        contentDescription = null,
                                        tint = PendingAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(id = R.string.game_score_save_failed),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PendingAmber
                                    )
                                }
                            }
                        }

                        if (saveState is MatchSaveState.Error) {
                            TextButton(
                                onClick = onRetrySave,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text(stringResource(id = R.string.btn_retry), color = ElectricCyan)
                            }
                        }
                    }
                }

                Button(
                    onClick = onPlayAgain,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("play_again_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = stringResource(id = R.string.game_play_again),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("back_to_hub_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(stringResource(id = R.string.game_back_to_hub))
                }
            }
        }
    }
}

@Composable
private fun ResultMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun DrawScope.drawArenaGrid(scrollOffset: Float) {
    val gridColor = ElectricCyan.copy(alpha = 0.09f)
    val cols = 6
    for (i in 0..cols) {
        val x = size.width * (i / cols.toFloat())
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1.2f
        )
    }

    val rows = 10
    val stepY = size.height / rows
    val offsetPx = scrollOffset * stepY
    for (j in -1..rows) {
        val y = j * stepY + offsetPx
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.2f
        )
    }
}

private fun DrawScope.drawTrail(state: RainCubeGameState) {
    val trailColor = if (state.hasShield) PendingAmber else ElectricCyan
    val baseRadius = size.minDimension * state.playerHalfSize * 0.72f
    for (node in state.trail) {
        val cx = node.x * size.width
        val cy = node.y * size.height
        val r = baseRadius * node.radiusScale
        drawRoundRect(
            color = trailColor.copy(alpha = node.alpha * 0.35f),
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2f, r * 2f),
            cornerRadius = CornerRadius(r * 0.45f, r * 0.45f)
        )
    }
}

private fun DrawScope.drawPlayerCube(state: RainCubeGameState) {
    val cx = state.playerX * size.width
    val cy = state.playerY * size.height
    val halfPx = size.minDimension * state.playerHalfSize
    val blinkSkip = state.invulnerableSeconds > 0f &&
        ((state.invulnerableSeconds * 16f).toInt() % 2 == 0)

    if (state.hasShield) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    PendingAmber.copy(alpha = 0.28f),
                    ElectricCyan.copy(alpha = 0.14f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = halfPx * 2.1f
            ),
            center = Offset(cx, cy),
            radius = halfPx * 2.1f
        )
        drawCircle(
            color = PendingAmber.copy(alpha = 0.85f),
            center = Offset(cx, cy),
            radius = halfPx * 1.55f,
            style = Stroke(width = 3.5f)
        )
    }

    if (!blinkSkip) {
        // Neon dış parlama
        drawRoundRect(
            color = ElectricCyan.copy(alpha = 0.28f),
            topLeft = Offset(cx - halfPx * 1.24f, cy - halfPx * 1.24f),
            size = Size(halfPx * 2.48f, halfPx * 2.48f),
            cornerRadius = CornerRadius(halfPx * 0.48f, halfPx * 0.48f)
        )

        // Ana küp gövdesi
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF67E8F9), ElectricCyan, Color(0xFF0284C7)),
                start = Offset(cx - halfPx, cy - halfPx),
                end = Offset(cx + halfPx, cy + halfPx)
            ),
            topLeft = Offset(cx - halfPx, cy - halfPx),
            size = Size(halfPx * 2f, halfPx * 2f),
            cornerRadius = CornerRadius(halfPx * 0.36f, halfPx * 0.36f)
        )

        // İç parlak çekirdek
        drawRoundRect(
            color = Color.White.copy(alpha = 0.72f),
            topLeft = Offset(cx - halfPx * 0.42f, cy - halfPx * 0.42f),
            size = Size(halfPx * 0.84f, halfPx * 0.84f),
            cornerRadius = CornerRadius(halfPx * 0.22f, halfPx * 0.22f)
        )
    }
}

private fun DrawScope.drawEntities(state: RainCubeGameState) {
    for (entity in state.entities) {
        val cx = entity.x * size.width
        val cy = entity.y * size.height
        val half = size.minDimension * entity.size * 0.5f

        when (entity.kind) {
            FallingEntityKind.HAZARD -> {
                rotate(degrees = entity.rotationDeg, pivot = Offset(cx, cy)) {
                    drawRoundRect(
                        color = ErrorCoral.copy(alpha = 0.25f),
                        topLeft = Offset(cx - half * 1.25f, cy - half * 1.25f),
                        size = Size(half * 2.5f, half * 2.5f),
                        cornerRadius = CornerRadius(half * 0.35f, half * 0.35f)
                    )
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFFB7185), Color(0xFFE11D48)),
                            start = Offset(cx - half, cy - half),
                            end = Offset(cx + half, cy + half)
                        ),
                        topLeft = Offset(cx - half, cy - half),
                        size = Size(half * 2f, half * 2f),
                        cornerRadius = CornerRadius(half * 0.28f, half * 0.28f)
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.8f),
                        start = Offset(cx - half * 0.45f, cy - half * 0.45f),
                        end = Offset(cx + half * 0.45f, cy + half * 0.45f),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.8f),
                        start = Offset(cx + half * 0.45f, cy - half * 0.45f),
                        end = Offset(cx - half * 0.45f, cy + half * 0.45f),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                }
            }

            FallingEntityKind.ENERGY -> {
                rotate(degrees = entity.rotationDeg, pivot = Offset(cx, cy)) {
                    drawRoundRect(
                        color = SkyPulse.copy(alpha = 0.30f),
                        topLeft = Offset(cx - half * 1.3f, cy - half * 1.3f),
                        size = Size(half * 2.6f, half * 2.6f),
                        cornerRadius = CornerRadius(half * 0.4f, half * 0.4f)
                    )
                    drawRoundRect(
                        color = ElectricCyan,
                        topLeft = Offset(cx - half, cy - half),
                        size = Size(half * 2f, half * 2f),
                        cornerRadius = CornerRadius(half * 0.35f, half * 0.35f)
                    )
                }
            }

            FallingEntityKind.CROWN -> {
                drawCircle(
                    color = PendingAmber.copy(alpha = 0.24f),
                    center = Offset(cx, cy),
                    radius = half * 1.45f
                )
                val crownPath = Path().apply {
                    moveTo(cx - half * 0.9f, cy + half * 0.65f)
                    lineTo(cx - half * 0.95f, cy - half * 0.45f)
                    lineTo(cx - half * 0.35f, cy + half * 0.1f)
                    lineTo(cx, cy - half * 0.75f)
                    lineTo(cx + half * 0.35f, cy + half * 0.1f)
                    lineTo(cx + half * 0.95f, cy - half * 0.45f)
                    lineTo(cx + half * 0.9f, cy + half * 0.65f)
                    close()
                }
                drawPath(path = crownPath, color = PendingAmber)
            }
        }
    }
}

private fun DrawScope.drawParticles(state: RainCubeGameState) {
    for (p in state.particles) {
        val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
        val color = when {
            p.isDanger -> ErrorCoral
            p.isGolden -> PendingAmber
            else -> ElectricCyan
        }
        drawCircle(
            color = color.copy(alpha = alpha),
            center = Offset(p.x * size.width, p.y * size.height),
            radius = 5.5f * alpha + 2f
        )
    }
}

private fun DrawScope.drawFloatingCallouts(
    state: RainCubeGameState,
    textMeasurer: TextMeasurer
) {
    for (c in state.callouts) {
        val alpha = (c.life / 1.0f).coerceIn(0f, 1f)
        val color = when {
            c.isDanger -> ErrorCoral
            c.isGolden -> PendingAmber
            else -> ElectricCyan
        }
        val measured = textMeasurer.measure(
            text = c.text,
            style = TextStyle(
                color = color.copy(alpha = alpha),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        )
        val x = (c.x * size.width - measured.size.width / 2f)
            .coerceIn(12f, (size.width - measured.size.width - 12f).coerceAtLeast(12f))
        val y = (c.y * size.height - measured.size.height / 2f)
            .coerceIn(12f, (size.height - measured.size.height - 12f).coerceAtLeast(12f))
        drawText(textLayoutResult = measured, topLeft = Offset(x, y))
    }
}
