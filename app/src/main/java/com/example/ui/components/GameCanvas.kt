package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.game.FallingObject
import com.example.game.FallingObjectType
import com.example.game.GameEngine
import com.example.game.GamePhase
import com.example.game.PowerUpType
import com.example.ui.theme.CosmicVioletBgBottom
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.DeepIndigoBgMid
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmberOrange
import com.example.ui.theme.IceBlue
import com.example.ui.theme.MidnightBgTop
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMint
import com.example.ui.theme.SolarGold
import com.example.ui.theme.SolarGoldBright
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GameCanvas(
    engine: GameEngine,
    frameTick: Long,
    onPointerMoveX: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val monoTypeface = remember(context) {
        ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.DEFAULT_BOLD
    }
    val popupPaint = remember(monoTypeface) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(monoTypeface, Typeface.BOLD)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(density) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press || event.type == PointerEventType.Move) {
                            val change = event.changes.firstOrNull()
                            if (change != null) {
                                onPointerMoveX(change.position.x / density)
                            }
                        }
                    }
                }
            }
    ) {
        // Read frameTick so Compose invalidates this Canvas every frame without recomposing outer HUD.
        val tickRead = frameTick
        if (tickRead < 0L) return@Canvas

        val shakePx = engine.shakeIntensity * density
        val shakeDx = if (shakePx > 0.2f) sin(engine.elapsedTime * 72f) * shakePx else 0f
        val shakeDy = if (shakePx > 0.2f) cos(engine.elapsedTime * 89f) * shakePx * 0.65f else 0f

        // 1. Deep neon gradient sky
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(MidnightBgTop, DeepIndigoBgMid, CosmicVioletBgBottom)
            )
        )

        // 2. Twinkling starfield
        engine.stars.forEach { star ->
            val twinkle = 0.5f + 0.5f * sin(engine.elapsedTime * star.twinkleSpeed + star.twinklePhase)
            val alpha = (star.alphaBase * (0.45f + 0.55f * twinkle)).coerceIn(0.08f, 0.9f)
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = star.radiusDp * density,
                center = Offset(star.xFraction * size.width, star.yFraction * size.height)
            )
        }

        // 3. Subtle perspective cyber-grid near floor
        drawCyberGrid(density = density)

        // 4. Slow-Mo ambient frost aura
        if (engine.isPowerUpActive(PowerUpType.SLOW_MO)) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, IceBlue.copy(alpha = 0.16f)),
                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                    radius = size.maxDimension * 0.72f
                )
            )
        }

        // Shaken playfield layer
        translate(left = shakeDx, top = shakeDy) {
            // Shockwaves
            engine.shockwaves.forEach { sw ->
                val progress = 1f - (sw.life / sw.maxLife).coerceIn(0f, 1f)
                val radiusPx = sw.maxRadius * density * progress
                val alpha = (1f - progress).coerceIn(0f, 1f) * 0.75f
                drawCircle(
                    color = sw.color.copy(alpha = alpha),
                    radius = radiusPx,
                    center = Offset(sw.x * density, sw.y * density),
                    style = Stroke(width = (5f * (1f - progress) + 1.5f) * density)
                )
            }

            // Falling objects
            engine.fallingObjects.forEach { obj ->
                drawFallingObject(obj = obj, density = density, elapsedTime = engine.elapsedTime)
            }

            // Basket / Paddle (shown during playing, paused, and subtle preview on start)
            if (engine.phase != GamePhase.START) {
                drawNeonBasket(engine = engine, density = density)
            }

            // Particles
            engine.particles.forEach { p ->
                val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
                val center = Offset(p.x * density, p.y * density)
                val radiusPx = p.radius * density * (0.5f + 0.5f * alpha)
                if (p.isStar) {
                    drawFourPointStar(
                        center = center,
                        outerRadius = radiusPx * 1.55f,
                        color = p.color.copy(alpha = alpha)
                    )
                } else {
                    drawCircle(
                        color = p.color.copy(alpha = alpha * 0.35f),
                        radius = radiusPx * 2f,
                        center = center
                    )
                    drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = radiusPx,
                        center = center
                    )
                }
            }

            // Floating score popups
            engine.popups.forEach { pop ->
                val alpha = (pop.life / pop.maxLife).coerceIn(0f, 1f)
                drawIntoCanvas { canvas ->
                    popupPaint.textSize = 18f * pop.scale * density
                    popupPaint.color = pop.color.copy(alpha = alpha).toArgb()
                    popupPaint.setShadowLayer(
                        8f * density,
                        0f,
                        0f,
                        pop.color.copy(alpha = alpha * 0.8f).toArgb()
                    )
                    canvas.nativeCanvas.drawText(
                        pop.text,
                        pop.x * density,
                        pop.y * density,
                        popupPaint
                    )
                }
            }
        }

        // 5. Damage / Bomb screen-edge flash
        if (engine.damageFlashAlpha > 0.01f) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        CrimsonDanger.copy(alpha = engine.damageFlashAlpha)
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                    radius = size.maxDimension * 0.65f
                )
            )
        }
    }
}

