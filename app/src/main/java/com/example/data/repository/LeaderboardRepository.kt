package com.example.data.repository

import android.content.Context
import com.example.core.firebase.FirebaseModule
import com.example.core.firebase.OperationType
import com.example.core.firebase.handleFirestoreError
import com.example.data.model.GlobalLeaderboardEntry
import com.example.data.model.WeeklyLeaderboardEntry
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Genel ve haftalık sıralama okuma deposu.
 * Sıralama belgeleri yalnızca Cloud Functions tarafından yazılır; istemci salt okunurdur.
 */
class LeaderboardRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(FirebaseModule.provideFirestore(context))

    suspend fun fetchGlobalLeaderboard(limit: Long = 50L): Result<List<GlobalLeaderboardEntry>> {
        val path = "leaderboard_global"
        return try {
            val snapshot = db.collection(path)
                .whereEqualTo("isPublic", true)
                .orderBy("bestScore", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
            val entries = snapshot.toObjects(
                GlobalLeaderboardEntry::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(entries)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun fetchWeeklyLeaderboard(
        weekId: String,
        limit: Long = 50L
    ): Result<List<WeeklyLeaderboardEntry>> {
        val path = "leaderboard_weekly/$weekId/entries"
        return try {
            val snapshot = db.collection("leaderboard_weekly")
                .document(weekId)
                .collection("entries")
                .whereEqualTo("isPublic", true)
                .orderBy("weeklyScore", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
            val entries = snapshot.toObjects(
                WeeklyLeaderboardEntry::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(entries)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }
}
