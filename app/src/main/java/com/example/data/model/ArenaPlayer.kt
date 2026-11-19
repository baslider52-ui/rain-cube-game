package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class ArenaPlayer(
    val userId: String = "",
    val displayName: String = "",
    val mode: String = "classic",
    val worldX: Double = 0.0,
    val worldY: Double = 0.0,
    val score: Long = 0L,
    val shieldActive: Boolean = false,
    val arenaStatus: String = "active",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
