package com.example.data.repository

import android.content.Context
import com.example.auth.AuthenticatedUser
import com.example.core.firebase.FirebaseModule
import com.example.core.firebase.OperationType
import com.example.core.firebase.handleFirestoreError
import com.example.data.model.ConnectivityCheck
import com.example.data.model.PlayerStats
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class ConnectivityVerificationResult(
    val check: ConnectivityCheck,
    val roundTripMs: Long
)

class UserProfileRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseModule.provideFirestore(context),
        auth = FirebaseModule.provideAuth()
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Firestore erişimi için Google ile giriş yapılmalıdır.")
    }

    suspend fun syncAuthenticatedProfile(user: AuthenticatedUser): Result<UserProfile> {
        val uid = requireUserId()
        require(uid == user.uid) { "Oturum UID değeri profil UID değeri ile eşleşmiyor." }
        val docRef = db.collection("users").document(uid)
        return try {
            val existingSnapshot = docRef.get().await()
            if (!existingSnapshot.exists()) {
                val createPayload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", user.displayName.take(80).ifBlank { "Rain Cube Oyuncusu" })
                    user.photoUrl?.take(500)?.let { put("photoUrl", it) }
                    user.email?.take(120)?.let { put("email", it) }
                    put("connectionStatus", "verified")
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                    put("lastLoginAt", FieldValue.serverTimestamp())
                }
                docRef.set(createPayload).await()
            } else {
                val updatePayload = buildMap<String, Any> {
                    put("displayName", user.displayName.take(80).ifBlank { "Rain Cube Oyuncusu" })
                    user.photoUrl?.take(500)?.let { put("photoUrl", it) }
                    user.email?.take(120)?.let { put("email", it) }
                    put("connectionStatus", "verified")
                    put("updatedAt", FieldValue.serverTimestamp())
                    put("lastLoginAt", FieldValue.serverTimestamp())
                }
                docRef.update(updatePayload).await()
            }
            val refreshed = docRef.get().await()
                .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(id = uid, userId = uid, displayName = user.displayName)
            Result.success(refreshed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    /**
     * Gerçek Firestore yazma ve sunucudan okuma (Source.SERVER) doğrulama testi.
     * Önbellekten sahte başarı üretmez; ağ veya kural hatası varsa gerçek hatayı döndürür.
     */
    suspend fun runConnectivityCheck(
        appVersion: String,
        note: String = "Rain Cube Firestore yazma/okuma doğrulaması",
        checkId: String = "check_${System.currentTimeMillis()}"
    ): Result<ConnectivityVerificationResult> {
        val uid = requireUserId()
        val docRef = db.collection("users")
            .document(uid)
            .collection("diagnostics")
            .document(checkId)

        val startNs = System.nanoTime()
        return try {
            val payload = mapOf(
                "userId" to uid,
                "note" to note.take(120),
                "platform" to "Android",
                "appVersion" to appVersion.take(30).ifBlank { "1.0" },
                "checkedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            val serverSnapshot = docRef.get(Source.SERVER).await()
            val roundTripMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
            val check = serverSnapshot.toObject(
                ConnectivityCheck::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: throw IllegalStateException("Sunucu doğrulama belgesi okunamadı.")
            Result.success(ConnectivityVerificationResult(check = check, roundTripMs = roundTripMs))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeDiagnostics(userId: String): Flow<List<ConnectivityCheck>> = flow {
        val colRef = db.collection("users").document(userId).collection("diagnostics")
        val path = "users/$userId/diagnostics"
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ConnectivityCheck::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.checkedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePlayerStats(userId: String): Flow<PlayerStats?> = flow {
        val docRef = db.collection("users").document(userId).collection("stats").document("summary")
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        PlayerStats::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }
}
