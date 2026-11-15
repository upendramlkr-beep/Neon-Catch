package com.example.game

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ReferralRedemption
import com.example.data.ReferralRepository
import com.example.data.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ReferralUiState {
    data object Loading : ReferralUiState
    data class Ready(val profile: UserProfile) : ReferralUiState
    data class Error(val message: String) : ReferralUiState
}

class ReferralViewModel(
    private val repository: ReferralRepository,
    private val currentUserId: String,
    private val displayName: String,
    private val initialLocalBestScore: Int
) : ViewModel() {

    val profileState: StateFlow<ReferralUiState> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, ReferralUiState> { profile ->
            if (profile != null) ReferralUiState.Ready(profile) else ReferralUiState.Loading
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing user profile", rootError)
            emit(ReferralUiState.Error(rootError.message ?: "Failed to load profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ReferralUiState.Loading
        )

    val referredFriends: StateFlow<List<ReferralRedemption>> = repository.observeReferredFriends(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing referred friends", rootError)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    private val _redeemStatus = MutableStateFlow<RedeemStatus>(RedeemStatus.Idle)
    val redeemStatus: StateFlow<RedeemStatus> = _redeemStatus.asStateFlow()

    private val _newlyClaimedBanner = MutableStateFlow<Int?>(null)
    val newlyClaimedBanner: StateFlow<Int?> = _newlyClaimedBanner.asStateFlow()

    private val _useShieldForNextRun = MutableStateFlow(false)
    val useShieldForNextRun: StateFlow<Boolean> = _useShieldForNextRun.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(displayName, initialLocalBestScore)
            val claimed = repository.claimPendingReferrals().getOrDefault(0)
            if (claimed > 0) {
                _newlyClaimedBanner.value = claimed
            }
        }
        viewModelScope.launch {
            referredFriends.collect { list ->
                if (list.isNotEmpty()) {
                    val claimed = repository.claimPendingReferrals().getOrDefault(0)
                    if (claimed > 0) {
                        _newlyClaimedBanner.value = claimed
                    }
                }
            }
        }
    }

    fun toggleUseShieldForNextRun(availableShields: Long) {
        if (availableShields <= 0L) {
            _useShieldForNextRun.value = false
        } else {
            _useShieldForNextRun.value = !_useShieldForNextRun.value
        }
    }

    fun consumeShieldIfSelected(onResult: (Boolean) -> Unit) {
        if (!_useShieldForNextRun.value) {
            onResult(false)
            return
        }
        _useShieldForNextRun.value = false
        viewModelScope.launch {
            val consumed = repository.consumeBonusShield().getOrDefault(false)
            onResult(consumed)
        }
    }

    fun redeemCode(rawCode: String) {
        if (_redeemStatus.value is RedeemStatus.Submitting) return
        _redeemStatus.value = RedeemStatus.Submitting
        viewModelScope.launch {
            val result = repository.redeemReferralCode(rawCode, displayName)
            _redeemStatus.value = if (result.isSuccess) {
                RedeemStatus.Success("Code redeemed! +1 Shield & +100 Shards added.")
            } else {
                RedeemStatus.Error(result.exceptionOrNull()?.localizedMessage ?: "Failed to redeem code.")
            }
        }
    }

    fun clearRedeemStatus() {
        _redeemStatus.value = RedeemStatus.Idle
    }

    fun dismissClaimedBanner() {
        _newlyClaimedBanner.value = null
    }

    fun syncBestScore(score: Int) {
        viewModelScope.launch {
            repository.syncBestScore(score)
        }
    }

    private companion object {
        const val TAG = "ReferralVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}

sealed interface RedeemStatus {
    data object Idle : RedeemStatus
    data object Submitting : RedeemStatus
    data class Success(val message: String) : RedeemStatus
    data class Error(val message: String) : RedeemStatus
}
