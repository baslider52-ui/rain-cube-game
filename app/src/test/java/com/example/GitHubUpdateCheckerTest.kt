package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.update.GitHubUpdateChecker
import com.example.update.UpdatePrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GitHubUpdateCheckerTest {

    @Test
    fun `normalizeRepoSlug accepts slug and full GitHub URLs`() {
        assertEquals("ornek-kullanici/rain-cube", GitHubUpdateChecker.normalizeRepoSlug("ornek-kullanici/rain-cube"))
        assertEquals(
            "ornek-kullanici/rain-cube",
            GitHubUpdateChecker.normalizeRepoSlug("https://github.com/ornek-kullanici/rain-cube")
        )
        assertEquals(
            "ornek-kullanici/rain-cube",
            GitHubUpdateChecker.normalizeRepoSlug("https://github.com/ornek-kullanici/rain-cube/releases/latest")
        )
        assertEquals(
            "ornek-kullanici/rain-cube",
            GitHubUpdateChecker.normalizeRepoSlug("https://github.com/ornek-kullanici/rain-cube.git")
        )
        assertNull(GitHubUpdateChecker.normalizeRepoSlug("sadece-tek-kelime"))
        assertNull(GitHubUpdateChecker.normalizeRepoSlug(""))
        assertNull(GitHubUpdateChecker.normalizeRepoSlug("../bad"))
    }

    @Test
    fun `isNewerVersion compares semantic versions accurately including v dot prefix`() {
        assertTrue(GitHubUpdateChecker.isNewerVersion("v.1.0.2", "1.0.1"))
        assertTrue(GitHubUpdateChecker.isNewerVersion("v.1.1.0", "v.1.0.12"))
        assertTrue(GitHubUpdateChecker.isNewerVersion("v.2.0.0", "v.1.21.12"))
        assertTrue(GitHubUpdateChecker.isNewerVersion("v.1.0.10", "v.1.0.2"))
        assertFalse(GitHubUpdateChecker.isNewerVersion("v.1.0.1", "1.0.1"))
        assertFalse(GitHubUpdateChecker.isNewerVersion("v.1.0.12", "1.1.0"))
        assertFalse(GitHubUpdateChecker.isNewerVersion("invalid", "1.0.1"))
    }

    @Test
    fun `nextVersion rolls over at patch 12 and minor 21`() {
        assertEquals(Triple(1, 0, 1), GitHubUpdateChecker.nextVersion(1, 0, 0))
        assertEquals(Triple(1, 0, 12), GitHubUpdateChecker.nextVersion(1, 0, 11))
        assertEquals(Triple(1, 1, 0), GitHubUpdateChecker.nextVersion(1, 0, 12))
        assertEquals(Triple(1, 21, 12), GitHubUpdateChecker.nextVersion(1, 21, 11))
        assertEquals(Triple(2, 0, 0), GitHubUpdateChecker.nextVersion(1, 21, 12))
    }

    @Test
    fun `formatVersionTag normalizes plain and v-prefixed versions to v dot format`() {
        assertEquals("v.1.0.0", GitHubUpdateChecker.formatVersionTag("1.0.0"))
        assertEquals("v.1.1.0", GitHubUpdateChecker.formatVersionTag("v1.1.0"))
        assertEquals("v.1.21.12", GitHubUpdateChecker.formatVersionTag("v.1.21.12"))
    }

    @Test
    fun `parseLatestReleaseJson extracts apk asset and version comparison`() {
        val sampleJson = """
            {
              "tag_name": "v1.0.3",
              "name": "Rain Cube v1.0.3",
              "body": "## Rain Cube v1.0.3 Yayınlandı!\n- Yeni güncelleme kontrolü",
              "html_url": "https://github.com/ornek-kullanici/rain-cube/releases/tag/v1.0.3",
              "assets": [
                {
                  "name": "notes.txt",
                  "browser_download_url": "https://github.com/ornek-kullanici/rain-cube/releases/download/v1.0.3/notes.txt",
                  "size": 120
                },
                {
                  "name": "RainCube-v1.0.3.apk",
                  "browser_download_url": "https://github.com/ornek-kullanici/rain-cube/releases/download/v1.0.3/RainCube-v1.0.3.apk",
                  "size": 14800000
                }
              ]
            }
        """.trimIndent()

        val release = GitHubUpdateChecker.parseLatestReleaseJson(sampleJson, currentVersion = "1.0.1")
        assertNotNull(release)
        assertEquals("v1.0.3", release!!.tagName)
        assertEquals("Rain Cube v1.0.3", release.releaseTitle)
        assertEquals("RainCube-v1.0.3.apk", release.apkFileName)
        assertEquals(
            "https://github.com/ornek-kullanici/rain-cube/releases/download/v1.0.3/RainCube-v1.0.3.apk",
            release.apkDownloadUrl
        )
        assertEquals(14_800_000L, release.apkSizeBytes)
        assertTrue(release.isUpdateAvailable)

        val sameVersion = GitHubUpdateChecker.parseLatestReleaseJson(sampleJson, currentVersion = "1.0.3")
        assertNotNull(sameVersion)
        assertFalse(sameVersion!!.isUpdateAvailable)
    }

    @Test
    fun `parseLatestReleaseJson supports array payload from releases list`() {
        val listJson = """
            [
              {
                "tag_name": "v1.0.4",
                "name": "Rain Cube v1.0.4",
                "draft": false,
                "html_url": "https://github.com/ornek-kullanici/rain-cube/releases/tag/v1.0.4",
                "assets": [
                  {
                    "name": "RainCube-v1.0.4.apk",
                    "browser_download_url": "https://github.com/ornek-kullanici/rain-cube/releases/download/v1.0.4/RainCube-v1.0.4.apk",
                    "size": 15200000
                  }
                ]
              }
            ]
        """.trimIndent()

        val release = GitHubUpdateChecker.parseLatestReleaseJson(listJson, currentVersion = "1.0.2")
        assertNotNull(release)
        assertEquals("v1.0.4", release!!.tagName)
        assertTrue(release.isUpdateAvailable)
    }

    @Test
    fun `UpdatePrefs persists normalized custom repo slug`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val saved = UpdatePrefs.saveCustomRepoSlug(context, "https://github.com/oyuncu/rain-cube")
        assertEquals("oyuncu/rain-cube", saved)
        assertEquals("oyuncu/rain-cube", UpdatePrefs.getEffectiveRepoSlug(context, ""))

        UpdatePrefs.saveCustomRepoSlug(context, "")
        assertEquals("ci/rain-cube", UpdatePrefs.getEffectiveRepoSlug(context, "ci/rain-cube"))
    }
}