private fun DrawScope.drawCyberGrid(density: Float) {
    val horizonY = size.height * 0.72f
    val gridColor = NeonCyan.copy(alpha = 0.07f)
    val rows = 6
    for (i in 0..rows) {
        val t = i.toFloat() / rows
        val y = horizonY + (size.height - horizonY) * (t * t)
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f * density
        )
    }
    val vanishX = size.width * 0.5f
    val cols = 10
    for (i in -cols..cols) {
        val bottomX = vanishX + i * (size.width * 0.14f)
        drawLine(
            color = gridColor,
            start = Offset(vanishX + i * (size.width * 0.03f), horizonY),
            end = Offset(bottomX, size.height),
            strokeWidth = 1f * density
        )
    }
}

private fun DrawScope.drawNeonBasket(engine: GameEngine, density: Float) {
    val cx = engine.basketX * density
    val bounceOffset = sin(engine.basketCatchBounce * PI.toFloat()) * 4f * density
    val cy = engine.basketY * density + bounceOffset
    val widthPx = engine.currentBasketWidth * density
    val heightPx = engine.basketHeight * density
    val halfW = widthPx * 0.5f
    val halfH = heightPx * 0.5f

    val accentColor = when {
        engine.isPowerUpActive(PowerUpType.WIDE_BASKET) -> NeonMint
        engine.isPowerUpActive(PowerUpType.MAGNET) -> ElectricViolet
        engine.isPowerUpActive(PowerUpType.SLOW_MO) -> IceBlue
        else -> NeonCyan
    }

    // Magnet aura waves above basket
    if (engine.isPowerUpActive(PowerUpType.MAGNET)) {
        val pulse = (engine.elapsedTime * 2.2f) % 1f
        val arcRadius = halfW * (0.9f + pulse * 0.7f)
        drawArc(
            color = ElectricViolet.copy(alpha = (1f - pulse) * 0.45f),
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - arcRadius, cy - arcRadius * 0.9f),
            size = Size(arcRadius * 2f, arcRadius * 1.4f),
            style = Stroke(width = 2.5f * density, cap = StrokeCap.Round)
        )
    }

    // Soft underglow
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(accentColor.copy(alpha = 0.38f), Color.Transparent),
            center = Offset(cx, cy + 4f * density),
            radius = halfW * 1.25f
        ),
        topLeft = Offset(cx - halfW * 1.25f, cy - halfH * 1.4f),
        size = Size(widthPx * 1.25f, heightPx * 2.8f)
    )

    // Basket body (sleek curved energy catcher)
    val left = cx - halfW
    val right = cx + halfW
    val top = cy - halfH
    val bottom = cy + halfH

    val basketPath = Path().apply {
        moveTo(left, top)
        lineTo(right, top)
        cubicTo(
            right - widthPx * 0.05f, bottom + 4f * density,
            left + widthPx * 0.05f, bottom + 4f * density,
            left, top
        )
        close()
    }

    drawPath(
        path = basketPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.45f),
                accentColor.copy(alpha = 0.14f)
            ),
            startY = top,
            endY = bottom
        )
    )

    drawPath(
        path = basketPath,
        color = accentColor,
        style = Stroke(width = 2.8f * density, join = StrokeJoin.Round, cap = StrokeCap.Round)
    )

    // Top glowing rim bar
    drawRoundRect(
        color = Color.White.copy(alpha = 0.92f),
        topLeft = Offset(left + 2f * density, top - 2f * density),
        size = Size((widthPx - 4f * density).coerceAtLeast(4f), 4f * density),
        cornerRadius = CornerRadius(4f * density, 4f * density)
    )
}

