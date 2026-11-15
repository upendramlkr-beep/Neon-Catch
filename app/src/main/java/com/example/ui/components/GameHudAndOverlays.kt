package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.ActivePowerUpUi
import com.example.game.FallingObjectType
import com.example.game.GameHudState
import com.example.game.GamePhase
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.IceBlue
import com.example.ui.theme.MidnightBgTop
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonMint
import com.example.ui.theme.SolarGold
import com.example.ui.theme.SolarGoldBright
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun MuteToggleButton(
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = SurfaceCard,
        border = BorderStroke(1.dp, SurfaceBorder),
        modifier = modifier
    ) {
        IconButton(
            onClick = onToggleMute,
            modifier = Modifier
                .size(48.dp)
                .testTag("mute_button")
        ) {
            Icon(
                imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = stringResource(
                    if (isMuted) R.string.cd_unmute else R.string.cd_mute
                ),
                tint = if (isMuted) TextMuted else NeonCyan
            )
        }
    }
}

@Composable
fun InGameHud(
    hud: GameHudState,
    onPause: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Score + High Score + Combo Pill
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = SurfaceCard,
                border = BorderStroke(1.dp, SurfaceBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.score_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = hud.score.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary,
                            modifier = Modifier.testTag("hud_score")
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.high_score_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        Text(
                            text = hud.highScore.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = SolarGold,
                            modifier = Modifier.testTag("hud_high_score")
                        )
                    }
                    AnimatedVisibility(
                        visible = hud.comboMultiplier > 1,
                        enter = scaleIn() + fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SolarGold.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, SolarGold)
                        ) {
                            Text(
                                text = "${hud.comboMultiplier}x",
                                style = MaterialTheme.typography.labelLarge,
                                color = SolarGoldBright,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("hud_combo_badge")
                            )
                        }
                    }
                }
            }

            // Right: Hearts + Pause + Mute
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = SurfaceCard,
                    border = BorderStroke(1.dp, CrimsonDanger.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .testTag("hud_lives"),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(hud.maxLives) { index ->
                            val alive = index < hud.lives
                            val isShieldHeart = index >= GameHudState.MAX_LIVES
                            val tint = when {
                                alive && isShieldHeart -> NeonMint
                                alive -> CrimsonDanger
                                else -> TextMuted.copy(alpha = 0.45f)
                            }
                            Icon(
                                imageVector = if (alive) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = stringResource(
                                    if (alive) R.string.cd_life_full else R.string.cd_life_empty
                                ),
                                tint = tint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (hud.phase == GamePhase.PLAYING) {
                    Surface(
                        shape = CircleShape,
                        color = SurfaceCard,
                        border = BorderStroke(1.dp, SurfaceBorder)
                    ) {
                        IconButton(
                            onClick = onPause,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("pause_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Pause,
                                contentDescription = stringResource(R.string.cd_pause),
                                tint = TextPrimary
                            )
                        }
                    }
                }

                MuteToggleButton(isMuted = hud.isMuted, onToggleMute = onToggleMute)
            }
        }

        // Active Power-Up Countdown Bars
        if (hud.activePowerUps.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                hud.activePowerUps.forEach { power ->
                    PowerUpCountdownPill(
                        powerUp = power,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PowerUpCountdownPill(
    powerUp: ActivePowerUpUi,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, powerUp.type.color.copy(alpha = 0.7f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = powerUp.type.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = powerUp.type.color
                )
                Text(
                    text = String.format(Locale.US, "%.1fs", powerUp.remainingSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary
                )
            }
            LinearProgressIndicator(
                progress = { powerUp.progress },
                color = powerUp.type.color,
                trackColor = powerUp.type.color.copy(alpha = 0.2f),
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
fun StartScreenOverlay(
    highScore: Int,
    isMuted: Boolean,
    bonusShields: Long,
    starShards: Long,
    useShieldForNextRun: Boolean,
    onToggleUseShield: () -> Unit,
    onOpenReferralHub: () -> Unit,
    onPlayClick: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "titlePulse")
    val playPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "playScale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .navigationBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Utility Bar (Shields + Star Shards + Mute)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = SurfaceCard,
                border = BorderStroke(1.dp, SurfaceBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = NeonMint,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = bonusShields.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonMint,
                            modifier = Modifier.testTag("start_shields_count")
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = SolarGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = starShards.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = SolarGoldBright
                        )
                    }
                }
            }

            MuteToggleButton(
                isMuted = isMuted,
                onToggleMute = onToggleMute
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Game Title
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.game_title),
                        style = MaterialTheme.typography.displayLarge.copy(
                            brush = Brush.linearGradient(listOf(NeonCyan, SolarGold, NeonMagenta))
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.game_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }

                // High Score Badge
                Surface(
                    shape = RoundedCornerShape(50),
                    color = SurfaceCard,
                    border = BorderStroke(1.dp, SolarGold.copy(alpha = 0.65f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EmojiEvents,
                            contentDescription = null,
                            tint = SolarGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${stringResource(R.string.high_score_label)}: $highScore",
                            style = MaterialTheme.typography.labelLarge,
                            color = SolarGoldBright,
                            modifier = Modifier.testTag("start_high_score")
                        )
                    }
                }

                // Object Legend Card
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = SurfaceCard,
                    border = BorderStroke(1.dp, SurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.legend_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonCyan
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LegendItem(
                                type = FallingObjectType.NORMAL_BALL,
                                title = stringResource(R.string.legend_normal_title),
                                desc = stringResource(R.string.legend_normal_desc),
                                modifier = Modifier.weight(1f)
                            )
                            LegendItem(
                                type = FallingObjectType.GOLDEN_BALL,
                                title = stringResource(R.string.legend_golden_title),
                                desc = stringResource(R.string.legend_golden_desc),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LegendItem(
                                type = FallingObjectType.POWER_MAGNET,
                                title = stringResource(R.string.legend_magnet_title),
                                desc = stringResource(R.string.legend_magnet_desc),
                                modifier = Modifier.weight(1f)
                            )
                            LegendItem(
                                type = FallingObjectType.POWER_WIDE,
                                title = stringResource(R.string.legend_wide_title),
                                desc = stringResource(R.string.legend_wide_desc),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LegendItem(
                                type = FallingObjectType.POWER_SLOW_MO,
                                title = stringResource(R.string.legend_slowmo_title),
                                desc = stringResource(R.string.legend_slowmo_desc),
                                modifier = Modifier.weight(1f)
                            )
                            LegendItem(
                                type = FallingObjectType.BOMB,
                                title = stringResource(R.string.legend_bomb_title),
                                desc = stringResource(R.string.legend_bomb_desc),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Shield Activation Toggle (when user owns >= 1 Shield from referrals)
                if (bonusShields > 0L) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (useShieldForNextRun) NeonMint.copy(alpha = 0.16f) else SurfaceCard,
                        border = BorderStroke(
                            1.dp,
                            if (useShieldForNextRun) NeonMint else SurfaceBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleUseShield() }
                            .testTag("shield_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = NeonMint,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = stringResource(R.string.shield_toggle_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (useShieldForNextRun) NeonMint else TextPrimary
                                )
                            }
                            Checkbox(
                                checked = useShieldForNextRun,
                                onCheckedChange = { onToggleUseShield() },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = NeonMint,
                                    uncheckedColor = TextSecondary
                                )
                            )
                        }
                    }
                }

                // PLAY Button
                Button(
                    onClick = onPlayClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = MidnightBgTop
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .scale(playPulse)
                        .testTag("play_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.play_button),
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 20.sp),
                        fontWeight = FontWeight.Bold
                    )
                }

                // REFER & EARN Button
                OutlinedButton(
                    onClick = onOpenReferralHub,
                    border = BorderStroke(1.5.dp, ElectricViolet),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("refer_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GroupAdd,
                        contentDescription = null,
                        tint = ElectricViolet,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.refer_button),
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricViolet
                    )
                }

                Text(
                    text = stringResource(R.string.controls_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun LegendItem(
    type: FallingObjectType,
    title: String,
    desc: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LegendIconBadge(type = type)
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                color = TextPrimary
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun LegendIconBadge(type: FallingObjectType) {
    val density = LocalDensity.current.density
    Canvas(modifier = Modifier.size(30.dp)) {
        val center = Offset(size.width * 0.5f, size.height * 0.5f)
        val r = size.minDimension * 0.38f
        when (type) {
            FallingObjectType.NORMAL_BALL -> {
                drawCircle(color = NeonCyan.copy(alpha = 0.3f), radius = r * 1.35f, center = center)
                drawCircle(color = NeonCyan, radius = r, center = center)
            }
            FallingObjectType.GOLDEN_BALL -> {
                drawCircle(color = SolarGold.copy(alpha = 0.35f), radius = r * 1.35f, center = center)
                drawCircle(color = SolarGold, radius = r, center = center)
                drawFourPointStar(center, r * 0.65f, Color.White)
            }
            FallingObjectType.POWER_MAGNET -> {
                drawCircle(color = MidnightBgTop, radius = r, center = center)
                drawCircle(color = ElectricViolet, radius = r, center = center, style = Stroke(2f * density))
                drawMagnetGlyph(center, r * 0.62f, ElectricViolet, density)
            }
            FallingObjectType.POWER_WIDE -> {
                drawCircle(color = MidnightBgTop, radius = r, center = center)
                drawCircle(color = NeonMint, radius = r, center = center, style = Stroke(2f * density))
                drawWideGlyph(center, r * 0.65f, NeonMint, density)
            }
            FallingObjectType.POWER_SLOW_MO -> {
                drawCircle(color = MidnightBgTop, radius = r, center = center)
                drawCircle(color = IceBlue, radius = r, center = center, style = Stroke(2f * density))
                drawSlowMoGlyph(center, r * 0.6f, IceBlue, density)
            }
            FallingObjectType.BOMB -> {
                drawCircle(color = CrimsonDanger.copy(alpha = 0.25f), radius = r * 1.35f, center = center)
                drawCircle(color = Color(0xFF1A1E30), radius = r, center = center)
                drawCircle(color = CrimsonDanger, radius = r, center = center, style = Stroke(2f * density))
                val x = r * 0.42f
                drawLine(CrimsonDanger, Offset(center.x - x, center.y - x), Offset(center.x + x, center.y + x), 2.2f * density, StrokeCap.Round)
                drawLine(CrimsonDanger, Offset(center.x + x, center.y - x), Offset(center.x - x, center.y + x), 2.2f * density, StrokeCap.Round)
            }
        }
    }
}

@Composable
fun PauseOverlay(
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onMainMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBgTop.copy(alpha = 0.72f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = SurfaceCard,
            border = BorderStroke(1.5.dp, NeonCyan.copy(alpha = 0.6f)),
            modifier = Modifier.widthIn(max = 380.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.paused_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = NeonCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = MidnightBgTop
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("resume_button")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.resume_button), style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = onRestart,
                    border = BorderStroke(1.dp, SurfaceBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("restart_pause_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = TextPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.restart_button), color = TextPrimary)
                }
                OutlinedButton(
                    onClick = onMainMenu,
                    border = BorderStroke(1.dp, SurfaceBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("menu_pause_button")
                ) {
                    Text(stringResource(R.string.main_menu_button), color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun GameOverOverlay(
    hud: GameHudState,
    onPlayAgain: () -> Unit,
    onMainMenu: () -> Unit,
    onOpenReferralHub: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBgTop.copy(alpha = 0.78f))
            .windowInsetsPadding(WindowInsets.statusBars)
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        MuteToggleButton(
            isMuted = hud.isMuted,
            onToggleMute = onToggleMute,
            modifier = Modifier.align(Alignment.TopEnd)
        )

        Surface(
            shape = MaterialTheme.shapes.large,
            color = SurfaceCard,
            border = BorderStroke(
                1.5.dp,
                if (hud.isNewHighScore) SolarGold else CrimsonDanger.copy(alpha = 0.65f)
            ),
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 420.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.game_over_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = CrimsonDanger,
                    textAlign = TextAlign.Center
                )

                if (hud.isNewHighScore) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = SolarGold.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, SolarGold),
                        modifier = Modifier.testTag("new_high_score_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.EmojiEvents,
                                contentDescription = null,
                                tint = SolarGoldBright,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.new_high_score_badge),
                                style = MaterialTheme.typography.labelMedium,
                                color = SolarGoldBright
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.score_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = hud.score.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        color = TextPrimary,
                        modifier = Modifier.testTag("final_score")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${stringResource(R.string.high_score_label)}: ${hud.highScore}",
                        style = MaterialTheme.typography.labelLarge,
                        color = SolarGold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatBox(
                        label = stringResource(R.string.stat_caught),
                        value = hud.ballsCaught.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        label = stringResource(R.string.stat_max_combo),
                        value = "${hud.maxComboMultiplier}x",
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = onPlayAgain,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = MidnightBgTop
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("play_again_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.play_again_button),
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onOpenReferralHub,
                    border = BorderStroke(1.dp, ElectricViolet),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("refer_over_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GroupAdd,
                        contentDescription = null,
                        tint = ElectricViolet,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.refer_button),
                        color = ElectricViolet
                    )
                }

                OutlinedButton(
                    onClick = onMainMenu,
                    border = BorderStroke(1.dp, SurfaceBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("main_menu_button")
                ) {
                    Text(
                        text = stringResource(R.string.main_menu_button),
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = NeonCyan
        )
    }
}
