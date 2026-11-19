package com.example.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.model.ArenaPlayer
import com.example.data.repository.MatchRecordRepository
import com.example.data.repository.OnlineArenaRepository
import com.example.game.GameMode
import com.example.game.GameStatus
import com.example.game.RainCubeEngine
import com.example.game.RainCubeGameState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

sealed interface MatchSaveState {
    data object Idle : MatchSaveState
    data object Saving : MatchSaveState
    data class Saved(val matchId: String) : MatchSaveState
    data class Error(val message: String) : MatchSaveState
}

data class RemoteArenaPlayer(
    val userId: String,
    val displayName: String,
    val mode: String,
    val renderX: Float,
    val renderY: Float,
    val targetX: Float,
    val targetY: Float,
    val score: Long,
    val shieldActive: Boolean
)

class RainCubeGameViewModel(
    private val matchRepository: MatchRecordRepository,
    private val onlineArenaRepository: OnlineArenaRepository,
    val currentUserId: String,
    val currentDisplayName: String,
    initialMode: GameMode
) : ViewModel() {

    private val engine = RainCubeEngine()
    private var submittedThisRun = false

    private val _gameState = MutableStateFlow(
        engine.createInitialState(mode = initialMode, autoStart = true)
    )
    val gameState: StateFlow<RainCubeGameState> = _gameState.asStateFlow()

    private val _saveState = MutableStateFlow<MatchSaveState>(MatchSaveState.Idle)
    val saveState: StateFlow<MatchSaveState> = _saveState.asStateFlow()

    private val _remotePlayers = MutableStateFlow<List<RemoteArenaPlayer>>(emptyList())
    val remotePlayers: StateFlow<List<RemoteArenaPlayer>> = _remotePlayers.asStateFlow()

    private var lastPresenceSyncMs = 0L
    private var lastSyncedX = 0f
    private var lastSyncedY = 0f
    private var lastSyncedScore = -1L
    private var lastSyncedShield = false
    private var isPresenceSyncInFlight = false

    init {
        observeOnlinePlayers()
        publishPresenceImmediate(_gameState.value)
    }

    private fun observeOnlinePlayers() {
        viewModelScope.launch {
            onlineArenaRepository.observeActivePlayers()
                .catch { emit(emptyList()) }
                .collect { activeList ->
                    val currentModeId = _gameState.value.mode.id
                    val existingById = _remotePlayers.value.associateBy { it.userId }
                    val updated = activeList
                        .asSequence()
                        .filter { it.userId.isNotBlank() && it.userId != currentUserId }
                        .filter { it.mode == currentModeId }
                        .map { remote -> remote.toUiPlayer(existingById[remote.userId]) }
                        .toList()
                    _remotePlayers.value = updated
                }
        }
    }

    fun stepFrame(deltaSeconds: Float) {
        val dt = deltaSeconds.coerceIn(0.001f, 0.05f)
        interpolateRemotePlayers(dt)

        val prev = _gameState.value
        val next = engine.step(prev, dt)
        _gameState.value = next

        if (prev.status != GameStatus.GAME_OVER && next.status == GameStatus.GAME_OVER) {
            markOffline()
            submitMatchRecord(next)
        } else if (next.status == GameStatus.RUNNING) {
            maybeSyncPresence(next)
        }
    }

    fun setJoystickInput(dx: Float, dy: Float) {
        val current = _gameState.value
        val updated = engine.setJoystickInput(current, dx, dy)
        _gameState.value = if (updated.status == GameStatus.READY && (dx != 0f || dy != 0f)) {
            engine.startGame(updated)
        } else {
            updated
        }
    }

    fun startOrResume() {
        val resumed = engine.startGame(_gameState.value)
        _gameState.value = resumed
        if (resumed.status == GameStatus.RUNNING) {
            publishPresenceImmediate(resumed)
        }
    }

    fun pause() {
        _gameState.value = engine.pauseGame(_gameState.value)
        markOffline()
    }

    fun restart() {
        submittedThisRun = false
        _saveState.value = MatchSaveState.Idle
        val fresh = engine.createInitialState(
            mode = _gameState.value.mode,
            autoStart = true
        )
        _gameState.value = fresh
        publishPresenceImmediate(fresh)
    }

    fun leaveArenaNow() {
        markOffline()
    }

    fun retrySaveMatch() {
        val current = _gameState.value
        if (current.status == GameStatus.GAME_OVER && _saveState.value !is MatchSaveState.Saving) {
            submittedThisRun = false
            submitMatchRecord(current)
        }
    }

    private fun interpolateRemotePlayers(dt: Float) {
        val list = _remotePlayers.value
        if (list.isEmpty()) return
        val rate = min(1f, dt * 10f)
        var changed = false
        val interpolated = list.map { player ->
            val dx = player.targetX - player.renderX
            val dy = player.targetY - player.renderY
            if (abs(dx) < 0.0005f && abs(dy) < 0.0005f) {
                player
            } else {
                changed = true
                player.copy(
                    renderX = player.renderX + dx * rate,
                    renderY = player.renderY + dy * rate
                )
            }
        }
        if (changed) {
            _remotePlayers.value = interpolated
        }
    }

    private fun maybeSyncPresence(state: RainCubeGameState) {
        if (isPresenceSyncInFlight) return
        val nowMs = System.currentTimeMillis()
        val elapsedMs = nowMs - lastPresenceSyncMs
        val movedDist = hypot(state.playerX - lastSyncedX, state.playerY - lastSyncedY)
        val shieldChanged = state.hasShield != lastSyncedShield
        val scoreDelta = abs(state.score - lastSyncedScore)

        val shouldSync = when {
            lastPresenceSyncMs == 0L -> true
            shieldChanged && elapsedMs >= 140L -> true
            movedDist >= 0.03f && elapsedMs >= 180L -> true
            scoreDelta >= 25L && elapsedMs >= 450L -> true
            elapsedMs >= 3000L -> true
            else -> false
        }

        if (shouldSync) {
            publishPresenceImmediate(state)
        }
    }

    private fun publishPresenceImmediate(state: RainCubeGameState) {
        if (isPresenceSyncInFlight) return
        isPresenceSyncInFlight = true
        lastPresenceSyncMs = System.currentTimeMillis()
        lastSyncedX = state.playerX
        lastSyncedY = state.playerY
        lastSyncedScore = state.score
        lastSyncedShield = state.hasShield

        viewModelScope.launch {
            try {
                onlineArenaRepository.syncPresence(
                    displayName = currentDisplayName,
                    mode = state.mode.id,
                    worldX = state.playerX,
                    worldY = state.playerY,
                    score = state.score,
                    shieldActive = state.hasShield
                )
            } finally {
                isPresenceSyncInFlight = false
            }
        }
    }

    private fun markOffline() {
        viewModelScope.launch {
            onlineArenaRepository.leaveArena()
        }
    }

    override fun onCleared() {
        super.onCleared()
        markOffline()
    }

    private fun submitMatchRecord(state: RainCubeGameState) {
        if (submittedThisRun) return
        submittedThisRun = true
        _saveState.value = MatchSaveState.Saving
        val matchId = "match_${UUID.randomUUID().toString().replace("-", "")}"

        viewModelScope.launch {
            val result = matchRepository.submitPendingMatch(
                matchId = matchId,
                mode = state.mode.id,
                score = state.score,
                durationMs = state.durationMs,
                clientVersion = BuildConfig.VERSION_NAME
            )
            _saveState.value = result.fold(
                onSuccess = { savedId -> MatchSaveState.Saved(savedId) },
                onFailure = { err ->
                    MatchSaveState.Error(err.localizedMessage ?: "Skor kaydedilemedi")
                }
            )
        }
    }

    private fun ArenaPlayer.toUiPlayer(previous: RemoteArenaPlayer?): RemoteArenaPlayer {
        val tx = worldX.toFloat()
        val ty = worldY.toFloat()
        val snapJump = previous != null && hypot(tx - previous.renderX, ty - previous.renderY) > 1.8f
        return RemoteArenaPlayer(
            userId = userId,
            displayName = displayName.ifBlank { "Oyuncu" },
            mode = mode,
            renderX = if (previous == null || snapJump) tx else previous.renderX,
            renderY = if (previous == null || snapJump) ty else previous.renderY,
            targetX = tx,
            targetY = ty,
            score = score,
            shieldActive = shieldActive
        )
    }
}
