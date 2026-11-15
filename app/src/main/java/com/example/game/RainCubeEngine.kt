package com.example.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class GameMode(
    val id: String,
    val baseLives: Int,
    val speedMultiplier: Float,
    val scoreMultiplier: Int
) {
    CLASSIC(
        id = "classic",
        baseLives = 3,
        speedMultiplier = 1.0f,
        scoreMultiplier = 1
    ),
    RUSH(
        id = "rush",
        baseLives = 2,
        speedMultiplier = 1.38f,
        scoreMultiplier = 2
    )
}

enum class GameStatus {
    READY,
    RUNNING,
    PAUSED,
    GAME_OVER
}

enum class FallingEntityKind {
    HAZARD,
    ENERGY,
    CROWN
}

data class FallingEntity(
    val id: Long,
    val kind: FallingEntityKind,
    val x: Float,
    val y: Float,
    val size: Float,
    val speedY: Float,
    val driftX: Float,
    val rotationDeg: Float,
    val rotationSpeed: Float
)

data class TrailNode(
    val x: Float,
    val y: Float,
    val alpha: Float,
    val radiusScale: Float
)

data class SparkParticle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val life: Float,
    val maxLife: Float,
    val isGolden: Boolean,
    val isDanger: Boolean
)

data class FloatingCallout(
    val id: Long,
    val text: String,
    val x: Float,
    val y: Float,
    val life: Float,
    val isGolden: Boolean,
    val isDanger: Boolean
)

data class RainCubeGameState(
    val mode: GameMode = GameMode.CLASSIC,
    val status: GameStatus = GameStatus.READY,
    val playerX: Float = 0.5f,
    val targetX: Float = 0.5f,
    val playerY: Float = 0.82f,
    val playerHalfSize: Float = 0.048f,
    val lives: Int = 3,
    val maxLives: Int = 3,
    val score: Long = 0L,
    val combo: Int = 1,
    val maxCombo: Int = 1,
    val wave: Int = 1,
    val elapsedSeconds: Float = 0f,
    val shieldSeconds: Float = 0f,
    val invulnerableSeconds: Float = 0f,
    val gridScrollOffset: Float = 0f,
    val hitFlashAlpha: Float = 0f,
    val collectedOrbs: Int = 0,
    val entities: List<FallingEntity> = emptyList(),
    val trail: List<TrailNode> = emptyList(),
    val particles: List<SparkParticle> = emptyList(),
    val callouts: List<FloatingCallout> = emptyList()
) {
    val durationMs: Long
        get() = (elapsedSeconds * 1000f).toLong().coerceAtLeast(100L)

    val hasShield: Boolean
        get() = shieldSeconds > 0f
}

