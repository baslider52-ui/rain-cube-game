package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthSessionManager
import com.example.auth.AuthSessionManager.toAuthenticatedUser
import com.example.core.config.InfrastructureConfig
import com.example.core.firebase.AnalyticsTracker
import com.example.core.firebase.FirebaseModule
import com.example.data.repository.UserProfileRepository
import com.example.ui.auth.AuthGateScreen
import com.example.ui.diagnostics.InfrastructureDashboardScreen
import com.example.ui.diagnostics.InfrastructureViewModel
import com.example.ui.theme.RainCubeTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RainCubeTheme {
                RainCubeInfrastructureRoot()
            }
        }
    }
}

@Composable
fun RainCubeInfrastructureRoot() {
    val auth = remember { Firebase.auth }
    val currentUser by AuthSessionManager.authStateFlow(auth)
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthGateScreen(
            onAuthenticated = {
                // AuthStateListener otomatik olarak kimlik doğrulamalı ekrana geçirir
            }
        )
    } else {
        val authenticatedUser = remember(user.uid, user.displayName, user.email, user.photoUrl) {
            user.toAuthenticatedUser()
        }
        val context = LocalContext.current
        key(authenticatedUser.uid) {
            val viewModel: InfrastructureViewModel = viewModel(
                key = authenticatedUser.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val firestore = FirebaseModule.provideFirestore(app)
                        val repository = UserProfileRepository(db = firestore, auth = auth)
                        val analyticsTracker = AnalyticsTracker(app)
                        val configSnapshot = InfrastructureConfig.load(app)
                        InfrastructureViewModel(
                            repository = repository,
                            analyticsTracker = analyticsTracker,
                            authenticatedUser = authenticatedUser,
                            configSnapshot = configSnapshot
                        )
                    }
                }
            )
            InfrastructureDashboardScreen(
                user = authenticatedUser,
                viewModel = viewModel
            )
        }
    }
}