private fun DrawScope.drawFallingObject(
    obj: FallingObject,
    density: Float,
    elapsedTime: Float
) {
    val cx = obj.x * density
    val cy = obj.y * density
    val r = obj.radius * density
    val center = Offset(cx, cy)

    when (obj.type) {
        FallingObjectType.NORMAL_BALL -> {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(obj.color.copy(alpha = 0.5f), Color.Transparent),
                    center = center,
                    radius = r * 1.8f
                ),
                radius = r * 1.8f,
                center = center
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, obj.color, obj.color.copy(alpha = 0.85f)),
                    center = Offset(cx - r * 0.28f, cy - r * 0.28f),
                    radius = r * 1.35f
                ),
                radius = r,
                center = center
            )
        }

        FallingObjectType.GOLDEN_BALL -> {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SolarGoldBright.copy(alpha = 0.65f), Color.Transparent),
                    center = center,
                    radius = r * 2.15f
                ),
                radius = r * 2.15f,
                center = center
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, SolarGoldBright, SolarGold),
                    center = Offset(cx - r * 0.25f, cy - r * 0.25f),
                    radius = r * 1.3f
                ),
                radius = r,
                center = center
            )
            rotate(degrees = obj.rotation * 57.3f, pivot = center) {
                drawFourPointStar(
                    center = center,
                    outerRadius = r * 0.68f,
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
        }

        FallingObjectType.POWER_MAGNET,
        FallingObjectType.POWER_WIDE,
        FallingObjectType.POWER_SLOW_MO -> {
            val pulse = 1f + 0.08f * sin(elapsedTime * 7f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(obj.color.copy(alpha = 0.45f), Color.Transparent),
                    center = center,
                    radius = r * 1.9f * pulse
                ),
                radius = r * 1.9f * pulse,
                center = center
            )
            drawCircle(
                color = MidnightBgTop.copy(alpha = 0.92f),
                radius = r,
                center = center
            )
            drawCircle(
                color = obj.color,
                radius = r,
                center = center,
                style = Stroke(width = 2.6f * density)
            )
            when (obj.type) {
                FallingObjectType.POWER_MAGNET -> drawMagnetGlyph(center, r * 0.56f, obj.color, density)
                FallingObjectType.POWER_WIDE -> drawWideGlyph(center, r * 0.62f, obj.color, density)
                FallingObjectType.POWER_SLOW_MO -> drawSlowMoGlyph(center, r * 0.56f, obj.color, density)
                else -> Unit
            }
        }

        FallingObjectType.BOMB -> {
            val dangerPulse = 0.7f + 0.3f * sin(elapsedTime * 11f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CrimsonDanger.copy(alpha = 0.52f * dangerPulse),
                        Color.Transparent
                    ),
                    center = center,
                    radius = r * 1.9f
                ),
                radius = r * 1.9f,
                center = center
            )
            // Bomb body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF393E58), Color(0xFF141724)),
                    center = Offset(cx - r * 0.25f, cy - r * 0.25f),
                    radius = r * 1.3f
                ),
                radius = r * 0.92f,
                center = center
            )
            drawCircle(
                color = CrimsonDanger,
                radius = r * 0.92f,
                center = center,
                style = Stroke(width = 2f * density)
            )
            // Warning X emblem
            val xR = r * 0.38f
            drawLine(
                color = CrimsonDanger,
                start = Offset(cx - xR, cy - xR),
                end = Offset(cx + xR, cy + xR),
                strokeWidth = 2.8f * density,
                cap = StrokeCap.Round
            )
            drawLine(
                color = CrimsonDanger,
                start = Offset(cx + xR, cy - xR),
                end = Offset(cx - xR, cy + xR),
                strokeWidth = 2.8f * density,
                cap = StrokeCap.Round
            )
            // Fuse + spark
            val fuseStart = Offset(cx + r * 0.35f, cy - r * 0.85f)
            val fuseTip = Offset(cx + r * 0.78f, cy - r * 1.25f)
            drawLine(
                color = Color(0xFFD1D5DB),
                start = fuseStart,
                end = fuseTip,
                strokeWidth = 2.4f * density,
                cap = StrokeCap.Round
            )
            val sparkRadius = (2.8f + 1.5f * sin(elapsedTime * 25f)) * density
            drawCircle(color = EmberOrange, radius = sparkRadius * 1.6f, center = fuseTip)
            drawCircle(color = SolarGoldBright, radius = sparkRadius, center = fuseTip)
        }
    }
}

