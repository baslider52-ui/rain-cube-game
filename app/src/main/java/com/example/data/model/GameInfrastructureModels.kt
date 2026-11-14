package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val email: String? = null,
    val preferredLocale: String? = null,
    val connectionStatus: String = "verified",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @ServerTimestamp val lastLoginAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ConnectivityCheck(
    @DocumentId val id: String = "",
    val userId: String = "",
    val note: String = "",
    val platform: String = "Android",
    val appVersion: String = "1.0",
    @ServerTimestamp val checkedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PlayerStats(
    @DocumentId val id: String = "",
    val userId: String = "",
    val xp: Long = 0L,
    val level: Long = 1L,
    val totalScore: Long = 0L,
    val bestScore: Long = 0L,
    val matchesPlayed: Long = 0L,
    val wins: Long = 0L,
    val serverVerified: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class GlobalLeaderboardEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val bestScore: Long = 0L,
    val xp: Long = 0L,
    val level: Long = 1L,
    val rank: Long = 0L,
    val isPublic: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class WeeklyLeaderboardEntry(
    @DocumentId val id: String = "",
    val weekId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val weeklyScore: Long = 0L,
    val matchesCount: Long = 0L,
    val rank: Long = 0L,
    val isPublic: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MatchRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val mode: String = "classic",
    val score: Long = 0L,
    val xpEarned: Long = 0L,
    val durationMs: Long = 0L,
    val status: String = "pending_verification",
    val clientVersion: String = "1.0",
    @ServerTimestamp val createdAt: Timestamp? = null,
    val verifiedAt: Timestamp? = null
)
