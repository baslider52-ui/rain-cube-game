package com.example.update

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class GitHubReleaseInfo(
    val tagName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val releasePageUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long,
    val isUpdateAvailable: Boolean
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: GitHubReleaseInfo) : UpdateCheckResult
    data class UpToDate(val release: GitHubReleaseInfo) : UpdateCheckResult
    data class NotFound(val repoSlug: String) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

object UpdatePrefs {
    private const val PREFS_NAME = "rain_cube_update_prefs"
    private const val KEY_CUSTOM_REPO_SLUG = "custom_github_repo_slug"

    fun getEffectiveRepoSlug(context: Context, buildConfigSlug: String): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_CUSTOM_REPO_SLUG, null).orEmpty()
        return GitHubUpdateChecker.normalizeRepoSlug(saved)
            ?: GitHubUpdateChecker.normalizeRepoSlug(buildConfigSlug)
            ?: ""
    }

    fun saveCustomRepoSlug(context: Context, rawInput: String): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().remove(KEY_CUSTOM_REPO_SLUG).apply()
            return ""
        }
        val normalized = GitHubUpdateChecker.normalizeRepoSlug(trimmed) ?: return null
        prefs.edit().putString(KEY_CUSTOM_REPO_SLUG, normalized).apply()
        return normalized
    }
}

object GitHubUpdateChecker {
    private val SEGMENT_REGEX = Regex("^[A-Za-z0-9._-]+$")
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000

    fun normalizeRepoSlug(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        var cleaned = trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
        if (cleaned.startsWith("github.com/", ignoreCase = true)) {
            cleaned = cleaned.substring("github.com/".length)
        } else if (cleaned.equals("github.com", ignoreCase = true)) {
            return null
        }

        val parts = cleaned.trim('/')
            .split('/')
            .filter { it.isNotBlank() }
        if (parts.size < 2) return null

        val owner = parts[0].trim()
        val repo = parts[1].trim().removeSuffix(".git")
        if (owner == "." || owner == ".." || repo == "." || repo == "..") return null
        if (!SEGMENT_REGEX.matches(owner) || !SEGMENT_REGEX.matches(repo)) return null

        return "$owner/$repo"
    }

    fun parseSemVer(raw: String): List<Int> {
        val cleaned = raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
            .trim()
        if (cleaned.isEmpty()) return emptyList()

        val numbers = cleaned.split('.')
            .mapNotNull { segment ->
                val digits = segment.takeWhile { it.isDigit() }
                digits.toIntOrNull()
            }
        return numbers
    }

    fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val remoteParts = parseSemVer(remoteVersion)
        val currentParts = parseSemVer(currentVersion)
        if (remoteParts.isEmpty() || currentParts.isEmpty()) return false

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    fun releasesPageUrl(repoSlug: String): String {
        val normalized = normalizeRepoSlug(repoSlug)
        return if (normalized != null) {
            "https://github.com/$normalized/releases"
        } else {
            "https://github.com"
        }
    }

