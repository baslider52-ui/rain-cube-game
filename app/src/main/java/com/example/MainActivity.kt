package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthSessionManager
import com.example.auth.AuthSessionManager.toAuthenticatedUser
import com.example.core.firebase.FirebaseModule
import com.example.data.repository.MatchRecordRepository
import com.example.data.repository.UserProfileRepository
import com.example.game.GameMode
import com.example.ui.auth.AuthGateScreen
import com.example.ui.game.RainCubeGameScreen
import com.example.ui.game.RainCubeGameViewModel
import com.example.ui.hub.PlayerHubScreen
import com.example.ui.hub.PlayerHubViewModel
import com.example.ui.theme.RainCubeTheme
import com.example.update.UpdatePrefs
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RainCubeTheme {
                RainCubeRoot()
            }
        }
    }
}

@Composable
fun RainCubeRoot() {
    val context = LocalContext.current
    val isFirebaseReady = remember(context) {
        AuthSessionManager.isFirebaseInitialized(context)
    }

    if (!isFirebaseReady) {
        AuthGateScreen(onAuthenticated = {})
        return
    }

    val auth = remember { Firebase.auth }
    val currentUser by AuthSessionManager.authStateFlow(auth)
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthGateScreen(
            onAuthenticated = {
                // AuthStateListener otomatik olarak PlayerHubScreen'e geçirir
            }
        )
    } else {
        val authenticatedUser = remember(user.uid, user.displayName, user.email, user.photoUrl) {
            user.toAuthenticatedUser()
        }
        key(authenticatedUser.uid) {
            var activeMode by remember { mutableStateOf<GameMode?>(null) }
            var gameSessionSeed by remember { mutableIntStateOf(0) }

            val hubViewModel: PlayerHubViewModel = viewModel(
                key = authenticatedUser.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val firestore = FirebaseModule.provideFirestore(app)
                        PlayerHubViewModel(
                            repository = UserProfileRepository(db = firestore, auth = auth),
                            matchRepository = MatchRecordRepository(db = firestore, auth = auth),
                            authenticatedUser = authenticatedUser,
                            initialRepoSlug = UpdatePrefs.getEffectiveRepoSlug(
                                app,
                                BuildConfig.GITHUB_REPO_SLUG
                            ),
                            currentVersionName = BuildConfig.VERSION_NAME,
                            onSaveRepoSlug = { raw -> UpdatePrefs.saveCustomRepoSlug(app, raw) }
                        )
                    }
                }
            )

            val selectedMode = activeMode
            if (selectedMode == null) {
                PlayerHubScreen(
                    user = authenticatedUser,
                    viewModel = hubViewModel,
                    onPlayMode = { mode ->
                        gameSessionSeed += 1
                        activeMode = mode
                    }
                )
            } else {
                val stats by hubViewModel.playerStats.collectAsStateWithLifecycle()
                val recentMatches by hubViewModel.recentMatches.collectAsStateWithLifecycle()
                val bestPrevious = maxOf(
                    stats.bestScore,
                    recentMatches.maxOfOrNull { it.score } ?: 0L
                )

                key("${authenticatedUser.uid}_${selectedMode.id}_$gameSessionSeed") {
                    val gameViewModel: RainCubeGameViewModel = viewModel(
                        key = "${authenticatedUser.uid}_${selectedMode.id}_$gameSessionSeed",
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY]) {
                                    "APPLICATION_KEY missing from CreationExtras"
                                }
                                val firestore = FirebaseModule.provideFirestore(app)
                                RainCubeGameViewModel(
                                    matchRepository = MatchRecordRepository(db = firestore, auth = auth),
                                    initialMode = selectedMode
                                )
                            }
                        }
                    )
                    RainCubeGameScreen(
                        viewModel = gameViewModel,
                        bestPreviousScore = bestPrevious,
                        onExitToHub = {
                            hubViewModel.refreshRecentMatches()
                            activeMode = null
                        }
                    )
                }
            }
        }
    }
}
