package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

class ReferralRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeReferredFriends(userId: String): Flow<List<ReferralRedemption>> = flow {
        val path = "referralRedemptions"
        emitAll(
            db.collection(path)
                .whereEqualTo("referrerUid", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ReferralRedemption::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(displayName: String, localBestScore: Int): Result<UserProfile> {
        return try {
            val uid = requireUserId()
            val userDocRef = db.collection("users").document(uid)
            val existingSnap = userDocRef.get().await()
            if (existingSnap.exists()) {
                val profile = existingSnap.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )!!
                if (localBestScore > profile.bestScore) {
                    syncBestScore(localBestScore)
                    return Result.success(profile.copy(bestScore = localBestScore.toLong()))
                }
                return Result.success(profile)
            }

            val cleanName = displayName.trim().ifBlank { "Neon Player" }.take(50)
            var attempt = 0
            var allocatedCode: String? = null
            while (attempt < 5 && allocatedCode == null) {
                val candidate = generateCandidateCode(uid, attempt)
                val codeSnap = db.collection("referralCodes").document(candidate).get().await()
                if (!codeSnap.exists()) {
                    allocatedCode = candidate
                }
                attempt++
            }
            val finalCode = allocatedCode
                ?: return Result.failure(IllegalStateException("Unable to allocate unique referral code."))

            val codePayload = mapOf(
                "ownerUid" to uid,
                "ownerDisplayName" to cleanName,
                "code" to finalCode,
                "createdAt" to FieldValue.serverTimestamp()
            )
            val userPayload = mapOf(
                "userId" to uid,
                "displayName" to cleanName,
                "referralCode" to finalCode,
                "bonusShields" to 0L,
                "starShards" to 0L,
                "referralCount" to 0L,
                "bestScore" to localBestScore.toLong().coerceAtLeast(0L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.set(db.collection("referralCodes").document(finalCode), codePayload)
            batch.set(userDocRef, userPayload)
            batch.commit().await()

            val createdSnap = userDocRef.get().await()
            val created = createdSnap.toObject(
                UserProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: UserProfile(
                id = uid,
                userId = uid,
                displayName = cleanName,
                referralCode = finalCode,
                bestScore = localBestScore.toLong().coerceAtLeast(0L)
            )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users")
            Result.failure(e)
        }
    }

    suspend fun redeemReferralCode(rawCode: String, redeemerName: String): Result<Unit> {
        val code = rawCode.trim().uppercase()
        if (!CODE_REGEX.matches(code)) {
            return Result.failure(IllegalArgumentException("Enter a valid 6–12 character referral code."))
        }
        return try {
            val uid = requireUserId()
            val userDocRef = db.collection("users").document(uid)
            val userSnap = userDocRef.get().await()
            val profile = userSnap.toObject(UserProfile::class.java)
                ?: return Result.failure(IllegalStateException("Player profile is not initialized yet."))

            if (!profile.referredByCode.isNullOrBlank()) {
                return Result.failure(IllegalStateException("You have already redeemed a referral code."))
            }

            val codeDoc = db.collection("referralCodes").document(code).get().await()
            if (!codeDoc.exists()) {
                return Result.failure(IllegalArgumentException("Referral code not found."))
            }
            val codeEntry = codeDoc.toObject(ReferralCodeEntry::class.java)
                ?: return Result.failure(IllegalArgumentException("Invalid referral code record."))

            if (codeEntry.ownerUid == uid) {
                return Result.failure(IllegalArgumentException("You cannot redeem your own referral code."))
            }

            val cleanRedeemerName = redeemerName.trim().ifBlank { profile.displayName }.take(50)
            val redemptionDocRef = db.collection("referralRedemptions").document(uid)
            val redemptionPayload = mapOf(
                "redeemerUid" to uid,
                "redeemerName" to cleanRedeemerName,
                "code" to code,
                "referrerUid" to codeEntry.ownerUid,
                "createdAt" to FieldValue.serverTimestamp()
            )
            val userUpdate = mapOf(
                "referredByCode" to code,
                "referredByUid" to codeEntry.ownerUid,
                "bonusShields" to (profile.bonusShields + 1L),
                "starShards" to (profile.starShards + REDEEM_SHARDS_REWARD),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.set(redemptionDocRef, redemptionPayload)
            batch.update(userDocRef, userUpdate)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "referralRedemptions")
            Result.failure(e)
        }
    }

    suspend fun claimPendingReferrals(): Result<Int> {
        return try {
            val uid = requireUserId()
            val redemptionsSnap = db.collection("referralRedemptions")
                .whereEqualTo("referrerUid", uid)
                .get()
                .await()
            val redemptions = redemptionsSnap.toObjects(ReferralRedemption::class.java)
            if (redemptions.isEmpty()) return Result.success(0)

            val claimedSnap = db.collection("users").document(uid)
                .collection("claimedReferrals")
                .get()
                .await()
            val alreadyClaimedIds = claimedSnap.documents.map { it.id }.toSet()

            val unclaimed = redemptions.filter { it.redeemerUid !in alreadyClaimedIds }
            if (unclaimed.isEmpty()) return Result.success(0)

            var claimedNow = 0
            val userDocRef = db.collection("users").document(uid)
            for (redemption in unclaimed) {
                val freshUser = userDocRef.get().await().toObject(UserProfile::class.java) ?: break
                val claimedDocRef = userDocRef.collection("claimedReferrals").document(redemption.redeemerUid)
                val claimPayload = mapOf(
                    "redeemerUid" to redemption.redeemerUid,
                    "redeemerName" to redemption.redeemerName.ifBlank { "Friend" }.take(50),
                    "rewardShields" to 1L,
                    "rewardShards" to REFERRER_SHARDS_REWARD,
                    "claimedAt" to FieldValue.serverTimestamp()
                )
                val userUpdate = mapOf(
                    "bonusShields" to (freshUser.bonusShields + 1L),
                    "starShards" to (freshUser.starShards + REFERRER_SHARDS_REWARD),
                    "referralCount" to (freshUser.referralCount + 1L),
                    "lastClaimedRedeemerUid" to redemption.redeemerUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val batch = db.batch()
                batch.set(claimedDocRef, claimPayload)
                batch.update(userDocRef, userUpdate)
                batch.commit().await()
                claimedNow++
            }
            Result.success(claimedNow)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users")
            Result.failure(e)
        }
    }

    suspend fun consumeBonusShield(): Result<Boolean> {
        return try {
            val uid = requireUserId()
            val userDocRef = db.collection("users").document(uid)
            val snap = userDocRef.get().await()
            val profile = snap.toObject(UserProfile::class.java) ?: return Result.success(false)
            if (profile.bonusShields < 1L) return Result.success(false)

            userDocRef.update(
                mapOf(
                    "bonusShields" to (profile.bonusShields - 1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users")
            Result.failure(e)
        }
    }

    suspend fun syncBestScore(newScore: Int): Result<Unit> {
        return try {
            val uid = requireUserId()
            val userDocRef = db.collection("users").document(uid)
            val snap = userDocRef.get().await()
            val profile = snap.toObject(UserProfile::class.java) ?: return Result.success(Unit)
            if (newScore.toLong() <= profile.bestScore) return Result.success(Unit)

            userDocRef.update(
                mapOf(
                    "bestScore" to newScore.toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users")
            Result.failure(e)
        }
    }

    companion object {
        const val REDEEM_SHARDS_REWARD = 100L
        const val REFERRER_SHARDS_REWARD = 150L
        private val CODE_REGEX = Regex("^[A-Z0-9]{6,12}$")
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun generateCandidateCode(uid: String, salt: Int): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$uid#$salt".toByteArray(Charsets.UTF_8))
            val suffix = buildString {
                for (i in 0 until 6) {
                    val idx = (digest[i].toInt() and 0xFF) % CODE_ALPHABET.length
                    append(CODE_ALPHABET[idx])
                }
            }
            return "NC$suffix"
        }
    }
}
