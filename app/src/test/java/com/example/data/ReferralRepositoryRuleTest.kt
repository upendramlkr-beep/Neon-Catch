package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ReferralRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserProfile_redeemAndClaimReferral_endToEndSucceeds() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val aliceEmail = "alice_$uniqueSuffix@test.com"
        val bobEmail = "bob_$uniqueSuffix@test.com"

        val aliceUid = signInTestUser(aliceEmail)
        val repo = ReferralRepository(firestore, auth)

        val aliceProfileResult = withTimeout(TIMEOUT_MS) {
            repo.ensureUserProfile("Alice", 250)
        }
        assertTrue(aliceProfileResult.isSuccess)
        val aliceProfile = aliceProfileResult.getOrThrow()
        assertEquals(aliceUid, aliceProfile.userId)
        assertEquals(250L, aliceProfile.bestScore)
        assertTrue(aliceProfile.referralCode.startsWith("NC"))

        // Switch to Bob and redeem Alice's code
        val bobUid = signInTestUser(bobEmail)
        val bobProfileResult = withTimeout(TIMEOUT_MS) {
            repo.ensureUserProfile("Bob", 0)
        }
        assertTrue(bobProfileResult.isSuccess)

        val redeemResult = withTimeout(TIMEOUT_MS) {
            repo.redeemReferralCode(aliceProfile.referralCode, "Bob")
        }
        assertTrue("Redeem failed: ${redeemResult.exceptionOrNull()}", redeemResult.isSuccess)

        // Verify Bob cannot redeem a second code or self-refer
        val selfRedeemResult = withTimeout(TIMEOUT_MS) {
            repo.redeemReferralCode(bobProfileResult.getOrThrow().referralCode, "Bob")
        }
        assertTrue(selfRedeemResult.isFailure)

        // Verify Bob received +1 shield and +100 shards
        val observedBob = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeUserProfile(bobUid).first { it?.referredByCode == aliceProfile.referralCode }
        }
        assertNotNull(observedBob)
        assertEquals(1L, observedBob!!.bonusShields)
        assertEquals(100L, observedBob.starShards)

        // Bob consumes 1 shield
        val consumed = withTimeout(TIMEOUT_MS) { repo.consumeBonusShield() }
        assertTrue(consumed.isSuccess && consumed.getOrThrow())

        // Switch back to Alice, claim pending referral reward
        signInTestUser(aliceEmail)
        val claimedCount = withTimeout(TIMEOUT_MS) { repo.claimPendingReferrals() }
        assertTrue(claimedCount.isSuccess)
        assertEquals(1, claimedCount.getOrThrow())

        val updatedAlice = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeUserProfile(aliceUid).first { (it?.referralCount ?: 0L) == 1L }
        }
        assertNotNull(updatedAlice)
        assertEquals(1L, updatedAlice!!.bonusShields)
        assertEquals(150L, updatedAlice.starShards)
    }

    @Test
    fun observeUserProfile_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = ReferralRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeUserProfile("some_uid").first()
            }
        } catch (t: Throwable) {
            caught = t
        }

        assertNotNull(caught)
        val firestoreEx = caught!!.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got $caught", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
    }

    @Test
    fun observeReferredFriends_crossUserRead_failsWithPermissionDenied() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val aliceUid = signInTestUser("alice2_$uniqueSuffix@test.com")
        signInTestUser("bob2_$uniqueSuffix@test.com")

        val repo = ReferralRepository(firestore, auth)
        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeReferredFriends(aliceUid).first()
            }
        } catch (t: Throwable) {
            caught = t
        }

        assertNotNull(caught)
        val firestoreEx = caught!!.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got $caught", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
    }

    private companion object {
        const val TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
