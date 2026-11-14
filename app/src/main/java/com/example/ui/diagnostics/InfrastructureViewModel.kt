package com.example.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.AuthenticatedUser
import com.example.core.config.InfrastructureConfigSnapshot
import com.example.core.firebase.AnalyticsTracker
import com.example.data.model.ConnectivityCheck
import com.example.data.model.PlayerStats
import com.example.data.model.UserProfile
import com.example.data.repository.ConnectivityVerificationResult
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

sealed interface VerificationState {
    data object NotTestedYet : VerificationState
    data object Running : VerificationState
    data class Verified(val result: ConnectivityVerificationResult) : VerificationState
    data class Failed(val errorMessage: String) : VerificationState
}

sealed interface ProfileSyncState {
    data object Syncing : ProfileSyncState
    data class Ready(val profile: UserProfile?) : ProfileSyncState
    data class Error(val message: String) : ProfileSyncState
}

class InfrastructureViewModel(
    private val repository: UserProfileRepository,
    private val analyticsTracker: AnalyticsTracker,
    private val authenticatedUser: AuthenticatedUser,
    val configSnapshot: InfrastructureConfigSnapshot
) : ViewModel() {

    private val _verificationState = MutableStateFlow<VerificationState>(VerificationState.NotTestedYet)
    val verificationState: StateFlow<VerificationState> = _verificationState.asStateFlow()

    private val _profileSyncError = MutableStateFlow<String?>(null)
    val profileSyncError: StateFlow<String?> = _profileSyncError.asStateFlow()

    private val _analyticsStatus = MutableStateFlow("Firebase Analytics SDK başlatıldı (henüz manuel olay gönderilmedi)")
    val analyticsStatus: StateFlow<String> = _analyticsStatus.asStateFlow()

    val profileState: StateFlow<ProfileSyncState> = repository.observeUserProfile(authenticatedUser.uid)
        .map<UserProfile?, ProfileSyncState> { ProfileSyncState.Ready(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            emit(ProfileSyncState.Error(rootError.localizedMessage ?: "Profil dinleyicisi hatası"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ProfileSyncState.Syncing
        )

    val diagnosticsHistory: StateFlow<List<ConnectivityCheck>> = repository.observeDiagnostics(authenticatedUser.uid)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val playerStats: StateFlow<PlayerStats?> = repository.observePlayerStats(authenticatedUser.uid)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    init {
        syncProfile()
    }

    fun syncProfile() {
        viewModelScope.launch {
            _profileSyncError.value = null
            repository.syncAuthenticatedProfile(authenticatedUser)
                .onFailure { err ->
                    _profileSyncError.value = err.localizedMessage ?: "Firestore profil senkronizasyonu başarısız."
                }
        }
    }

    fun runFirestoreVerificationTest() {
        if (_verificationState.value is VerificationState.Running) return
        viewModelScope.launch {
            _verificationState.value = VerificationState.Running
            repository.runConnectivityCheck(appVersion = configSnapshot.versionName)
                .onSuccess { result ->
                    _verificationState.value = VerificationState.Verified(result)
                    runCatching { analyticsTracker.logFirestoreConnectivityVerified(result.roundTripMs) }
                }
                .onFailure { error ->
                    _verificationState.value = VerificationState.Failed(
                        error.localizedMessage ?: "Firestore yazma/okuma doğrulaması başarısız."
                    )
                }
        }
    }

    fun sendAnalyticsPing() {
        runCatching {
            analyticsTracker.logInfrastructureCheck("manual_verification_ping")
        }.onSuccess {
            _analyticsStatus.value = "Analytics olayı gönderildi: infrastructure_manual_ping"
        }.onFailure { err ->
            _analyticsStatus.value = "Analytics hatası: ${err.localizedMessage}"
        }
    }
}
