package com.example

import com.example.game.FallingEntity
import com.example.game.FallingEntityKind
import com.example.game.GameMode
import com.example.game.GameStatus
import com.example.game.RainCubeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RainCubeEngineTest {

    @Test
    fun `collecting energy orb increases score and combo`() {
        val engine = RainCubeEngine(Random(42))
        val initial = engine.createInitialState(GameMode.CLASSIC, autoStart = true)
        val orb = FallingEntity(
            id = 100L,
            kind = FallingEntityKind.ENERGY,
            x = initial.playerX,
            y = initial.playerY,
            size = 0.044f,
            vx = 0f,
            vy = 0f,
            rotationDeg = 0f,
            rotationSpeed = 0f
        )
        val next = engine.step(initial.copy(entities = listOf(orb)), 0.016f)

        assertTrue(next.score >= 25L)
        assertEquals(2, next.combo)
        assertEquals(1, next.collectedOrbs)
    }

    @Test
    fun `collecting crown activates shield and grants bonus points`() {
        val engine = RainCubeEngine(Random(42))
        val initial = engine.createInitialState(GameMode.CLASSIC, autoStart = true)
        val crown = FallingEntity(
            id = 101L,
            kind = FallingEntityKind.CROWN,
            x = initial.playerX,
            y = initial.playerY,
            size = 0.064f,
            vx = 0f,
            vy = 0f,
            rotationDeg = 0f,
            rotationSpeed = 0f
        )
        val next = engine.step(initial.copy(entities = listOf(crown)), 0.016f)

        assertTrue(next.hasShield)
        assertTrue(next.score >= 100L)
    }

    @Test
    fun `hazard collision reduces lives and ends game at zero`() {
        val engine = RainCubeEngine(Random(42))
        val initial = engine.createInitialState(GameMode.CLASSIC, autoStart = true).copy(lives = 1)
        val hazard = FallingEntity(
            id = 102L,
            kind = FallingEntityKind.HAZARD,
            x = initial.playerX,
            y = initial.playerY,
            size = 0.07f,
            vx = 0f,
            vy = 0f,
            rotationDeg = 0f,
            rotationSpeed = 0f
        )
        val next = engine.step(initial.copy(entities = listOf(hazard)), 0.016f)

        assertEquals(0, next.lives)
        assertEquals(GameStatus.GAME_OVER, next.status)
    }

    @Test
    fun `joystick moves cube across unbounded infinite 2d map and camera follows`() {
        val engine = RainCubeEngine(Random(42))
        var state = engine.createInitialState(GameMode.CLASSIC, autoStart = true)
            .copy(entities = emptyList())
        state = engine.setJoystickInput(state, 1f, -1f)

        repeat(90) {
            state = engine.step(state, 0.03f)
        }

        // 1.0'ın ötesine geçerek haritanın sınırlandırılmadığını doğrula
        assertTrue(state.playerX > 1.0f)
        assertTrue(state.playerY < -1.0f)
        assertTrue(state.cameraX > 0.8f)
        assertTrue(state.cameraY < -0.8f)
    }

    @Test
    fun `pause stops physics progression until resumed`() {
        val engine = RainCubeEngine(Random(42))
        val running = engine.createInitialState(GameMode.RUSH, autoStart = true)
        val paused = engine.pauseGame(running)
        val afterStep = engine.step(paused, 0.05f)

        assertEquals(GameStatus.PAUSED, afterStep.status)
        assertEquals(0f, afterStep.elapsedSeconds, 0.0001f)
    }
}
