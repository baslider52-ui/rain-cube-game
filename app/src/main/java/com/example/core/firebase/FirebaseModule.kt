package com.example.core.firebase

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Firebase servis sağlayıcı katmanı.
 * Tüm Firestore istemcileri zorunlu olarak firestore_database_id kaynağı ile başlatılır.
 */
object FirebaseModule {

    @SuppressLint("DiscouragedApi")
    fun resolveDatabaseId(context: Context): String {
        val appContext = context.applicationContext
        val candidatePackages = listOfNotNull(
            appContext.packageName,
            appContext.applicationInfo?.packageName,
            "com.example"
        ).distinct()

        for (pkg in candidatePackages) {
            val resId = appContext.resources.getIdentifier("firestore_database_id", "string", pkg)
            if (resId != 0) {
                val value = runCatching { appContext.getString(resId) }.getOrNull()
                if (!value.isNullOrBlank()) return value
            }
        }
        return ""
    }

    fun provideFirestore(context: Context): FirebaseFirestore {
        val databaseId = resolveDatabaseId(context)
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
