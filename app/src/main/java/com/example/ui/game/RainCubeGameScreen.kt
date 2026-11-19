package com.example.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.outlined.Group
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import java.util.Locale
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun RainCubeGameScreen(
    viewModel: RainCubeGameViewModel,
    bestPreviousScore: Long,
    onExitToHub: () -> Unit
) {
    val context = LocalContext.current
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val remotePlayers by viewModel.remotePlayers.collectAsStateWithLifecycle()
    val textMeasurer = rememberTextMeasurer()

    BackHandler {
        when (gameState.status) {
            GameStatus.RUNNING -> viewModel.pause()
            GameStatus.PAUSED, GameStatus.READY, GameStatus.GAME_OVER -> {
                viewModel.leaveArenaNow()
                onExitToHub()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.leaveArenaNow()
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
            // Ana 60fps Sonsuz Harita Canvas'ı (Çevrimiçi oyuncular + İsim etiketleri)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("rain_cube_arena_canvas")
            ) {
                val pixelsPerUnit = size.minDimension.coerceAtLeast(1f)
                drawInfiniteArenaGrid(
                    cameraX = gameState.cameraX,
                    cameraY = gameState.cameraY,
                    pixelsPerUnit = pixelsPerUnit
                )
                drawTrail(gameState, pixelsPerUnit)
                drawEntities(gameState, pixelsPerUnit)
                drawRemotePlayers(
                    state = gameState,
                    remotePlayers = remotePlayers,
                    pixelsPerUnit = pixelsPerUnit,
                    textMeasurer = textMeasurer
                )
                drawPlayerCube(
                    state = gameState,
                    displayName = viewModel.currentDisplayName,
                    pixelsPerUnit = pixelsPerUnit,
                    textMeasurer = textMeasurer
                )
                drawParticles(gameState, pixelsPerUnit)
                drawFloatingCallouts(gameState, pixelsPerUnit, textMeasurer)

                if (gameState.hitFlashAlpha > 0f) {
                    drawRect(
                        color = ErrorCoral.copy(alpha = gameState.hitFlashAlpha * 0.25f),
                        size = size
                    )
                }
            }

            // Üst HUD (Skor, Çevrimiçi Oyuncu Sayısı, Harita Koordinatı, Canlar, Duraklat)
            GameHudOverlay(
                state = gameState,
                onlineCount = remotePlayers.size + 1,
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
                        viewModel.leaveArenaNow()
                        onExitToHub()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )

            // Alt Joystik Kontrolü (Oyun aktifken)
            if (gameState.status == GameStatus.RUNNING || gameState.status == GameStatus.READY) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (gameState.elapsedSeconds < 4.5f) {
                        Surface(
                            color = DeepNavy.copy(alpha = 0.84f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.game_joystick_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = ElectricCyan,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }

                    VirtualJoystick(
                        onVectorChange = { dx, dy ->
                            viewModel.setJoystickInput(dx, dy)
                        },
                        modifier = Modifier.testTag("virtual_joystick")
                    )
                }
            }

            // Duraklatma Ekranı
            if (gameState.status == GameStatus.PAUSED) {
                PauseOverlay(
                    onResume = { viewModel.startOrResume() },
                    onRestart = { viewModel.restart() },
                    onExit = {
                        viewModel.leaveArenaNow()
                        onExitToHub()
                    }
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
                    onExit = {
                        viewModel.leaveArenaNow()
                        onExitToHub()
                    }
                )
            }
        }
    }
}