    fun formatApkSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            val kb = (bytes / 1024L).coerceAtLeast(1L)
            "$kb KB"
        }
    }

    fun parseLatestReleaseJson(
        jsonBody: String,
        currentVersion: String,
        fallbackRepoSlug: String = ""
    ): GitHubReleaseInfo? {
        val trimmed = jsonBody.trim()
        if (trimmed.isEmpty()) return null

        val releaseObj = runCatching {
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                if (arr.length() == 0) return null
                var chosen: JSONObject? = null
                for (i in 0 until arr.length()) {
                    val candidate = arr.optJSONObject(i) ?: continue
                    if (!candidate.optBoolean("draft", false)) {
                        chosen = candidate
                        break
                    }
                }
                chosen ?: arr.optJSONObject(0)
            } else {
                JSONObject(trimmed)
            }
        }.getOrNull() ?: return null

        val tagName = releaseObj.optString("tag_name", "").trim()
        if (tagName.isEmpty()) return null

        val title = releaseObj.optString("name", "").trim().ifEmpty { tagName }
        val notes = releaseObj.optString("body", "").trim()
        val htmlUrl = releaseObj.optString("html_url", "").trim()
            .takeIf { it.startsWith("https://") }
            ?: releasesPageUrl(fallbackRepoSlug)

        var apkUrl: String? = null
        var apkName: String? = null
        var apkSize = 0L

        val assets = releaseObj.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val assetName = asset.optString("name", "").trim()
                val downloadUrl = asset.optString("browser_download_url", "").trim()
                if (assetName.endsWith(".apk", ignoreCase = true) &&
                    downloadUrl.startsWith("https://")
                ) {
                    apkUrl = downloadUrl
                    apkName = assetName
                    apkSize = asset.optLong("size", 0L).coerceAtLeast(0L)
                    break
                }
            }
        }

        return GitHubReleaseInfo(
            tagName = tagName,
            releaseTitle = title,
            releaseNotes = notes,
            releasePageUrl = htmlUrl,
            apkDownloadUrl = apkUrl,
            apkFileName = apkName,
            apkSizeBytes = apkSize,
            isUpdateAvailable = isNewerVersion(tagName, currentVersion)
        )
    }

    suspend fun fetchLatestRelease(
        repoSlug: String,
        currentVersion: String
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val normalized = normalizeRepoSlug(repoSlug)
            ?: return@withContext UpdateCheckResult.Error(
                "Geçerli bir GitHub deposu girin (ör. kullanici/rain-cube)."
            )

        try {
            val latestUrl = "https://api.github.com/repos/$normalized/releases/latest"
            val (code, body) = executeGet(latestUrl, currentVersion)

            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val info = parseLatestReleaseJson(body, currentVersion, normalized)
                        ?: return@withContext UpdateCheckResult.NotFound(normalized)
                    if (info.isUpdateAvailable) {
                        UpdateCheckResult.UpdateAvailable(info)
                    } else {
                        UpdateCheckResult.UpToDate(info)
                    }
                }

                HttpURLConnection.HTTP_NOT_FOUND -> {
                    val listUrl = "https://api.github.com/repos/$normalized/releases?per_page=1"
                    val (listCode, listBody) = executeGet(listUrl, currentVersion)
                    if (listCode == HttpURLConnection.HTTP_OK) {
                        val info = parseLatestReleaseJson(listBody, currentVersion, normalized)
                            ?: return@withContext UpdateCheckResult.NotFound(normalized)
                        if (info.isUpdateAvailable) {
                            UpdateCheckResult.UpdateAvailable(info)
                        } else {
                            UpdateCheckResult.UpToDate(info)
                        }
                    } else if (listCode == HttpURLConnection.HTTP_NOT_FOUND) {
                        UpdateCheckResult.NotFound(normalized)
                    } else {
                        UpdateCheckResult.Error("GitHub Releases yanıt vermedi (Kod: $listCode).")
                    }
                }

                HttpURLConnection.HTTP_FORBIDDEN, 429 -> {
                    UpdateCheckResult.Error(
                        "GitHub API istek sınırına ulaşıldı. Biraz sonra tekrar deneyin veya Releases sayfasını açın."
                    )
                }

                else -> {
                    UpdateCheckResult.Error("GitHub sürüm kontrolü başarısız (Kod: $code).")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            UpdateCheckResult.Error(
                "GitHub Releases bağlantısı kurulamadı. İnternet bağlantınızı kontrol edin."
            )
        } catch (_: Exception) {
            UpdateCheckResult.Error("Güncelleme kontrolü sırasında beklenmeyen bir sorun oluştu.")
        }
    }

    private fun executeGet(urlString: String, currentVersion: String): Pair<Int, String> {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "RainCube-Android/$currentVersion")
        }
        return try {
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            statusCode to body
        } finally {
            connection.disconnect()
        }
    }
}
