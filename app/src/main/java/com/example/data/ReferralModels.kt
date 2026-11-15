package com.example.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val referralCode: String = "",
    val referredByCode: String? = null,
    val referredByUid: String? = null,
    val lastClaimedRedeemerUid: String? = null,
    val bonusShields: Long = 0L,
    val starShards: Long = 0L,
    val referralCount: Long = 0L,
    val bestScore: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ReferralCodeEntry(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val ownerDisplayName: String = "",
    val code: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ReferralRedemption(
    @DocumentId val id: String = "",
    val redeemerUid: String = "",
    val redeemerName: String = "",
    val code: String = "",
    val referrerUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ClaimedReferral(
    @DocumentId val id: String = "",
    val redeemerUid: String = "",
    val redeemerName: String = "",
    val rewardShields: Long = 1L,
    val rewardShards: Long = 150L,
    @ServerTimestamp val claimedAt: Timestamp? = null
)
