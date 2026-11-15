package com.example.game

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.audio.SynthesizedSoundEngine
import com.example.data.SharedPreferencesScoreStorage
import com.example.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val scoreStorage = SharedPreferencesScoreStorage(application)
    private val soundEngine = SynthesizedSoundEngine(initialMuted = scoreStorage.isMuted())
    val engine = GameEngine(scoreStorage = scoreStorage, soundPlayer = soundEngine)

    private val _hudState = MutableStateFlow(engine.snapshotHud())
    val hudState: StateFlow<GameHudState> = _hudState.asStateFlow()

    private val _localReferralState = MutableStateFlow<ReferralUiState>(loadLocalProfile())
    val localReferralState: StateFlow<ReferralUiState> = _localReferralState.asStateFlow()

    private val _localRedeemStatus = MutableStateFlow<RedeemStatus>(RedeemStatus.Idle)
    val localRedeemStatus: StateFlow<RedeemStatus> = _localRedeemStatus.asStateFlow()

    private val _useShieldForNextRun = MutableStateFlow(false)
    val useShieldForNextRun: StateFlow<Boolean> = _useShieldForNextRun.asStateFlow()

    var renderFrameTick by mutableLongStateOf(0L)
        private set

    private var lastHudSyncTime: Float = 0f

    private fun loadLocalProfile(): ReferralUiState.Ready {
        val code = scoreStorage.getOrCreateLocalReferralCode()
        return ReferralUiState.Ready(
            UserProfile(
                id = "local",
                userId = "local",
                displayName = "Player",
                referralCode = code,
                referredByCode = scoreStorage.getLocalRedeemedCode(),
                bonusShields = scoreStorage.getLocalBonusShields(),
                starShards = scoreStorage.getLocalStarShards(),
                referralCount = 0L,
                bestScore = scoreStorage.getHighScore().toLong()
            )
        )
    }

    fun toggleUseShieldForNextRun() {
        val shields = scoreStorage.getLocalBonusShields()
        if (shields <= 0L) {
            _useShieldForNextRun.value = false
        } else {
            _useShieldForNextRun.value = !_useShieldForNextRun.value
        }
    }

    fun startGameWithShieldOption() {
        val extraHearts = if (_useShieldForNextRun.value && scoreStorage.getLocalBonusShields() > 0L) {
            scoreStorage.setLocalBonusShields(scoreStorage.getLocalBonusShields() - 1L)
            _useShieldForNextRun.value = false
            _localReferralState.value = loadLocalProfile()
            1
        } else {
            _useShieldForNextRun.value = false
            0
        }
        startGame(extraStartHearts = extraHearts)
    }

    fun redeemLocalReferralCode(rawCode: String) {
        val code = rawCode.trim().uppercase()
        if (!Regex("^[A-Z0-9]{6,12}$").matches(code)) {
            _localRedeemStatus.value = RedeemStatus.Error("Enter a valid 6–12 character referral code.")
            return
        }
        val ownCode = scoreStorage.getOrCreateLocalReferralCode()
        if (code == ownCode) {
            _localRedeemStatus.value = RedeemStatus.Error("You cannot redeem your own referral code.")
            return
        }
        if (!scoreStorage.getLocalRedeemedCode().isNullOrBlank()) {
            _localRedeemStatus.value = RedeemStatus.Error("You have already redeemed a referral code.")
            return
        }
        scoreStorage.setLocalRedeemedCode(code)
        scoreStorage.setLocalBonusShields(scoreStorage.getLocalBonusShields() + 1L)
        scoreStorage.setLocalStarShards(scoreStorage.getLocalStarShards() + 100L)
        _localReferralState.value = loadLocalProfile()
        _localRedeemStatus.value = RedeemStatus.Success("Code redeemed! +1 Shield & +100 Shards added.")
    }

    fun clearLocalRedeemStatus() {
        _localRedeemStatus.value = RedeemStatus.Idle
    }

    fun updateViewport(widthDp: Float, heightDp: Float, bottomSafeDp: Float) {
        engine.updateViewport(widthDp, heightDp, bottomSafeDp)
    }

    fun startGame(extraStartHearts: Int = 0) {
        engine.startNewGame(extraStartHearts)
        syncHudNow()
    }

    fun pauseGame() {
        engine.pauseGame()
        syncHudNow()
    }

    fun resumeGame() {
        engine.resumeGame()
        syncHudNow()
    }

    fun returnToMenu() {
        engine.returnToStart()
        syncHudNow()
    }

    fun toggleMute() {
        engine.toggleMute()
        syncHudNow()
    }

    fun setArrowKeys(left: Boolean, right: Boolean) {
        engine.setArrowKeys(left, right)
    }

    fun moveBasketTo(xDp: Float) {
        engine.moveBasketTo(xDp)
    }

    fun onFrame(deltaSeconds: Float) {
        val prevScore = engine.score
        val prevLives = engine.lives
        val prevCombo = engine.comboMultiplier
        val prevPhase = engine.phase
        val prevPowerCount = _hudState.value.activePowerUps.size

        engine.tick(deltaSeconds)
        renderFrameTick++

        val hasPowerUps = _hudState.value.activePowerUps.isNotEmpty() ||
            PowerUpType.entries.any { engine.isPowerUpActive(it) }

        val shouldSync = engine.score != prevScore ||
            engine.lives != prevLives ||
            engine.comboMultiplier != prevCombo ||
            engine.phase != prevPhase ||
            (hasPowerUps && (engine.elapsedTime - lastHudSyncTime >= 0.05f || prevPowerCount == 0))

        if (shouldSync) {
            syncHudNow()
        }
    }

    private fun syncHudNow() {
        lastHudSyncTime = engine.elapsedTime
        _hudState.value = engine.snapshotHud()
    }

    override fun onCleared() {
        super.onCleared()
        soundEngine.release()
    }
}
