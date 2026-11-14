package com.example.ui.hub

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.AuthenticatedUser
import com.example.data.model.PlayerStats
import com.example.data.model.UserProfile
import com.example.data.repository.UserProfileRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PlayerHubUiState {
    data object Loading : PlayerHubUiState
    data class Ready(val profile: UserProfile?) : PlayerHubUiState
    data class Error(val message: String) : PlayerHubUiState
}

class PlayerHubViewModel(
    private val repository: UserProfileRepository,
    private val authenticatedUser: AuthenticatedUser
) : ViewModel() {

    private val _syncNotice = MutableStateFlow<String?>(null)
    val syncNotice: StateFlow<String?> = _syncNotice.asStateFlow()

    val hubState: StateFlow<PlayerHubUiState> = repository.observeUserProfile(authenticatedUser.uid)
        .map<UserProfile?, PlayerHubUiState> { PlayerHubUiState.Ready(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Profile observation failed", rootError)
            emit(PlayerHubUiState.Error("Profil bilgileri şu anda yenilenemedi."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = PlayerHubUiState.Loading
        )

    val playerStats: StateFlow<PlayerStats> = repository.observePlayerStats(authenticatedUser.uid)
        .map { it ?: PlayerStats(userId = authenticatedUser.uid) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(PlayerStats(userId = authenticatedUser.uid))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = PlayerStats(userId = authenticatedUser.uid)
        )

    init {
        syncPlayerProfile()
    }

    fun syncPlayerProfile() {
        viewModelScope.launch {
            _syncNotice.value = null
            repository.syncAuthenticatedProfile(authenticatedUser)
                .onFailure { error ->
                    Log.w(TAG, "Silent profile sync error: ${error.message}")
                    _syncNotice.value = "Profil senkronizasyonu bağlantıyı bekliyor."
                }
        }
    }

    private companion object {
        const val TAG = "PlayerHubVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
