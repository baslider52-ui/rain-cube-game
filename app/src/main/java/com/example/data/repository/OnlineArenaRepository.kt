package com.example.data.repository

import android.content.Context
import com.example.core.firebase.FirebaseModule
import com.example.core.firebase.OperationType
import com.example.core.firebase.handleFirestoreError
import com.example.data.model.ArenaPlayer
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Gerçek zamanlı çevrimiçi arena oyuncuları deposu.
 * /arena_players/{playerId} koleksiyonunda her oyuncu yalnızca kendi dokümanını günceller
 * ve aktif oyuncuları canlı olarak dinler.
 */
class OnlineArenaRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseModule.provideFirestore(context),
        auth = FirebaseModule.provideAuth()
    )

    @Volatile
    private var hasSessionDoc = false

    fun observeActivePlayers(): Flow<List<ArenaPlayer>> = callbackFlow {
        val path = COLLECTION_ARENA_PLAYERS
        val query = db.collection(path)
            .whereEqualTo("arenaStatus", STATUS_ACTIVE)
            .limit(40)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, path)
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val nowSeconds = System.currentTimeMillis() / 1000L
                val players = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(
                        ArenaPlayer::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }.filter { player ->
                    val updatedSec = player.updatedAt?.seconds ?: nowSeconds
                    (nowSeconds - updatedSec) <= STALE_THRESHOLD_SECONDS
                }
                trySend(players)
            }
        }

        awaitClose { registration.remove() }
    }

    suspend fun syncPresence(
        displayName: String,
        mode: String,
        worldX: Float,
        worldY: Float,
        score: Long,
        shieldActive: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Çevrimiçi arena için oturum gereklidir."))
        val docRef = db.collection(COLLECTION_ARENA_PLAYERS).document(uid)
        val safeName = displayName.trim().take(80).ifEmpty { "Oyuncu" }
        val safeMode = if (mode == "rush") "rush" else "classic"
        val safeX = worldX.toDouble().coerceIn(-MAX_COORD, MAX_COORD)
        val safeY = worldY.toDouble().coerceIn(-MAX_COORD, MAX_COORD)
        val safeScore = score.coerceIn(0L, MAX_SCORE)

        val mutableFields = mapOf<String, Any>(
            "displayName" to safeName,
            "mode" to safeMode,
            "worldX" to safeX,
            "worldY" to safeY,
            "score" to safeScore,
            "shieldActive" to shieldActive,
            "arenaStatus" to STATUS_ACTIVE,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        return try {
            if (hasSessionDoc) {
                try {
                    docRef.update(mutableFields).await()
                    return Result.success(Unit)
                } catch (e: FirebaseFirestoreException) {
                    if (e.code != FirebaseFirestoreException.Code.NOT_FOUND) {
                        throw e
                    }
                    hasSessionDoc = false
                }
            }

            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(mutableFields).await()
            } else {
                val createPayload = mutableFields + mapOf(
                    "userId" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            }
            hasSessionDoc = true
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun leaveArena() {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection(COLLECTION_ARENA_PLAYERS).document(uid)
        withContext(NonCancellable) {
            runCatching {
                if (hasSessionDoc || docRef.get().await().exists()) {
                    docRef.update(
                        mapOf(
                            "arenaStatus" to STATUS_OFFLINE,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            hasSessionDoc = false
        }
    }

    private companion object {
        const val COLLECTION_ARENA_PLAYERS = "arena_players"
        const val STATUS_ACTIVE = "active"
        const val STATUS_OFFLINE = "offline"
        const val MAX_COORD = 9_999_999.0
        const val MAX_SCORE = 100_000_000L
        const val STALE_THRESHOLD_SECONDS = 40L
    }
}
