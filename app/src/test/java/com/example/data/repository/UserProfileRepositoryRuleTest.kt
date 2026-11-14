package com.example.data.repository

import com.example.auth.AuthenticatedUser
import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class UserProfileRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun syncAuthenticatedProfile_authenticatedOwner_writesAndReadsProfile() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = UserProfileRepository(db = firestore, auth = auth)
        val user = AuthenticatedUser(
            uid = uid,
            displayName = "Alice RainCube",
            email = ALICE_EMAIL,
            photoUrl = null,
            isEmailVerified = true
        )

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.syncAuthenticatedProfile(user)
        }
        assertTrue("Expected profile sync success, got ${result.exceptionOrNull()}", result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals(uid, profile.userId)
        assertEquals("Alice RainCube", profile.displayName)
    }

    @Test
    fun runConnectivityCheck_authenticatedOwner_writesAndReadsFromServer() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = UserProfileRepository(db = firestore, auth = auth)
        val checkId = "check_${UUID.randomUUID().toString().replace("-", "")}"

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.runConnectivityCheck(
                appVersion = "1.0",
                note = "Robolectric Firestore emulator write-read test",
                checkId = checkId
            )
        }
        assertTrue("Expected connectivity check success, got ${result.exceptionOrNull()}", result.isSuccess)
        val verification = result.getOrThrow()
        assertEquals(uid, verification.check.userId)
        assertEquals(checkId, verification.check.id)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeDiagnostics(uid).first { list -> list.any { it.id == checkId } }
        }
        assertTrue(observed.any { it.id == checkId })
    }

    @Test
    fun submitPendingMatch_authenticatedOwner_createsAndQueriesMatchRecord() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val matchRepo = MatchRecordRepository(db = firestore, auth = auth)
        val matchId = "match_${UUID.randomUUID().toString().replace("-", "")}"

        val submitResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            matchRepo.submitPendingMatch(
                matchId = matchId,
                mode = "classic",
                score = 420L,
                durationMs = 15000L,
                clientVersion = "1.0"
            )
        }
        assertTrue("Expected match submit success, got ${submitResult.exceptionOrNull()}", submitResult.isSuccess)

        val listResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            matchRepo.fetchUserMatchRecords()
        }
        assertTrue(listResult.isSuccess)
        val match = listResult.getOrThrow().firstOrNull { it.id == matchId }
        assertNotNull(match)
        assertEquals(uid, match?.userId)
        assertEquals("pending_verification", match?.status)
        assertEquals(0L, match?.xpEarned)
    }

    @Test
    fun crossUserProfileRead_otherUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = UserProfileRepository(db = firestore, auth = auth)
        val aliceUser = AuthenticatedUser(
            uid = aliceUid,
            displayName = "Alice",
            email = ALICE_EMAIL,
            photoUrl = null,
            isEmailVerified = true
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.syncAuthenticatedProfile(aliceUser).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid).get().await()
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice's profile")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
                ?: error("Expected FirebaseFirestoreException, got $e")
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
        }
    }

    @Test
    fun observeUserProfile_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = UserProfileRepository(db = firestore, auth = auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeUserProfile("some_uid_123").first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated profile observation")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
                ?: error("Expected nested FirebaseFirestoreException, got $e")
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice@raincube.test"
        const val BOB_EMAIL = "bob@raincube.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
