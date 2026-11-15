package com.example.core.firebase

import android.content.Context
import android.os.Bundle
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Firebase servis sağlayıcı katmanı.
 * Tüm Firestore istemcileri zorunlu olarak R.string.firestore_database_id ile başlatılır.
 */
object FirebaseModule {

    fun provideFirestore(context: Context): FirebaseFirestore {
        val databaseId = context.applicationContext.getString(R.string.firestore_database_id)
        require(databaseId.isNotBlank()) {
            "firestore_database_id cannot be blank in firebase_applet_config.xml"
        }
        return FirebaseFirestore.getInstance(databaseId)
    }

    fun provideAuth(): FirebaseAuth = Firebase.auth

    fun provideAnalytics(context: Context): FirebaseAnalytics {
        return FirebaseAnalytics.getInstance(context.applicationContext)
    }
}

class AnalyticsTracker(private val analytics: FirebaseAnalytics) {
    constructor(context: Context) : this(FirebaseModule.provideAnalytics(context))

    fun logGoogleSignInSuccess(uid: String) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, "google")
            putString("uid_prefix", uid.take(8))
        }
        analytics.logEvent(FirebaseAnalytics.Event.LOGIN, bundle)
    }

    fun logSignOut() {
        analytics.logEvent("auth_sign_out", Bundle())
    }

    fun logFirestoreConnectivityVerified(latencyMs: Long) {
        val bundle = Bundle().apply {
            putLong("latency_ms", latencyMs)
            putString("status", "verified")
        }
        analytics.logEvent("firestore_connectivity_verified", bundle)
    }

    fun logInfrastructureCheck(checkName: String) {
        val bundle = Bundle().apply {
            putString("check_name", checkName)
        }
        analytics.logEvent("infrastructure_manual_ping", bundle)
    }

    fun logGameStarted(mode: String) {
        val bundle = Bundle().apply {
            putString("mode", mode)
        }
        analytics.logEvent("rain_cube_game_start", bundle)
    }

    fun logGameFinished(mode: String, score: Long, wave: Int, durationMs: Long) {
        val bundle = Bundle().apply {
            putString("mode", mode)
            putLong(FirebaseAnalytics.Param.SCORE, score)
            putLong("wave", wave.toLong())
            putLong("duration_ms", durationMs)
        }
        analytics.logEvent("rain_cube_game_over", bundle)
    }
}
