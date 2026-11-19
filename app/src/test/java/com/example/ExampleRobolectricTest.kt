package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.auth.AuthSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Rain Cube", appName)
    }

    @Test
    fun `resolve google web client id when google services present`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId != 0) {
            val clientId = AuthSessionManager.resolveWebClientId(context)
            assertFalse(clientId.isNullOrBlank())
            assertTrue(AuthSessionManager.isFirebaseInitialized(context))
        }
    }

    @Test
    fun `fresh install skips background silent sign-in to prevent Play Services cooldown`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("rain_cube_auth_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        var unauthenticatedCalled = false
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        AuthSessionManager.attemptSilentSignIn(
            context = context,
            scope = scope,
            onAuthSuccess = {},
            onUnauthenticated = { unauthenticatedCalled = true }
        )

        assertTrue(unauthenticatedCalled)
    }

    @Test
    fun `diagnostic mapper distinguishes SHA mismatch from Error 16 cooldown`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shaError = AuthSessionManager.mapDiagnosticDetailOrNull(
            context,
            "16: [28444] Developer console is not set up correctly"
        )
        assertEquals(context.getString(R.string.auth_sha_mismatch_message), shaError)

        val cooldownError = AuthSessionManager.mapDiagnosticDetailOrNull(
            context,
            "[16] Caller has been temporarily blocked due to too many canceled sign-in prompts."
        )
        assertEquals(context.getString(R.string.auth_rate_limited_message), cooldownError)
    }
}
