package com.example.game

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
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
    val vx: Float,
    val vy: Float,
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
    val playerX: Float = 0f,
    val playerY: Float = 0f,
    val playerVx: Float = 0f,
    val playerVy: Float = 0f,
    val cameraX: Float = 0f,
    val cameraY: Float = 0f,
    val joystickX: Float = 0f,
    val joystickY: Float = 0f,
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

        // Başlangıçta oyuncunun çevresinde birkaç enerji küpü hazır olsun
        val starterOrbs = List(5) { idx ->
            val angle = (idx / 5f) * (2f * PI.toFloat())
            val radius = 0.34f + (idx % 2) * 0.16f
            FallingEntity(
                id = nextId++,
                kind = FallingEntityKind.ENERGY,
                x = cos(angle) * radius,
                y = sin(angle) * radius,
                size = 0.044f,
                vx = (random.nextFloat() - 0.5f) * 0.06f,
                vy = (random.nextFloat() - 0.5f) * 0.06f,
                rotationDeg = random.nextFloat() * 360f,
                rotationSpeed = 90f
            )
        }

        return RainCubeGameState(
            mode = mode,
            status = if (autoStart) GameStatus.RUNNING else GameStatus.READY,
            lives = mode.baseLives,
            maxLives = mode.baseLives,
            entities = starterOrbs
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
            state.copy(status = GameStatus.PAUSED, joystickX = 0f, joystickY = 0f)
        } else {
            state
        }
    }

    fun setJoystickInput(state: RainCubeGameState, dx: Float, dy: Float): RainCubeGameState {
        if (!dx.isFinite() || !dy.isFinite()) return state
        val mag = hypot(dx, dy)
        if (mag < JOYSTICK_DEADZONE) {
            return if (state.joystickX == 0f && state.joystickY == 0f) {
                state
            } else {
                state.copy(joystickX = 0f, joystickY = 0f)
            }
        }
        val clampedMag = min(1f, mag)
        val normScale = clampedMag / mag
        return state.copy(
            joystickX = dx * normScale,
            joystickY = dy * normScale
        )
    }

    fun step(state: RainCubeGameState, rawDeltaSeconds: Float): RainCubeGameState {
        if (state.status != GameStatus.RUNNING) return state

        val dt = rawDeltaSeconds.coerceIn(0.001f, 0.05f)
        val newElapsed = state.elapsedSeconds + dt
        val newWave = 1 + (newElapsed / 14f).toInt()
        val waveSpeedScale = state.mode.speedMultiplier * (1f + (newWave - 1) * 0.10f)

        // Sonsuz 2D haritada joystick girdisine göre akıcı hız ve konum güncellemesi
        val maxSpeed = if (state.mode == GameMode.RUSH) 0.78f else 0.68f
        val targetVx = state.joystickX * maxSpeed
        val targetVy = state.joystickY * maxSpeed
        val accelRate = min(1f, dt * 14f)
        val newVx = state.playerVx + (targetVx - state.playerVx) * accelRate
        val newVy = state.playerVy + (targetVy - state.playerVy) * accelRate
        val newPlayerX = state.playerX + newVx * dt
        val newPlayerY = state.playerY + newVy * dt

        // Kamera oyuncuyu akıcı şekilde ve hareket yönüne hafif öncü bakışla takip eder
        val targetCamX = newPlayerX + newVx * 0.16f
        val targetCamY = newPlayerY + newVy * 0.16f
        val camRate = min(1f, dt * 8.5f)
        val newCameraX = state.cameraX + (targetCamX - state.cameraX) * camRate
        val newCameraY = state.cameraY + (targetCamY - state.cameraY) * camRate

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

        // Dünya koordinatlarında neon iz (trail) düğümleri
        val updatedTrail = state.trail.mapNotNull { node ->
            val nextAlpha = node.alpha - dt * 3.0f
            if (nextAlpha <= 0.02f) null else node.copy(
                alpha = nextAlpha,
                radiusScale = max(0.35f, node.radiusScale - dt * 1.15f)
            )
        }.toMutableList()

        trailAccumulator += dt
        val isMoving = hypot(newVx, newVy) > 0.04f
        if (trailAccumulator >= 0.024f && (isMoving || updatedTrail.isEmpty())) {
            trailAccumulator = 0f
            updatedTrail.add(
                TrailNode(
                    x = newPlayerX,
                    y = newPlayerY,
                    alpha = 0.88f,
                    radiusScale = 1f
                )
            )
            if (updatedTrail.size > 22) {
                updatedTrail.removeAt(0)
            }
        }

        // Parçacıkları ve uçan yazıları güncelle
        val updatedParticles = state.particles.mapNotNull { p ->
            val nextLife = p.life - dt
            if (nextLife <= 0f) null else p.copy(
                x = p.x + p.vx * dt,
                y = p.y + p.vy * dt,
                life = nextLife
            )
        }.toMutableList()

        val updatedCallouts = state.callouts.mapNotNull { c ->
            val nextLife = c.life - dt
            if (nextLife <= 0f) null else c.copy(
                y = c.y - dt * 0.14f,
                life = nextLife
            )
        }.toMutableList()

        // Varlıkları 2D dünya uzayında ilerlet ve çarpışmaları kontrol et
        val survivingEntities = mutableListOf<FallingEntity>()
        val playerRadius = state.playerHalfSize * 0.88f

        for (entity in state.entities) {
            val nx = entity.x + entity.vx * dt
            val ny = entity.y + entity.vy * dt
            val nRot = (entity.rotationDeg + entity.rotationSpeed * dt) % 360f
            val moved = entity.copy(x = nx, y = ny, rotationDeg = nRot)

            val distToPlayer = hypot(moved.x - newPlayerX, moved.y - newPlayerY)
            val hitThreshold = playerRadius + (moved.size * 0.45f)

            if (distToPlayer < hitThreshold) {
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
                                x = moved.x,
                                y = moved.y - 0.04f,
                                life = 1.15f,
                                isGolden = true,
                                isDanger = false
                            )
                        )
                    }

                    FallingEntityKind.HAZARD -> {
                        if (newShieldSeconds > 0f) {
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
                            spawnBurst(updatedParticles, newPlayerX, newPlayerY, isGolden = false, isDanger = true)
                            updatedCallouts.add(
                                FloatingCallout(
                                    id = nextId++,
                                    text = "-1 CAN",
                                    x = newPlayerX,
                                    y = newPlayerY - 0.06f,
                                    life = 0.9f,
                                    isGolden = false,
                                    isDanger = true
                                )
                            )
                        }
                    }
                }
            } else {
                val distToCamera = hypot(moved.x - newCameraX, moved.y - newCameraY)
                if (distToCamera < DESPAWN_RADIUS) {
                    survivingEntities.add(moved)
                }
            }
        }

        // Oyuncunun mevcut konumu çevresinde dinamik varlık doğurma
        crownCooldownSeconds -= dt
        val spawnInterval = max(0.28f, 0.62f / waveSpeedScale)
        spawnAccumulator += dt
        if (spawnAccumulator >= spawnInterval && survivingEntities.size < MAX_ACTIVE_ENTITIES) {
            spawnAccumulator = 0f
            survivingEntities.add(
                createEntityAroundCamera(
                    cameraX = newCameraX,
                    cameraY = newCameraY,
                    playerX = newPlayerX,
                    playerY = newPlayerY,
                    waveSpeedScale = waveSpeedScale
                )
            )
        }

        val nextStatus = if (currentLives <= 0) GameStatus.GAME_OVER else GameStatus.RUNNING

        return state.copy(
            status = nextStatus,
            playerX = newPlayerX,
            playerY = newPlayerY,
            playerVx = newVx,
            playerVy = newVy,
            cameraX = newCameraX,
            cameraY = newCameraY,
            lives = currentLives,
            score = currentScore,
            combo = currentCombo,
            maxCombo = currentMaxCombo,
            wave = newWave,
            elapsedSeconds = newElapsed,
            shieldSeconds = newShieldSeconds,
            invulnerableSeconds = newInvulnSeconds,
            hitFlashAlpha = newHitFlash,
            collectedOrbs = currentCollected,
            entities = survivingEntities,
            trail = updatedTrail,
            particles = updatedParticles,
            callouts = updatedCallouts
        )
    }

    private fun createEntityAroundCamera(
        cameraX: Float,
        cameraY: Float,
        playerX: Float,
        playerY: Float,
        waveSpeedScale: Float
    ): FallingEntity {
        val kind = when {
            crownCooldownSeconds <= 0f -> {
                crownCooldownSeconds = random.nextFloat() * 5f + 11f
                FallingEntityKind.CROWN
            }
            random.nextFloat() < 0.32f -> FallingEntityKind.ENERGY
            else -> FallingEntityKind.HAZARD
        }

        val size = when (kind) {
            FallingEntityKind.HAZARD -> 0.062f + random.nextFloat() * 0.030f
            FallingEntityKind.ENERGY -> 0.044f
            FallingEntityKind.CROWN -> 0.064f
        }

        val spawnAngle = random.nextFloat() * (2f * PI.toFloat())
        val spawnDist = when (kind) {
            FallingEntityKind.HAZARD -> 1.02f + random.nextFloat() * 0.25f
            FallingEntityKind.ENERGY -> 0.72f + random.nextFloat() * 0.45f
            FallingEntityKind.CROWN -> 0.85f + random.nextFloat() * 0.30f
        }
        val spawnX = cameraX + cos(spawnAngle) * spawnDist
        val spawnY = cameraY + sin(spawnAngle) * spawnDist

        val speed = when (kind) {
            FallingEntityKind.HAZARD -> (0.30f + random.nextFloat() * 0.16f) * waveSpeedScale
            FallingEntityKind.ENERGY -> (0.10f + random.nextFloat() * 0.10f) * waveSpeedScale
            FallingEntityKind.CROWN -> 0.16f * waveSpeedScale
        }

        // Tehlikeleri hafif sapmayla oyuncunun bulunduğu bölgeye yönlendir
        val targetJitterX = playerX + (random.nextFloat() - 0.5f) * 0.75f
        val targetJitterY = playerY + (random.nextFloat() - 0.5f) * 0.75f
        val dirX = targetJitterX - spawnX
        val dirY = targetJitterY - spawnY
        val len = hypot(dirX, dirY).coerceAtLeast(0.001f)

        return FallingEntity(
            id = nextId++,
            kind = kind,
            x = spawnX,
            y = spawnY,
            size = size,
            vx = (dirX / len) * speed,
            vy = (dirY / len) * speed,
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
            val angle = random.nextFloat() * (2f * PI.toFloat())
            val spd = 0.18f + random.nextFloat() * 0.36f
            particles.add(
                SparkParticle(
                    x = x,
                    y = y,
                    vx = cos(angle) * spd,
                    vy = sin(angle) * spd,
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

    private companion object {
        const val JOYSTICK_DEADZONE = 0.06f
        const val DESPAWN_RADIUS = 1.95f
        const val MAX_ACTIVE_ENTITIES = 28
    }
}