@Composable
private fun VirtualJoystick(
    onVectorChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(136.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val center = Offset(size.width * 0.5f, size.height * 0.5f)
                    val maxRadius = (size.width.coerceAtMost(size.height) * 0.36f).coerceAtLeast(1f)

                    fun updateFromPosition(pos: Offset) {
                        val delta = pos - center
                        val dist = hypot(delta.x, delta.y)
                        val clamped = if (dist > maxRadius && dist > 0f) {
                            delta * (maxRadius / dist)
                        } else {
                            delta
                        }
                        knobOffset = clamped
                        onVectorChange(
                            (clamped.x / maxRadius).coerceIn(-1f, 1f),
                            (clamped.y / maxRadius).coerceIn(-1f, 1f)
                        )
                    }

                    isPressed = true
                    updateFromPosition(down.position)
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                            ?: break
                        if (!change.pressed) break
                        updateFromPosition(change.position)
                        change.consume()
                    }

                    isPressed = false
                    knobOffset = Offset.Zero
                    onVectorChange(0f, 0f)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.5f)
            val outerRadius = size.minDimension * 0.46f
            val maxTravelRadius = size.minDimension * 0.36f
            val knobRadius = size.minDimension * 0.19f

            // Dış neon halka gövdesi
            drawCircle(
                color = MidnightVoid.copy(alpha = 0.65f),
                center = center,
                radius = outerRadius
            )
            drawCircle(
                color = ElectricCyan.copy(alpha = if (isPressed) 0.72f else 0.38f),
                center = center,
                radius = outerRadius,
                style = Stroke(width = if (isPressed) 3.5f else 2.2f)
            )

            // İç yön işaretçileri (Kuzey, Güney, Doğu, Batı)
            val tickInner = outerRadius * 0.74f
            val tickOuter = outerRadius * 0.88f
            val tickColor = ElectricCyan.copy(alpha = 0.42f)
            drawLine(tickColor, Offset(center.x, center.y - tickInner), Offset(center.x, center.y - tickOuter), strokeWidth = 2.5f, cap = StrokeCap.Round)
            drawLine(tickColor, Offset(center.x, center.y + tickInner), Offset(center.x, center.y + tickOuter), strokeWidth = 2.5f, cap = StrokeCap.Round)
            drawLine(tickColor, Offset(center.x - tickInner, center.y), Offset(center.x - tickOuter, center.y), strokeWidth = 2.5f, cap = StrokeCap.Round)
            drawLine(tickColor, Offset(center.x + tickInner, center.y), Offset(center.x + tickOuter, center.y), strokeWidth = 2.5f, cap = StrokeCap.Round)

            // İç rehber çemberi
            drawCircle(
                color = ElectricCyan.copy(alpha = 0.16f),
                center = center,
                radius = maxTravelRadius * 0.55f,
                style = Stroke(width = 1.5f)
            )

            val knobCenter = center + knobOffset

            // Merkezden joystik topuzuna neon ışın çizgisi
            if (isPressed && hypot(knobOffset.x, knobOffset.y) > 4f) {
                drawLine(
                    color = ElectricCyan.copy(alpha = 0.55f),
                    start = center,
                    end = knobCenter,
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
            }

            // Joystik topuzu
            drawCircle(
                color = ElectricCyan.copy(alpha = if (isPressed) 0.32f else 0.18f),
                center = knobCenter,
                radius = knobRadius * 1.32f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFA5F3FC), ElectricCyan, Color(0xFF0284C7)),
                    center = knobCenter,
                    radius = knobRadius
                ),
                center = knobCenter,
                radius = knobRadius
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.65f),
                center = knobCenter,
                radius = knobRadius * 0.42f
            )
        }
    }
}

