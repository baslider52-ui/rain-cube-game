package com.example.data.repository

import android.content.Context
import com.example.core.firebase.FirebaseModule
import com.example.core.firebase.OperationType
import com.example.core.firebase.handleFirestoreError
import com.example.data.model.MatchRecord
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Oyun kayıtları deposu.
 * İstemci yalnızca 'pending_verification' ve xpEarned = 0 ile kayıt gönderebilir;
 * doğrulama ve XP hesaplaması Cloud Functions tarafından yapılır.
 */
class MatchRecordRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseModule.provideFirestore(context),
        auth = FirebaseModule.provideAuth()
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Oyun kaydı için Google ile giriş yapılmalıdır.")
    }

    suspend fun submitPendingMatch(
        matchId: String,
        mode: String,
        score: Long,
        durationMs: Long,
        clientVersion: String
    ): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection("match_records").document(matchId)
        val payload = mapOf(
            "userId" to uid,
            "mode" to mode,
            "score" to score,
            "xpEarned" to 0L,
            "durationMs" to durationMs,
            "status" to "pending_verification",
            "clientVersion" to clientVersion,
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun fetchUserMatchRecords(): Result<List<MatchRecord>> {
        val uid = requireUserId()
        val path = "match_records"
        return try {
            val snapshot = db.collection(path)
                .whereEqualTo("userId", uid)
                .get()
                .await()
            val records = snapshot.toObjects(
                MatchRecord::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            Result.success(records)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }
}
