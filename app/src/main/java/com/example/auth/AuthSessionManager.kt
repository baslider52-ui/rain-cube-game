package com.example.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
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
    private const val PREFS_NAME = "rain_cube_auth_prefs"
    private const val KEY_HAS_SIGNED_IN = "has_completed_google_sign_in"

    private val signOutScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var silentAttemptedInProcess = false
    private var lastInteractiveAttemptFailed = false

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private fun hasCompletedSignInBefore(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HAS_SIGNED_IN, false)
    }

    private fun setCompletedSignInBefore(context: Context, value: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HAS_SIGNED_IN, value)
            .apply()
    }

    fun isFirebaseInitialized(context: Context): Boolean {
        return runCatching {
            val appContext = context.applicationContext
            if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                true
            } else {
                FirebaseApp.initializeApp(appContext) != null
            }
        }.getOrDefault(false)
    }

    fun authStateFlow(auth: FirebaseAuth = Firebase.auth): Flow<FirebaseUser?> = callbackFlow {
        trySend(auth.currentUser)
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

    @SuppressLint("DiscouragedApi")
    fun resolveWebClientId(context: Context): String? {
        val candidatePackages = listOf(
            context.packageName,
            context.applicationInfo?.packageName,
            "com.example"
        ).filterNotNull().distinct()

        for (pkg in candidatePackages) {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", pkg)
            if (resId != 0) {
                val value = runCatching { context.getString(resId) }.getOrNull()
                if (!value.isNullOrBlank()) return value
            }
        }
        return null
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
            setCompletedSignInBefore(context, true)
            onAuthSuccess(currentUser.toAuthenticatedUser())
            return
        }

        // İlk kurulumda veya çıkış yapıldıktan sonra arka planda One Tap çağrısı yapıp
        // Google Play Hizmetleri hız sınırına (Error 16) takılmamak için yalnızca
        // daha önce oturum açmış kullanıcılarda ve süreç başına en fazla bir kez dene.
        if (silentAttemptedInProcess || !hasCompletedSignInBefore(context)) {
            onUnauthenticated()
            return
        }
        silentAttemptedInProcess = true

        val clientId = resolveWebClientId(context)
        if (clientId == null) {
            onUnauthenticated()
            return
        }

        val hostContext = context.findActivity()?.let { MutableContextWrapper(it) } ?: context
        val credentialManager = CredentialManager.create(hostContext)
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
                val result = credentialManager.getCredential(hostContext, request)
                val firebaseUser = exchangeCredentialWithFirebase(result.credential)
                if (firebaseUser != null) {
                    setCompletedSignInBefore(context, true)
                    onAuthSuccess(firebaseUser.toAuthenticatedUser())
                } else {
                    onUnauthenticated()
                }
            } catch (e: CancellationException) {
                throw e
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

        val activityContext = context.findActivity()
        if (activityContext == null) {
            onAuthError("Google hesap seçici yalnızca aktif bir ekran bağlamından açılabilir.")
            return
        }

        val mutableContext = MutableContextWrapper(activityContext)
        val credentialManager = CredentialManager.create(mutableContext)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val primaryRequest = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            if (lastInteractiveAttemptFailed) {
                runCatching {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                }
            }

            try {
                val result = try {
                    credentialManager.getCredential(mutableContext, primaryRequest)
                } catch (e: GetCredentialUnsupportedException) {
                    Log.w(TAG, "Primary SIWG unsupported, trying GoogleIdOption fallback", e)
                    val fallbackOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(clientId)
                        .setAutoSelectEnabled(false)
                        .build()
                    val fallbackRequest = GetCredentialRequest.Builder()
                        .addCredentialOption(fallbackOption)
                        .build()
                    credentialManager.getCredential(mutableContext, fallbackRequest)
                }

                val firebaseUser = exchangeCredentialWithFirebase(result.credential)
                if (firebaseUser != null) {
                    lastInteractiveAttemptFailed = false
                    setCompletedSignInBefore(context, true)
                    onAuthSuccess(firebaseUser.toAuthenticatedUser())
                } else {
                    lastInteractiveAttemptFailed = true
                    onAuthError("Oturum oluşturulamadı. Lütfen tekrar deneyin.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: GetCredentialCancellationException) {
                lastInteractiveAttemptFailed = true
                val rawDetail = listOfNotNull(
                    e.errorMessage?.toString(),
                    e.message,
                    e.cause?.message
                ).distinct().joinToString(" | ")
                Log.w(TAG, "Google Sign-In flow cancelled or dismissed: $rawDetail", e)
                val mappedDiagnostic = mapDiagnosticDetailOrNull(context, rawDetail)
                if (mappedDiagnostic != null) {
                    onAuthError(mappedDiagnostic)
                } else {
                    onAuthCancelled(context.getString(R.string.auth_cancelled_message))
                }
            } catch (e: NoCredentialException) {
                lastInteractiveAttemptFailed = true
                Log.w(TAG, "No Google credential available on device: ${e.message}", e)
                onAuthError(context.getString(R.string.auth_no_credential_message))
            } catch (e: GetCredentialProviderConfigurationException) {
                lastInteractiveAttemptFailed = true
                Log.e(TAG, "Credential provider configuration error: ${e.errorMessage}", e)
                onAuthError(context.getString(R.string.auth_play_services_error))
            } catch (e: GetCredentialUnsupportedException) {
                lastInteractiveAttemptFailed = true
                Log.e(TAG, "Credential manager unsupported: ${e.errorMessage}", e)
                onAuthError(context.getString(R.string.auth_play_services_error))
            } catch (e: GetCredentialInterruptedException) {
                lastInteractiveAttemptFailed = true
                Log.w(TAG, "Credential request interrupted: ${e.errorMessage}", e)
                onAuthError("Giriş işlemi kesintiye uğradı. Lütfen tekrar deneyin.")
            } catch (e: GetCredentialException) {
                lastInteractiveAttemptFailed = true
                val rawDetail = listOfNotNull(
                    e.errorMessage?.toString(),
                    e.message,
                    e.cause?.message
                ).distinct().joinToString(" | ")
                Log.e(TAG, "Google Sign-In credential exception: ${e.type} - $rawDetail", e)
                onAuthError(mapCredentialErrorMessage(context, rawDetail))
            } catch (e: FirebaseNetworkException) {
                lastInteractiveAttemptFailed = true
                Log.e(TAG, "Firebase network error during Google Sign-In", e)
                onAuthError("İnternet bağlantısı kurulamadı. Bağlantınızı kontrol edip tekrar deneyin.")
            } catch (e: FirebaseAuthException) {
                lastInteractiveAttemptFailed = true
                Log.e(TAG, "FirebaseAuthException during Google Sign-In: ${e.errorCode}", e)
                val message = when (e.errorCode) {
                    "ERROR_OPERATION_NOT_ALLOWED" ->
                        "Firebase projesinde Google ile Giriş sağlayıcısı etkinleştirilmemiş."
                    "ERROR_USER_DISABLED" ->
                        "Bu Google hesabı bu uygulama için devre dışı bırakılmış."
                    else -> mapCredentialErrorMessage(context, e.localizedMessage)
                }
                onAuthError(message)
            } catch (e: Exception) {
                lastInteractiveAttemptFailed = true
                Log.e(TAG, "Google Sign-In failed", e)
                onAuthError(mapCredentialErrorMessage(context, e.localizedMessage))
            }
        }
    }

    private suspend fun exchangeCredentialWithFirebase(credential: Credential): FirebaseUser? {
        if (credential is CustomCredential &&
            credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            val authResult = Firebase.auth.signInWithCredential(authCredential).await()
            return authResult.user
        }
        return null
    }

    internal fun mapDiagnosticDetailOrNull(context: Context, rawMessage: String?): String? {
        val msg = rawMessage.orEmpty()
        return when {
            msg.contains("28444") ||
                msg.contains("Developer console", ignoreCase = true) ||
                msg.contains("Cannot find a matching credential", ignoreCase = true) ||
                msg.contains("not whitelisted", ignoreCase = true) ||
                msg.contains("10:") ||
                msg.contains("[10]") ||
                msg.contains("12500") ->
                context.getString(R.string.auth_sha_mismatch_message)
            msg.contains("temporarily blocked", ignoreCase = true) ||
                msg.contains("too many canceled", ignoreCase = true) ||
                msg.contains("cooldown", ignoreCase = true) ||
                msg.contains("16:") ||
                msg.contains("[16]") ->
                context.getString(R.string.auth_rate_limited_message)
            msg.contains("network", ignoreCase = true) || msg.contains("7:") ->
                "İnternet bağlantısı kurulamadı. Bağlantınızı kontrol edip tekrar deneyin."
            else -> null
        }
    }

    private fun mapCredentialErrorMessage(context: Context, rawMessage: String?): String {
        return mapDiagnosticDetailOrNull(context, rawMessage)
            ?: context.getString(R.string.auth_generic_error)
    }

    fun signOut(
        context: Context,
        onSignOutComplete: () -> Unit = {}
    ) {
        val appContext = context.applicationContext
        setCompletedSignInBefore(appContext, false)
        silentAttemptedInProcess = false
        val credentialManager = CredentialManager.create(appContext)
        signOutScope.launch {
            withContext(NonCancellable) {
                if (isFirebaseInitialized(appContext)) {
                    runCatching { Firebase.auth.signOut() }
                }
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                }
                onSignOutComplete()
            }
        }
    }
}