fun DrawScope.drawFourPointStar(center: Offset, outerRadius: Float, color: Color) {
    val inner = outerRadius * 0.32f
    val path = Path().apply {
        moveTo(center.x, center.y - outerRadius)
        lineTo(center.x + inner, center.y - inner)
        lineTo(center.x + outerRadius, center.y)
        lineTo(center.x + inner, center.y + inner)
        lineTo(center.x, center.y + outerRadius)
        lineTo(center.x - inner, center.y + inner)
        lineTo(center.x - outerRadius, center.y)
        lineTo(center.x - inner, center.y - inner)
        close()
    }
    drawPath(path = path, color = color)
}

fun DrawScope.drawMagnetGlyph(center: Offset, r: Float, color: Color, density: Float) {
    val stroke = 2.4f * density
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(center.x - r * 0.65f, center.y - r * 0.25f),
        size = Size(r * 1.3f, r * 1.15f),
        style = Stroke(width = stroke, cap = StrokeCap.Round)
    )
    drawLine(
        color = Color.White,
        start = Offset(center.x - r * 0.65f, center.y - r * 0.65f),
        end = Offset(center.x - r * 0.65f, center.y + r * 0.32f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color.White,
        start = Offset(center.x + r * 0.65f, center.y - r * 0.65f),
        end = Offset(center.x + r * 0.65f, center.y + r * 0.32f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
}

fun DrawScope.drawWideGlyph(center: Offset, r: Float, color: Color, density: Float) {
    val stroke = 2.3f * density
    drawLine(
        color = color,
        start = Offset(center.x - r * 0.85f, center.y),
        end = Offset(center.x + r * 0.85f, center.y),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
    // Left arrow
    drawLine(
        color = Color.White,
        start = Offset(center.x - r * 0.85f, center.y),
        end = Offset(center.x - r * 0.42f, center.y - r * 0.42f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color.White,
        start = Offset(center.x - r * 0.85f, center.y),
        end = Offset(center.x - r * 0.42f, center.y + r * 0.42f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
    // Right arrow
    drawLine(
        color = Color.White,
        start = Offset(center.x + r * 0.85f, center.y),
        end = Offset(center.x + r * 0.42f, center.y - r * 0.42f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color.White,
        start = Offset(center.x + r * 0.85f, center.y),
        end = Offset(center.x + r * 0.42f, center.y + r * 0.42f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
}

fun DrawScope.drawSlowMoGlyph(center: Offset, r: Float, color: Color, density: Float) {
    val stroke = 2.1f * density
    drawCircle(
        color = color,
        radius = r * 0.85f,
        center = center,
        style = Stroke(width = stroke)
    )
    drawLine(
        color = Color.White,
        start = center,
        end = Offset(center.x, center.y - r * 0.52f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color.White,
        start = center,
        end = Offset(center.x + r * 0.42f, center.y + r * 0.16f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
}
