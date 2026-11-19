package com.example.ui.hub

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.auth.AuthenticatedUser
import com.example.data.model.MatchRecord
import com.example.data.model.PlayerStats
import com.example.data.model.UserProfile
import com.example.data.repository.MatchRecordRepository
import com.example.data.repository.UserProfileRepository
import com.example.update.GitHubReleaseInfo
import com.example.update.GitHubUpdateChecker
import com.example.update.UpdateCheckResult
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

sealed interface UpdateUiState {
    data object NeedsRepoConfig : UpdateUiState
    data class Checking(val repoSlug: String) : UpdateUiState
    data class UpToDate(val repoSlug: String, val release: GitHubReleaseInfo) : UpdateUiState
    data class UpdateAvailable(val repoSlug: String, val release: GitHubReleaseInfo) : UpdateUiState
    data class NoReleaseYet(val repoSlug: String) : UpdateUiState
    data class Error(val repoSlug: String, val message: String) : UpdateUiState
}

class PlayerHubViewModel(
    private val repository: UserProfileRepository,
    private val matchRepository: MatchRecordRepository,
    private val authenticatedUser: AuthenticatedUser,
    initialRepoSlug: String = BuildConfig.GITHUB_REPO_SLUG,
    private val currentVersionName: String = BuildConfig.VERSION_NAME
) : ViewModel() {

    private val _syncNotice = MutableStateFlow<String?>(null)
    val syncNotice: StateFlow<String?> = _syncNotice.asStateFlow()

    private val _recentMatches = MutableStateFlow<List<MatchRecord>>(emptyList())
    val recentMatches: StateFlow<List<MatchRecord>> = _recentMatches.asStateFlow()

    private val normalizedInitialRepo = GitHubUpdateChecker.normalizeRepoSlug(initialRepoSlug).orEmpty()
    private val _repoSlug = MutableStateFlow(normalizedInitialRepo)
    val repoSlug: StateFlow<String> = _repoSlug.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateUiState>(
        if (normalizedInitialRepo.isEmpty()) {
            UpdateUiState.NeedsRepoConfig
        } else {
            UpdateUiState.Checking(normalizedInitialRepo)
        }
    )
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private var updateCheckJob: Job? = null

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
        refreshRecentMatches()
        if (normalizedInitialRepo.isNotEmpty()) {
            checkForUpdates(normalizedInitialRepo)
        }
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

    fun refreshRecentMatches() {
        viewModelScope.launch {
            matchRepository.fetchUserMatchRecords()
                .onSuccess { records ->
                    _recentMatches.value = records.take(10)
                }
                .onFailure { error ->
                    Log.w(TAG, "Recent matches fetch skipped: ${error.message}")
                }
        }
    }

    fun checkForUpdates(overrideSlug: String? = null) {
        val targetSlug = GitHubUpdateChecker.normalizeRepoSlug(overrideSlug ?: _repoSlug.value).orEmpty()
        if (targetSlug.isEmpty()) {
            _updateState.value = UpdateUiState.NeedsRepoConfig
            return
        }

        updateCheckJob?.cancel()
        updateCheckJob = viewModelScope.launch {
            _updateState.value = UpdateUiState.Checking(targetSlug)
            when (val result = GitHubUpdateChecker.fetchLatestRelease(targetSlug, currentVersionName)) {
                is UpdateCheckResult.UpdateAvailable -> {
                    _updateState.value = UpdateUiState.UpdateAvailable(targetSlug, result.release)
                }
                is UpdateCheckResult.UpToDate -> {
                    _updateState.value = UpdateUiState.UpToDate(targetSlug, result.release)
                }
                is UpdateCheckResult.NotFound -> {
                    _updateState.value = UpdateUiState.NoReleaseYet(result.repoSlug)
                }
                is UpdateCheckResult.Error -> {
                    _updateState.value = UpdateUiState.Error(targetSlug, result.message)
                }
            }
        }
    }

    private companion object {
        const val TAG = "PlayerHubVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
