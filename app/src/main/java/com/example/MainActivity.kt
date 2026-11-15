package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.game.GamePhase
import com.example.game.GameViewModel
import com.example.game.ReferralUiState
import com.example.ui.components.CreatorIntroScreen
import com.example.ui.components.GameCanvas
import com.example.ui.components.GameOverOverlay
import com.example.ui.components.InGameHud
import com.example.ui.components.PauseOverlay
import com.example.ui.components.ReferralDialog
import com.example.ui.components.StartScreenOverlay
import com.example.ui.theme.NeonCatchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            NeonCatchTheme {
                var showCreatorIntro by remember { mutableStateOf(true) }
                Crossfade(
                    targetState = showCreatorIntro,
                    animationSpec = tween(durationMillis = 450),
                    label = "introCrossfade"
                ) { showingIntro ->
                    if (showingIntro) {
                        CreatorIntroScreen(onFinished = { showCreatorIntro = false })
                    } else {
                        NeonCatchApp()
                    }
                }
            }
        }
    }
}

@Composable
fun NeonCatchApp(
    gameViewModel: GameViewModel = viewModel()
) {
    val hud by gameViewModel.hudState.collectAsStateWithLifecycle()
    val referralState by gameViewModel.localReferralState.collectAsStateWithLifecycle()
    val redeemStatus by gameViewModel.localRedeemStatus.collectAsStateWithLifecycle()
    val useShieldNextRun by gameViewModel.useShieldForNextRun.collectAsStateWithLifecycle()

    var showReferralHub by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    var leftHeld by remember { mutableStateOf(false) }
    var rightHeld by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Pause automatically if app goes into background while playing
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                leftHeld = false
                rightHeld = false
                gameViewModel.setArrowKeys(left = false, right = false)
                gameViewModel.pauseGame()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Request focus so hardware keyboard Arrow Left/Right work immediately
    LaunchedEffect(hud.phase) {
        if (hud.phase == GamePhase.PLAYING) {
            focusRequester.requestFocus()
        }
    }

    // 60+ FPS delta-time game loop
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = (frameNanos - lastNanos) / 1_000_000_000f
                    gameViewModel.onFrame(dt)
                }
                lastNanos = frameNanos
            }
        }
    }

    BackHandler(enabled = showReferralHub || hud.phase != GamePhase.START) {
        if (showReferralHub) {
            showReferralHub = false
        } else {
            when (hud.phase) {
                GamePhase.PLAYING -> gameViewModel.pauseGame()
                GamePhase.PAUSED -> gameViewModel.resumeGame()
                GamePhase.GAME_OVER -> gameViewModel.returnToMenu()
                GamePhase.START -> Unit
            }
        }
    }

    val profile = (referralState as? ReferralUiState.Ready)?.profile
    val bonusShields = profile?.bonusShields ?: 0L
    val starShards = profile?.starShards ?: 0L

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                val isDown = keyEvent.type == KeyEventType.KeyDown
                val isUp = keyEvent.type == KeyEventType.KeyUp
                when (keyEvent.key) {
                    Key.DirectionLeft, Key.A -> {
                        if (isDown) leftHeld = true else if (isUp) leftHeld = false
                        gameViewModel.setArrowKeys(leftHeld, rightHeld)
                        true
                    }
                    Key.DirectionRight, Key.D -> {
                        if (isDown) rightHeld = true else if (isUp) rightHeld = false
                        gameViewModel.setArrowKeys(leftHeld, rightHeld)
                        true
                    }
                    Key.P, Key.Escape -> {
                        if (isDown) {
                            if (hud.phase == GamePhase.PLAYING) gameViewModel.pauseGame()
                            else if (hud.phase == GamePhase.PAUSED) gameViewModel.resumeGame()
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        val bottomInsetDp = WindowInsets.navigationBars.asPaddingValues()
            .calculateBottomPadding().value
        val widthDp = maxWidth.value
        val heightDp = maxHeight.value

        LaunchedEffect(widthDp, heightDp, bottomInsetDp) {
            gameViewModel.updateViewport(widthDp, heightDp, bottomInsetDp)
        }

        // Full-bleed 60 FPS Playfield Canvas
        GameCanvas(
            engine = gameViewModel.engine,
            frameTick = gameViewModel.renderFrameTick,
            onPointerMoveX = { xDp -> gameViewModel.moveBasketTo(xDp) },
            modifier = Modifier.fillMaxSize()
        )

        // In-Game HUD (shown during Playing and Paused)
        if (hud.phase == GamePhase.PLAYING || hud.phase == GamePhase.PAUSED) {
            InGameHud(
                hud = hud,
                onPause = { gameViewModel.pauseGame() },
                onToggleMute = { gameViewModel.toggleMute() }
            )
        }

        // Start Screen with TAP TO PLAY
        AnimatedVisibility(
            visible = hud.phase == GamePhase.START,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            StartScreenOverlay(
                highScore = hud.highScore,
                isMuted = hud.isMuted,
                bonusShields = bonusShields,
                starShards = starShards,
                useShieldForNextRun = useShieldNextRun,
                onToggleUseShield = { gameViewModel.toggleUseShieldForNextRun() },
                onOpenReferralHub = { showReferralHub = true },
                onPlayClick = { gameViewModel.startGameWithShieldOption() },
                onToggleMute = { gameViewModel.toggleMute() }
            )
        }

        // Pause Overlay
        AnimatedVisibility(
            visible = hud.phase == GamePhase.PAUSED,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PauseOverlay(
                onResume = { gameViewModel.resumeGame() },
                onRestart = { gameViewModel.startGame() },
                onMainMenu = { gameViewModel.returnToMenu() }
            )
        }

        // Game Over Overlay
        AnimatedVisibility(
            visible = hud.phase == GamePhase.GAME_OVER,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            GameOverOverlay(
                hud = hud,
                onPlayAgain = { gameViewModel.startGameWithShieldOption() },
                onMainMenu = { gameViewModel.returnToMenu() },
                onOpenReferralHub = { showReferralHub = true },
                onToggleMute = { gameViewModel.toggleMute() }
            )
        }

        if (showReferralHub) {
            ReferralDialog(
                referralState = referralState,
                referredFriends = emptyList(),
                redeemStatus = redeemStatus,
                onRedeemCode = { code -> gameViewModel.redeemLocalReferralCode(code) },
                onClearRedeemStatus = { gameViewModel.clearLocalRedeemStatus() },
                onDismiss = { showReferralHub = false }
            )
        }
    }
}
