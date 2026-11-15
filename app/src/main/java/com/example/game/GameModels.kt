package com.example.game

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.IceBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonMint
import com.example.ui.theme.SolarGold

enum class GamePhase {
    START,
    PLAYING,
    PAUSED,
    GAME_OVER
}

enum class FallingObjectType {
    NORMAL_BALL,
    GOLDEN_BALL,
    POWER_MAGNET,
    POWER_WIDE,
    POWER_SLOW_MO,
    BOMB
}

enum class PowerUpType(
    val durationSeconds: Float,
    val label: String,
    val color: Color
) {
    MAGNET(6f, "MAGNET", ElectricViolet),
    WIDE_BASKET(8f, "WIDE", NeonMint),
    SLOW_MO(5f, "SLOW-MO", IceBlue)
}

data class FallingObject(
    val id: Long,
    val type: FallingObjectType,
    var x: Float,
    var y: Float,
    val radius: Float,
    val baseSpeed: Float,
    val color: Color,
    var rotation: Float = 0f,
    val rotationSpeed: Float = 0f,
    var sparkleTimer: Float = 0f
)

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val color: Color,
    var life: Float,
    val maxLife: Float,
    val isStar: Boolean = false
)

data class Shockwave(
    val x: Float,
    val y: Float,
    val maxRadius: Float,
    val color: Color,
    var life: Float,
    val maxLife: Float
)

data class FloatingPopup(
    var x: Float,
    var y: Float,
    val vy: Float,
    val text: String,
    val color: Color,
    val scale: Float,
    var life: Float,
    val maxLife: Float
)

data class StarDot(
    val xFraction: Float,
    val yFraction: Float,
    val radiusDp: Float,
    val twinklePhase: Float,
    val twinkleSpeed: Float,
    val alphaBase: Float
)

data class ActivePowerUpUi(
    val type: PowerUpType,
    val remainingSeconds: Float,
    val totalSeconds: Float
) {
    val progress: Float
        get() = (remainingSeconds / totalSeconds).coerceIn(0f, 1f)
}

data class GameHudState(
    val phase: GamePhase = GamePhase.START,
    val score: Int = 0,
    val highScore: Int = 0,
    val lives: Int = MAX_LIVES,
    val maxLives: Int = MAX_LIVES,
    val comboStreak: Int = 0,
    val comboMultiplier: Int = 1,
    val maxComboMultiplier: Int = 1,
    val ballsCaught: Int = 0,
    val isNewHighScore: Boolean = false,
    val isMuted: Boolean = false,
    val activePowerUps: List<ActivePowerUpUi> = emptyList()
) {
    companion object {
        const val MAX_LIVES = 3
    }
}

val NORMAL_BALL_PALETTE: List<Color> = listOf(
    NeonCyan,
    NeonMagenta,
    NeonLime
)

fun objectColorForType(type: FallingObjectType): Color = when (type) {
    FallingObjectType.NORMAL_BALL -> NeonCyan
    FallingObjectType.GOLDEN_BALL -> SolarGold
    FallingObjectType.POWER_MAGNET -> ElectricViolet
    FallingObjectType.POWER_WIDE -> NeonMint
    FallingObjectType.POWER_SLOW_MO -> IceBlue
    FallingObjectType.BOMB -> CrimsonDanger
}
