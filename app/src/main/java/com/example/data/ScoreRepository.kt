package com.example.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

interface ScoreStorage {
    fun getHighScore(): Int
    fun saveHighScore(score: Int)
    fun isMuted(): Boolean
    fun saveMuted(muted: Boolean)

    fun getOrCreateLocalReferralCode(): String = "NC7K9P2A"
    fun getLocalBonusShields(): Long = 0L
    fun setLocalBonusShields(shields: Long) {}
    fun getLocalStarShards(): Long = 0L
    fun setLocalStarShards(shards: Long) {}
    fun getLocalRedeemedCode(): String? = null
    fun setLocalRedeemedCode(code: String) {}
}

class SharedPreferencesScoreStorage(context: Context) : ScoreStorage {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getHighScore(): Int = prefs.getInt(KEY_HIGH_SCORE, 0)

    override fun saveHighScore(score: Int) {
        prefs.edit().putInt(KEY_HIGH_SCORE, score).apply()
    }

    override fun isMuted(): Boolean = prefs.getBoolean(KEY_MUTED, false)

    override fun saveMuted(muted: Boolean) {
        prefs.edit().putBoolean(KEY_MUTED, muted).apply()
    }

    override fun getOrCreateLocalReferralCode(): String {
        val existing = prefs.getString(KEY_LOCAL_REFERRAL_CODE, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = ReferralRepository.generateCandidateCode(UUID.randomUUID().toString(), 0)
        prefs.edit().putString(KEY_LOCAL_REFERRAL_CODE, generated).apply()
        return generated
    }

    override fun getLocalBonusShields(): Long = prefs.getLong(KEY_LOCAL_SHIELDS, 0L)

    override fun setLocalBonusShields(shields: Long) {
        prefs.edit().putLong(KEY_LOCAL_SHIELDS, shields.coerceAtLeast(0L)).apply()
    }

    override fun getLocalStarShards(): Long = prefs.getLong(KEY_LOCAL_SHARDS, 0L)

    override fun setLocalStarShards(shards: Long) {
        prefs.edit().putLong(KEY_LOCAL_SHARDS, shards.coerceAtLeast(0L)).apply()
    }

    override fun getLocalRedeemedCode(): String? = prefs.getString(KEY_LOCAL_REDEEMED_CODE, null)

    override fun setLocalRedeemedCode(code: String) {
        prefs.edit().putString(KEY_LOCAL_REDEEMED_CODE, code).apply()
    }

    private companion object {
        const val PREFS_NAME = "neon_catch_prefs"
        const val KEY_HIGH_SCORE = "high_score"
        const val KEY_MUTED = "sound_muted"
        const val KEY_LOCAL_REFERRAL_CODE = "local_referral_code"
        const val KEY_LOCAL_SHIELDS = "local_bonus_shields"
        const val KEY_LOCAL_SHARDS = "local_star_shards"
        const val KEY_LOCAL_REDEEMED_CODE = "local_redeemed_code"
    }
}
