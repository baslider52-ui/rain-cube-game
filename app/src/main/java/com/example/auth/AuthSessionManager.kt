package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Jetpack Credential Manager + Firebase Authentication Google Sign-In katmanı.
 * Her GetCredentialRequest tek bir seçenek taşır (API 36 bottom-sheet uyumluluğu için).
 */
object AuthSessionManager {

    private const val TAG = "RainCubeAuth"

    fun isFirebaseInitialized(context: Context): Boolean {
        return FirebaseApp.getApps(context.applicationContext).isNotEmpty()
    }

    fun authStateFlow(auth: FirebaseAuth = Firebase.auth): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun FirebaseUser.toAuthenticatedUser(): AuthenticatedUser {
        return AuthenticatedUser(
            uid = uid,
            displayName = displayName?.takeIf { it.isNotBlank() } ?: "Rain Cube Oyuncusu",
            email = email,
            photoUrl = photoUrl?.toString(),
            isEmailVerified = isEmailVerified
        )
    }

    fun resolveWebClientId(context: Context): String? {
        val resId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        if (resId == 0) return null
        return runCatching { context.getString(resId) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    fun attemptSilentSignIn(
        context: Context,
        scope: CoroutineScope,
        onAuthSuccess: (AuthenticatedUser) -> Unit,
        onUnauthenticated: () -> Unit
    ) {
        if (!isFirebaseInitialized(context)) {
            onUnauthenticated()
            return
        }

        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            onAuthSuccess(currentUser.toAuthenticatedUser())
            return
        }

        val clientId = resolveWebClientId(context)
        if (clientId == null) {
            onUnauthenticated()
            return
        }

        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                    val firebaseUser = authResult.user
                    if (firebaseUser != null) {
                        onAuthSuccess(firebaseUser.toAuthenticatedUser())
                    } else {
                        onUnauthenticated()
                    }
                } else {
                    onUnauthenticated()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Silent Google Sign-In skipped/unavailable: ${e.message}")
                onUnauthenticated()
            }
        }
    }

    fun signInWithGoogle(
        context: Context,
        scope: CoroutineScope,
        onAuthSuccess: (AuthenticatedUser) -> Unit,
        onAuthCancelled: (String) -> Unit,
        onAuthError: (String) -> Unit
    ) {
        if (!isFirebaseInitialized(context)) {
            onAuthError(context.getString(R.string.auth_config_missing))
            return
        }

        val clientId = resolveWebClientId(context)
        if (clientId == null) {
            onAuthError(context.getString(R.string.auth_config_missing))
            return
        }

        val activityContext = context as? Activity
        if (activityContext == null) {
            onAuthError("Google hesap seçici yalnızca aktif bir ekran bağlamından açılabilir.")
            return
        }

        val credentialManager = CredentialManager.create(activityContext)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(activityContext, request)
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                    val firebaseUser = authResult.user
                    if (firebaseUser != null) {
                        onAuthSuccess(firebaseUser.toAuthenticatedUser())
                    } else {
                        onAuthError("Firebase Authentication oturumu oluşturulamadı.")
                    }
                } else {
                    onAuthError("Beklenmeyen kimlik bilgisi türü alındı.")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                onAuthCancelled(context.getString(R.string.auth_cancelled_message))
            } catch (e: NoCredentialException) {
                Log.w(TAG, "No Google credential available on device: ${e.message}", e)
                onAuthError(context.getString(R.string.auth_no_credential_message))
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                onAuthError(
                    e.localizedMessage
                        ?: "Google ile giriş başarısız oldu. SHA-1 ve Firebase yapılandırmasını kontrol edin."
                )
            }
        }
    }

    fun signOut(
        context: Context,
        scope: CoroutineScope,
        onSignOutComplete: () -> Unit = {}
    ) {
        val credentialManager = CredentialManager.create(context.applicationContext)
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    Firebase.auth.signOut()
                    onSignOutComplete()
                }
            }
        }
    }
}
