package com.example.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.repository.MatchRecordRepository
import com.example.game.GameMode
import com.example.game.GameStatus
import com.example.game.RainCubeEngine
import com.example.game.RainCubeGameState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface MatchSaveState {
    data object Idle : MatchSaveState
    data object Saving : MatchSaveState
    data class Saved(val matchId: String) : MatchSaveState
    data class Error(val message: String) : MatchSaveState
}

class RainCubeGameViewModel(
    private val matchRepository: MatchRecordRepository,
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

    fun stepFrame(deltaSeconds: Float) {
        val prev = _gameState.value
        val next = engine.step(prev, deltaSeconds)
        _gameState.value = next
        if (prev.status != GameStatus.GAME_OVER && next.status == GameStatus.GAME_OVER) {
            submitMatchRecord(next)
        }
    }

    fun movePlayerTo(normalizedX: Float) {
        _gameState.value = engine.setTargetX(_gameState.value, normalizedX)
    }

    fun movePlayerBy(deltaNormalizedX: Float) {
        _gameState.value = engine.nudgeTargetX(_gameState.value, deltaNormalizedX)
    }

    fun startOrResume() {
        _gameState.value = engine.startGame(_gameState.value)
    }

    fun pause() {
        _gameState.value = engine.pauseGame(_gameState.value)
    }

    fun restart() {
        submittedThisRun = false
        _saveState.value = MatchSaveState.Idle
        _gameState.value = engine.createInitialState(
            mode = _gameState.value.mode,
            autoStart = true
        )
    }

    fun retrySaveMatch() {
        val current = _gameState.value
        if (current.status == GameStatus.GAME_OVER && _saveState.value !is MatchSaveState.Saving) {
            submittedThisRun = false
            submitMatchRecord(current)
        }
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
}