class RainCubeEngine(
    private val random: Random = Random.Default
) {
    private var nextId = 1L
    private var spawnAccumulator = 0f
    private var scoreFractionAccumulator = 0f
    private var trailAccumulator = 0f
    private var crownCooldownSeconds = 10f

    fun createInitialState(mode: GameMode, autoStart: Boolean = false): RainCubeGameState {
        nextId = 1L
        spawnAccumulator = 0.25f
        scoreFractionAccumulator = 0f
        trailAccumulator = 0f
        crownCooldownSeconds = if (mode == GameMode.RUSH) 9f else 11f
        return RainCubeGameState(
            mode = mode,
            status = if (autoStart) GameStatus.RUNNING else GameStatus.READY,
            lives = mode.baseLives,
            maxLives = mode.baseLives
        )
    }

    fun startGame(state: RainCubeGameState): RainCubeGameState {
        return if (state.status == GameStatus.READY || state.status == GameStatus.PAUSED) {
            state.copy(status = GameStatus.RUNNING)
        } else {
            state
        }
    }

    fun pauseGame(state: RainCubeGameState): RainCubeGameState {
        return if (state.status == GameStatus.RUNNING) {
            state.copy(status = GameStatus.PAUSED)
        } else {
            state
        }
    }

    fun setTargetX(state: RainCubeGameState, normalizedX: Float): RainCubeGameState {
        val margin = state.playerHalfSize + 0.02f
        val clamped = normalizedX.coerceIn(margin, 1f - margin)
        return state.copy(targetX = clamped)
    }

    fun nudgeTargetX(state: RainCubeGameState, deltaNormalizedX: Float): RainCubeGameState {
        return setTargetX(state, state.targetX + deltaNormalizedX)
    }

    fun step(state: RainCubeGameState, rawDeltaSeconds: Float): RainCubeGameState {
        if (state.status != GameStatus.RUNNING) return state

        val dt = rawDeltaSeconds.coerceIn(0.001f, 0.05f)
        val newElapsed = state.elapsedSeconds + dt
        val newWave = 1 + (newElapsed / 12f).toInt()
        val waveSpeedScale = state.mode.speedMultiplier * (1f + (newWave - 1) * 0.11f)

        // Oyuncu küpünü hedef X pozisyonuna akıcı şekilde yaklaştır
        val margin = state.playerHalfSize + 0.02f
        val followRate = min(1f, dt * 18f)
        val newPlayerX = (state.playerX + (state.targetX - state.playerX) * followRate)
            .coerceIn(margin, 1f - margin)

        // Hayatta kalma skoru (2x kalkan aktifse çift puan)
        val shieldMultiplier = if (state.shieldSeconds > 0f) 2 else 1
        scoreFractionAccumulator += dt * 14f * state.mode.scoreMultiplier * shieldMultiplier
        val passivePoints = scoreFractionAccumulator.toLong()
        scoreFractionAccumulator -= passivePoints

        var currentScore = state.score + passivePoints
        var currentLives = state.lives
        var currentCombo = state.combo
        var currentMaxCombo = state.maxCombo
        var currentCollected = state.collectedOrbs
        var newShieldSeconds = max(0f, state.shieldSeconds - dt)
        var newInvulnSeconds = max(0f, state.invulnerableSeconds - dt)
        var newHitFlash = max(0f, state.hitFlashAlpha - dt * 2.6f)

        // İz (trail) düğümlerini güncelle
        val updatedTrail = state.trail.mapNotNull { node ->
            val nextAlpha = node.alpha - dt * 3.1f
            val nextY = node.y + dt * 0.24f
            if (nextAlpha <= 0.02f) null else node.copy(
                y = nextY,
                alpha = nextAlpha,
                radiusScale = max(0.35f, node.radiusScale - dt * 1.1f)
            )
        }.toMutableList()

        trailAccumulator += dt
        if (trailAccumulator >= 0.022f) {
            trailAccumulator = 0f
            updatedTrail.add(
                TrailNode(
                    x = newPlayerX,
                    y = state.playerY + state.playerHalfSize * 0.45f,
                    alpha = 0.88f,
                    radiusScale = 1f
                )
            )
            if (updatedTrail.size > 20) {
                updatedTrail.removeAt(0)
            }
        }

        // Parçacıkları ve uçan yazıları güncelle
        val updatedParticles = state.particles.mapNotNull { p ->
            val nextLife = p.life - dt
            if (nextLife <= 0f) null else p.copy(
                x = p.x + p.vx * dt,
                y = p.y + p.vy * dt,
                vy = p.vy + 0.35f * dt,
                life = nextLife
            )
        }.toMutableList()

        val updatedCallouts = state.callouts.mapNotNull { c ->
            val nextLife = c.life - dt
            if (nextLife <= 0f) null else c.copy(
                y = c.y - dt * 0.16f,
                life = nextLife
            )
        }.toMutableList()

        // Düşen varlıkları hareket ettir ve çarpışmaları kontrol et
        val survivingEntities = mutableListOf<FallingEntity>()
        val playerRadius = state.playerHalfSize * 0.88f

        for (entity in state.entities) {
            var nx = entity.x + entity.driftX * dt
            var ndx = entity.driftX
            val half = entity.size * 0.5f
            if (nx < half + 0.01f) {
                nx = half + 0.01f
                ndx = abs(ndx)
            } else if (nx > 1f - half - 0.01f) {
                nx = 1f - half - 0.01f
                ndx = -abs(ndx)
            }

            val ny = entity.y + entity.speedY * dt
            val nRot = (entity.rotationDeg + entity.rotationSpeed * dt) % 360f
            val moved = entity.copy(x = nx, y = ny, driftX = ndx, rotationDeg = nRot)

            val dx = abs(moved.x - newPlayerX)
            val dy = abs(moved.y - state.playerY)
            val hitThreshold = playerRadius + (moved.size * 0.44f)

            if (dx < hitThreshold && dy < hitThreshold) {
                when (moved.kind) {
                    FallingEntityKind.ENERGY -> {
                        val gained = (25L * currentCombo * state.mode.scoreMultiplier *
                            if (newShieldSeconds > 0f) 2 else 1)
                        currentScore += gained
                        currentCollected += 1
                        currentCombo = (currentCombo + 1).coerceAtMost(5)
                        currentMaxCombo = max(currentMaxCombo, currentCombo)
                        spawnBurst(updatedParticles, moved.x, moved.y, isGolden = false, isDanger = false)
                        updatedCallouts.add(
                            FloatingCallout(
                                id = nextId++,
                                text = "+$gained",
                                x = moved.x,
                                y = moved.y - 0.03f,
                                life = 0.85f,
                                isGolden = false,
                                isDanger = false
                            )
                        )
                    }

                    FallingEntityKind.CROWN -> {
                        val gained = 100L * state.mode.scoreMultiplier
                        currentScore += gained
                        newShieldSeconds = 5.5f
                        currentCombo = (currentCombo + 1).coerceAtMost(5)
                        currentMaxCombo = max(currentMaxCombo, currentCombo)
                        spawnBurst(updatedParticles, moved.x, moved.y, isGolden = true, isDanger = false)
                        updatedCallouts.add(
                            FloatingCallout(
                                id = nextId++,
                                text = "+$gained TAÇ KALKANI!",
                                x = moved.x.coerceIn(0.22f, 0.78f),
                                y = moved.y - 0.04f,
                                life = 1.15f,
                                isGolden = true,
                                isDanger = false
                            )
                        )
                    }

                    FallingEntityKind.HAZARD -> {
                        if (newShieldSeconds > 0f) {
                            // Kalkan engeli parçalar ve ekstra puan kazandırır
                            val smashPoints = 40L * state.mode.scoreMultiplier
                            currentScore += smashPoints
                            spawnBurst(updatedParticles, moved.x, moved.y, isGolden = true, isDanger = false)
                            updatedCallouts.add(
                                FloatingCallout(
                                    id = nextId++,
                                    text = "+$smashPoints",
                                    x = moved.x,
                                    y = moved.y - 0.03f,
                                    life = 0.75f,
                                    isGolden = true,
                                    isDanger = false
                                )
                            )
                        } else if (newInvulnSeconds <= 0f) {
                            currentLives = max(0, currentLives - 1)
                            currentCombo = 1
                            newInvulnSeconds = 1.35f
                            newHitFlash = 0.85f
                            spawnBurst(updatedParticles, newPlayerX, state.playerY, isGolden = false, isDanger = true)
                            updatedCallouts.add(
                                FloatingCallout(
                                    id = nextId++,
                                    text = "-1 CAN",
                                    x = newPlayerX,
                                    y = state.playerY - 0.06f,
                                    life = 0.9f,
                                    isGolden = false,
                                    isDanger = true
                                )
                            )
                        }
                    }
                }
            } else if (moved.y < 1.12f) {
                survivingEntities.add(moved)
            }
        }

        // Yeni varlık doğurma (spawn)
        crownCooldownSeconds -= dt
        val spawnInterval = max(0.24f, (0.64f / waveSpeedScale))
        spawnAccumulator += dt
        if (spawnAccumulator >= spawnInterval && survivingEntities.size < 26) {
            spawnAccumulator = 0f
            survivingEntities.add(createRandomEntity(newWave, waveSpeedScale))
        }

        val nextStatus = if (currentLives <= 0) GameStatus.GAME_OVER else GameStatus.RUNNING

        return state.copy(
            status = nextStatus,
            playerX = newPlayerX,
            lives = currentLives,
            score = currentScore,
            combo = currentCombo,
            maxCombo = currentMaxCombo,
            wave = newWave,
            elapsedSeconds = newElapsed,
            shieldSeconds = newShieldSeconds,
            invulnerableSeconds = newInvulnSeconds,
            gridScrollOffset = (state.gridScrollOffset + dt * 0.42f * waveSpeedScale) % 1f,
            hitFlashAlpha = newHitFlash,
            collectedOrbs = currentCollected,
            entities = survivingEntities,
            trail = updatedTrail,
            particles = updatedParticles,
            callouts = updatedCallouts
        )
    }

    private fun createRandomEntity(wave: Int, waveSpeedScale: Float): FallingEntity {
        val kind = when {
            crownCooldownSeconds <= 0f -> {
                crownCooldownSeconds = random.nextFloat() * 5f + 11f
                FallingEntityKind.CROWN
            }
            random.nextFloat() < 0.28f -> FallingEntityKind.ENERGY
            else -> FallingEntityKind.HAZARD
        }

        val size = when (kind) {
            FallingEntityKind.HAZARD -> 0.062f + random.nextFloat() * 0.032f
            FallingEntityKind.ENERGY -> 0.044f
            FallingEntityKind.CROWN -> 0.064f
        }

        val baseSpeed = when (kind) {
            FallingEntityKind.HAZARD -> 0.34f + random.nextFloat() * 0.16f
            FallingEntityKind.ENERGY -> 0.30f + random.nextFloat() * 0.10f
            FallingEntityKind.CROWN -> 0.28f
        }

        val drift = if (kind == FallingEntityKind.HAZARD && wave >= 2) {
            (random.nextFloat() - 0.5f) * 0.18f
        } else {
            0f
        }

        val spawnMargin = size * 0.6f + 0.02f
        val x = spawnMargin + random.nextFloat() * (1f - spawnMargin * 2f)

        return FallingEntity(
            id = nextId++,
            kind = kind,
            x = x,
            y = -0.08f,
            size = size,
            speedY = baseSpeed * waveSpeedScale,
            driftX = drift,
            rotationDeg = random.nextFloat() * 360f,
            rotationSpeed = when (kind) {
                FallingEntityKind.HAZARD -> (random.nextFloat() - 0.5f) * 140f
                FallingEntityKind.ENERGY -> 90f
                FallingEntityKind.CROWN -> 0f
            }
        )
    }

    private fun spawnBurst(
        particles: MutableList<SparkParticle>,
        x: Float,
        y: Float,
        isGolden: Boolean,
        isDanger: Boolean
    ) {
        val count = if (isGolden) 16 else 10
        repeat(count) {
            val vx = (random.nextFloat() - 0.5f) * 0.65f
            val vy = (random.nextFloat() - 0.7f) * 0.65f
            particles.add(
                SparkParticle(
                    x = x,
                    y = y,
                    vx = vx,
                    vy = vy,
                    life = 0.45f,
                    maxLife = 0.45f,
                    isGolden = isGolden,
                    isDanger = isDanger
                )
            )
        }
        if (particles.size > 60) {
            particles.subList(0, particles.size - 60).clear()
        }
    }
}
