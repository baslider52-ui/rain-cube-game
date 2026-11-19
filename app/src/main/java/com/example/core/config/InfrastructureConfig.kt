package com.example.core.config

import android.annotation.SuppressLint
import android.content.Context
import com.example.BuildConfig
import com.example.core.firebase.FirebaseModule

/**
 * Merkezi uygulama ve ortam yapılandırma okuyucusu.
 * Gizli anahtarlar veya Firebase proje kimlikleri kod içine sabitlenmez;
 * derleme zamanında google-services.json ve firebase_applet_config.xml'den çözülür.
 */
data class InfrastructureConfigSnapshot(
    val applicationId: String,
    val versionName: String,
    val versionCode: Int,
    val isDebugBuild: Boolean,
    val firestoreDatabaseId: String?,
    val isWebClientIdConfigured: Boolean,
    val isFirebaseAppConfigured: Boolean
)

object InfrastructureConfig {

    @SuppressLint("DiscouragedApi")
    fun load(context: Context): InfrastructureConfigSnapshot {
        val databaseId = FirebaseModule.resolveDatabaseId(context).takeIf { it.isNotBlank() }

        val webClientResId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        val hasWebClientId = webClientResId != 0 && runCatching {
            context.getString(webClientResId).isNotBlank()
        }.getOrDefault(false)

        val googleAppIdResId = context.resources.getIdentifier(
            "google_app_id",
            "string",
            context.packageName
        )
        val hasGoogleAppId = googleAppIdResId != 0 && runCatching {
            context.getString(googleAppIdResId).isNotBlank()
        }.getOrDefault(false)

        return InfrastructureConfigSnapshot(
            applicationId = BuildConfig.APPLICATION_ID,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            isDebugBuild = BuildConfig.DEBUG,
            firestoreDatabaseId = databaseId,
            isWebClientIdConfigured = hasWebClientId,
            isFirebaseAppConfigured = hasGoogleAppId
        )
    }
}