@Composable
private fun GameHudOverlay(
    state: RainCubeGameState,
    onlineCount: Int,
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
                        val sectorX = (state.playerX * 10f).roundToInt()
                        val sectorY = (-state.playerY * 10f).roundToInt()
                        Text(
                            text = "Dalga ${state.wave} • ($sectorX, $sectorY)",
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
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = VerifiedEmeraldContainer.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("online_players_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Group,
                            contentDescription = null,
                            tint = VerifiedEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(id = R.string.game_online_count, onlineCount),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = VerifiedEmerald
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (i in 1..state.maxLives) {
                        val alive = i <= state.lives
                        Icon(
                            imageVector = if (alive) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Can $i",
                            tint = if (alive) ErrorCoral else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
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
                        text = "TAÇ KALKANI 2x • ${String.format(Locale.US, "%.1f", state.shieldSeconds)}s",
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

private fun DrawScope.worldToScreen(
    wx: Float,
    wy: Float,
    cameraX: Float,
    cameraY: Float,
    pixelsPerUnit: Float
): Offset {
    return Offset(
        x = size.width * 0.5f + (wx - cameraX) * pixelsPerUnit,
        y = size.height * 0.5f + (wy - cameraY) * pixelsPerUnit
    )
}

private fun DrawScope.drawInfiniteArenaGrid(
    cameraX: Float,
    cameraY: Float,
    pixelsPerUnit: Float
) {
    val cellWorldSize = 0.22f
    val halfWorldW = (size.width * 0.5f) / pixelsPerUnit
    val halfWorldH = (size.height * 0.5f) / pixelsPerUnit

    val startCol = floor((cameraX - halfWorldW) / cellWorldSize).toInt() - 1
    val endCol = floor((cameraX + halfWorldW) / cellWorldSize).toInt() + 1
    val startRow = floor((cameraY - halfWorldH) / cellWorldSize).toInt() - 1
    val endRow = floor((cameraY + halfWorldH) / cellWorldSize).toInt() + 1

    val minorColor = ElectricCyan.copy(alpha = 0.08f)
    val majorColor = ElectricCyan.copy(alpha = 0.19f)

    for (col in startCol..endCol) {
        val wx = col * cellWorldSize
        val sx = size.width * 0.5f + (wx - cameraX) * pixelsPerUnit
        val isMajor = col % 4 == 0
        drawLine(
            color = if (isMajor) majorColor else minorColor,
            start = Offset(sx, 0f),
            end = Offset(sx, size.height),
            strokeWidth = if (isMajor) 1.8f else 1.1f
        )
    }

    for (row in startRow..endRow) {
        val wy = row * cellWorldSize
        val sy = size.height * 0.5f + (wy - cameraY) * pixelsPerUnit
        val isMajor = row % 4 == 0
        drawLine(
            color = if (isMajor) majorColor else minorColor,
            start = Offset(0f, sy),
            end = Offset(size.width, sy),
            strokeWidth = if (isMajor) 1.8f else 1.1f
        )
    }
}

private fun DrawScope.drawTrail(state: RainCubeGameState, pixelsPerUnit: Float) {
    val trailColor = if (state.hasShield) PendingAmber else ElectricCyan
    val baseRadius = pixelsPerUnit * state.playerHalfSize * 0.72f
    for (node in state.trail) {
        val pos = worldToScreen(node.x, node.y, state.cameraX, state.cameraY, pixelsPerUnit)
        val r = baseRadius * node.radiusScale
        drawRoundRect(
            color = trailColor.copy(alpha = node.alpha * 0.35f),
            topLeft = Offset(pos.x - r, pos.y - r),
            size = Size(r * 2f, r * 2f),
            cornerRadius = CornerRadius(r * 0.45f, r * 0.45f)
        )
    }
}

private fun DrawScope.drawNameBadgeAboveCube(
    name: String,
    cubeCenterX: Float,
    cubeTopY: Float,
    accentColor: Color,
    textMeasurer: TextMeasurer
) {
    val cleanName = name.trim().take(20).ifEmpty { "Oyuncu" }
    val measured = textMeasurer.measure(
        text = cleanName,
        style = TextStyle(
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    )
    val padH = 12f
    val padV = 4f
    val boxW = measured.size.width + padH * 2f
    val boxH = measured.size.height + padV * 2f
    val left = cubeCenterX - boxW * 0.5f
    val top = cubeTopY - boxH - 10f

    drawRoundRect(
        color = MidnightVoid.copy(alpha = 0.82f),
        topLeft = Offset(left, top),
        size = Size(boxW, boxH),
        cornerRadius = CornerRadius(12f, 12f)
    )
    drawRoundRect(
        color = accentColor.copy(alpha = 0.65f),
        topLeft = Offset(left, top),
        size = Size(boxW, boxH),
        cornerRadius = CornerRadius(12f, 12f),
        style = Stroke(width = 1.6f)
    )
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(left + padH, top + padV)
    )
}

private fun DrawScope.drawRemotePlayers(
    state: RainCubeGameState,
    remotePlayers: List<RemoteArenaPlayer>,
    pixelsPerUnit: Float,
    textMeasurer: TextMeasurer
) {
    val halfPx = pixelsPerUnit * state.playerHalfSize * 0.94f
    val padEdge = 36f

    for (remote in remotePlayers) {
        val pos = worldToScreen(
            wx = remote.renderX,
            wy = remote.renderY,
            cameraX = state.cameraX,
            cameraY = state.cameraY,
            pixelsPerUnit = pixelsPerUnit
        )

        val onScreen = pos.x in (-halfPx * 2f)..(size.width + halfPx * 2f) &&
            pos.y in (-halfPx * 2f)..(size.height + halfPx * 2f)

        if (onScreen) {
            if (remote.shieldActive) {
                drawCircle(
                    color = PendingAmber.copy(alpha = 0.72f),
                    center = pos,
                    radius = halfPx * 1.48f,
                    style = Stroke(width = 3f)
                )
            }

            // Çevrimiçi oyuncu küpü (Zümrüt-Mint neon gövde)
            drawRoundRect(
                color = VerifiedEmerald.copy(alpha = 0.25f),
                topLeft = Offset(pos.x - halfPx * 1.24f, pos.y - halfPx * 1.24f),
                size = Size(halfPx * 2.48f, halfPx * 2.48f),
                cornerRadius = CornerRadius(halfPx * 0.45f, halfPx * 0.45f)
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF6EE7B7), VerifiedEmerald, Color(0xFF059669)),
                    start = Offset(pos.x - halfPx, pos.y - halfPx),
                    end = Offset(pos.x + halfPx, pos.y + halfPx)
                ),
                topLeft = Offset(pos.x - halfPx, pos.y - halfPx),
                size = Size(halfPx * 2f, halfPx * 2f),
                cornerRadius = CornerRadius(halfPx * 0.35f, halfPx * 0.35f)
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.65f),
                topLeft = Offset(pos.x - halfPx * 0.38f, pos.y - halfPx * 0.38f),
                size = Size(halfPx * 0.76f, halfPx * 0.76f),
                cornerRadius = CornerRadius(halfPx * 0.2f, halfPx * 0.2f)
            )

            drawNameBadgeAboveCube(
                name = remote.displayName,
                cubeCenterX = pos.x,
                cubeTopY = pos.y - halfPx,
                accentColor = VerifiedEmerald,
                textMeasurer = textMeasurer
            )
        } else {
            // Ekran dışındaki çevrimiçi oyuncu için kenar göstergesi
            val edgeX = pos.x.coerceIn(padEdge, size.width - padEdge)
            val edgeY = pos.y.coerceIn(padEdge + 96f, size.height - padEdge - 160f)
            drawCircle(
                color = VerifiedEmerald.copy(alpha = 0.85f),
                center = Offset(edgeX, edgeY),
                radius = 9f
            )
            val tag = textMeasurer.measure(
                text = remote.displayName.take(12),
                style = TextStyle(
                    color = VerifiedEmerald,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            val tagX = (edgeX - tag.size.width * 0.5f)
                .coerceIn(10f, (size.width - tag.size.width - 10f).coerceAtLeast(10f))
            val tagY = (edgeY + 12f)
                .coerceIn(10f, (size.height - tag.size.height - 10f).coerceAtLeast(10f))
            drawText(tag, topLeft = Offset(tagX, tagY))
        }
    }
}

private fun DrawScope.drawPlayerCube(
    state: RainCubeGameState,
    displayName: String,
    pixelsPerUnit: Float,
    textMeasurer: TextMeasurer
) {
    val pos = worldToScreen(
        wx = state.playerX,
        wy = state.playerY,
        cameraX = state.cameraX,
        cameraY = state.cameraY,
        pixelsPerUnit = pixelsPerUnit
    )
    val cx = pos.x
    val cy = pos.y
    val halfPx = pixelsPerUnit * state.playerHalfSize
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
        drawRoundRect(
            color = ElectricCyan.copy(alpha = 0.28f),
            topLeft = Offset(cx - halfPx * 1.24f, cy - halfPx * 1.24f),
            size = Size(halfPx * 2.48f, halfPx * 2.48f),
            cornerRadius = CornerRadius(halfPx * 0.48f, halfPx * 0.48f)
        )

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

        drawRoundRect(
            color = Color.White.copy(alpha = 0.72f),
            topLeft = Offset(cx - halfPx * 0.42f, cy - halfPx * 0.42f),
            size = Size(halfPx * 0.84f, halfPx * 0.84f),
            cornerRadius = CornerRadius(halfPx * 0.22f, halfPx * 0.22f)
        )
    }

    drawNameBadgeAboveCube(
        name = displayName,
        cubeCenterX = cx,
        cubeTopY = cy - halfPx,
        accentColor = if (state.hasShield) PendingAmber else ElectricCyan,
        textMeasurer = textMeasurer
    )
}

private fun DrawScope.drawEntities(state: RainCubeGameState, pixelsPerUnit: Float) {
    for (entity in state.entities) {
        val pos = worldToScreen(entity.x, entity.y, state.cameraX, state.cameraY, pixelsPerUnit)
        val half = pixelsPerUnit * entity.size * 0.5f
        if (pos.x < -half * 3f || pos.x > size.width + half * 3f ||
            pos.y < -half * 3f || pos.y > size.height + half * 3f
        ) {
            continue
        }
        val cx = pos.x
        val cy = pos.y

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

private fun DrawScope.drawParticles(state: RainCubeGameState, pixelsPerUnit: Float) {
    for (p in state.particles) {
        val pos = worldToScreen(p.x, p.y, state.cameraX, state.cameraY, pixelsPerUnit)
        val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
        val color = when {
            p.isDanger -> ErrorCoral
            p.isGolden -> PendingAmber
            else -> ElectricCyan
        }
        drawCircle(
            color = color.copy(alpha = alpha),
            center = pos,
            radius = 5.5f * alpha + 2f
        )
    }
}

private fun DrawScope.drawFloatingCallouts(
    state: RainCubeGameState,
    pixelsPerUnit: Float,
    textMeasurer: TextMeasurer
) {
    for (c in state.callouts) {
        val pos = worldToScreen(c.x, c.y, state.cameraX, state.cameraY, pixelsPerUnit)
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
        val x = (pos.x - measured.size.width / 2f)
            .coerceIn(12f, (size.width - measured.size.width - 12f).coerceAtLeast(12f))
        val y = (pos.y - measured.size.height / 2f)
            .coerceIn(12f, (size.height - measured.size.height - 12f).coerceAtLeast(12f))
        drawText(textLayoutResult = measured, topLeft = Offset(x, y))
    }
}
