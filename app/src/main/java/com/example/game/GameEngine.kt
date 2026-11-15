package com.example.game

import androidx.compose.ui.graphics.Color
import com.example.audio.GameSoundPlayer
import com.example.data.ScoreStorage
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmberOrange
import com.example.ui.theme.IceBlue
import com.example.ui.theme.NeonMint
import com.example.ui.theme.SolarGold
import com.example.ui.theme.SolarGoldBright
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class GameEngine(
    private val scoreStorage: ScoreStorage,
    private val soundPlayer: GameSoundPlayer,
    private val random: Random = Random.Default
) {
    var phase: GamePhase = GamePhase.START
        private set

    var worldWidthDp: Float = 400f
        private set
    var worldHeightDp: Float = 800f
        private set
    var bottomInsetDp: Float = 16f
        private set

    var basketX: Float = 200f
        private set
    val basketY: Float
        get() = (worldHeightDp - bottomInsetDp - 46f).coerceAtLeast(160f)

    var currentBasketWidth: Float = 96f
        private set
    val basketHeight: Float = 22f

    var score: Int = 0
        private set
    var highScore: Int = scoreStorage.getHighScore()
        private set
    private var initialHighScoreAtRunStart: Int = highScore
    var isNewHighScore: Boolean = false
        private set

    var lives: Int = GameHudState.MAX_LIVES
        private set
    var maxLivesForRun: Int = GameHudState.MAX_LIVES
        private set
    var comboStreak: Int = 0
        private set
    var comboMultiplier: Int = 1
        private set
    var maxComboMultiplier: Int = 1
        private set
    var ballsCaught: Int = 0
        private set
    var isMuted: Boolean = scoreStorage.isMuted()
        private set

    var shakeIntensity: Float = 0f
        private set
    var damageFlashAlpha: Float = 0f
        private set
    var basketCatchBounce: Float = 0f
        private set
    var elapsedTime: Float = 0f
        private set

    val fallingObjects: MutableList<FallingObject> = mutableListOf()
    val particles: MutableList<Particle> = mutableListOf()
    val shockwaves: MutableList<Shockwave> = mutableListOf()
    val popups: MutableList<FloatingPopup> = mutableListOf()
    val stars: List<StarDot> = List(48) {
        StarDot(
            xFraction = random.nextFloat(),
            yFraction = random.nextFloat() * 0.88f,
            radiusDp = 0.8f + random.nextFloat() * 1.6f,
            twinklePhase = random.nextFloat() * (2f * PI.toFloat()),
            twinkleSpeed = 1.2f + random.nextFloat() * 2.4f,
            alphaBase = 0.2f + random.nextFloat() * 0.5f
        )
    }

    private val activePowerUpTimers = mutableMapOf<PowerUpType, Float>()
    private var spawnAccumulator: Float = 0f
    private var nextObjectId: Long = 1L
    private var lastNormalSpawnX: Float = -1000f

    private var leftKeyHeld: Boolean = false
    private var rightKeyHeld: Boolean = false

    init {
        soundPlayer.setMuted(isMuted)
        currentBasketWidth = baseBasketWidth()
    }

    fun updateViewport(widthDp: Float, heightDp: Float, bottomSafeDp: Float) {
        if (widthDp <= 0f || heightDp <= 0f) return
        val wasUninitialized = worldWidthDp == 400f && basketX == 200f && fallingObjects.isEmpty()
        worldWidthDp = widthDp
        worldHeightDp = heightDp
        bottomInsetDp = bottomSafeDp
        if (wasUninitialized) {
            basketX = widthDp * 0.5f
            currentBasketWidth = baseBasketWidth()
        } else {
            clampBasketPosition()
        }
    }

    fun syncCloudHighScore(cloudBest: Int) {
        if (cloudBest > highScore) {
            highScore = cloudBest
            scoreStorage.saveHighScore(highScore)
            if (phase != GamePhase.PLAYING) {
                initialHighScoreAtRunStart = highScore
            }
        }
    }

    fun startNewGame(extraStartHearts: Int = 0) {
        phase = GamePhase.PLAYING
        score = 0
        initialHighScoreAtRunStart = highScore
        isNewHighScore = false
        val startLives = GameHudState.MAX_LIVES + extraStartHearts.coerceIn(0, 2)
        lives = startLives
        maxLivesForRun = startLives
        comboStreak = 0
        comboMultiplier = 1
        maxComboMultiplier = 1
        ballsCaught = 0
        shakeIntensity = 0f
        damageFlashAlpha = 0f
        basketCatchBounce = 0f
        spawnAccumulator = 0.25f
        activePowerUpTimers.clear()
        fallingObjects.clear()
        particles.clear()
        shockwaves.clear()
        popups.clear()
        basketX = worldWidthDp * 0.5f
        currentBasketWidth = baseBasketWidth()
    }

    fun pauseGame() {
        if (phase == GamePhase.PLAYING) {
            phase = GamePhase.PAUSED
            leftKeyHeld = false
            rightKeyHeld = false
        }
    }

    fun resumeGame() {
        if (phase == GamePhase.PAUSED) {
            phase = GamePhase.PLAYING
        }
    }

    fun returnToStart() {
        phase = GamePhase.START
        fallingObjects.clear()
        particles.clear()
        shockwaves.clear()
        popups.clear()
        activePowerUpTimers.clear()
        shakeIntensity = 0f
        damageFlashAlpha = 0f
        leftKeyHeld = false
        rightKeyHeld = false
    }

    fun toggleMute(): Boolean {
        isMuted = !isMuted
        scoreStorage.saveMuted(isMuted)
        soundPlayer.setMuted(isMuted)
        return isMuted
    }

    fun setArrowKeys(left: Boolean, right: Boolean) {
        leftKeyHeld = left
        rightKeyHeld = right
    }

    fun moveBasketTo(targetXDp: Float) {
        if (phase != GamePhase.PLAYING) return
        basketX = targetXDp
        clampBasketPosition()
    }

    fun moveBasketBy(deltaXDp: Float) {
        if (phase != GamePhase.PLAYING) return
        basketX += deltaXDp
        clampBasketPosition()
    }

    fun isPowerUpActive(type: PowerUpType): Boolean =
        (activePowerUpTimers[type] ?: 0f) > 0f

    fun remainingPowerUpSeconds(type: PowerUpType): Float =
        activePowerUpTimers[type] ?: 0f

    fun snapshotHud(): GameHudState {
        val powerUps = PowerUpType.entries.mapNotNull { type ->
            val remaining = activePowerUpTimers[type] ?: 0f
            if (remaining > 0f) {
                ActivePowerUpUi(
                    type = type,
                    remainingSeconds = remaining,
                    totalSeconds = type.durationSeconds
                )
            } else {
                null
            }
        }
        return GameHudState(
            phase = phase,
            score = score,
            highScore = highScore,
            lives = lives,
            maxLives = maxLivesForRun,
            comboStreak = comboStreak,
            comboMultiplier = comboMultiplier,
            maxComboMultiplier = maxComboMultiplier,
            ballsCaught = ballsCaught,
            isNewHighScore = isNewHighScore,
            isMuted = isMuted,
            activePowerUps = powerUps
        )
    }

    fun tick(rawDeltaSeconds: Float) {
        val dt = rawDeltaSeconds.coerceIn(0f, 0.05f)
        elapsedTime += dt

        if (phase != GamePhase.PLAYING) {
            updateVisualEffectsOnly(dt)
            return
        }

        // 1. Keyboard movement
        val moveDir = (if (rightKeyHeld) 1f else 0f) - (if (leftKeyHeld) 1f else 0f)
        if (moveDir != 0f) {
            val keyboardSpeed = (worldWidthDp * 1.35f).coerceIn(520f, 900f)
            basketX += moveDir * keyboardSpeed * dt
            clampBasketPosition()
        }

        // 2. Power-up countdowns & basket width interpolation
        val expiredTypes = mutableListOf<PowerUpType>()
        activePowerUpTimers.forEach { (type, rem) ->
            val updated = rem - dt
            if (updated <= 0f) expiredTypes.add(type)
            else activePowerUpTimers[type] = updated
        }
        expiredTypes.forEach { activePowerUpTimers.remove(it) }

        val targetWidth = if (isPowerUpActive(PowerUpType.WIDE_BASKET)) {
            (baseBasketWidth() * 2f).coerceAtMost(worldWidthDp * 0.72f)
        } else {
            baseBasketWidth()
        }
        currentBasketWidth += (targetWidth - currentBasketWidth) * (1f - exp(-12f * dt))
        clampBasketPosition()

        // 3. Dynamic difficulty spawning
        val spawnInterval = currentSpawnInterval()
        spawnAccumulator += dt
        while (spawnAccumulator >= spawnInterval) {
            spawnAccumulator -= spawnInterval
            spawnFallingObject()
        }

        // 4. Update falling objects & collisions
        val slowMoFactor = if (isPowerUpActive(PowerUpType.SLOW_MO)) 0.48f else 1f
        val magnetActive = isPowerUpActive(PowerUpType.MAGNET)
        val bY = basketY
        val halfW = currentBasketWidth * 0.5f
        val halfH = basketHeight * 0.5f

        val iterator = fallingObjects.iterator()
        while (iterator.hasNext()) {
            val obj = iterator.next()
            obj.rotation += obj.rotationSpeed * dt

            // Magnet attraction for Normal & Golden balls only
            if (magnetActive && (obj.type == FallingObjectType.NORMAL_BALL || obj.type == FallingObjectType.GOLDEN_BALL)) {
                val dx = basketX - obj.x
                val dy = bY - obj.y
                val dist = hypot(dx, dy)
                val magnetRange = (worldWidthDp * 0.65f).coerceIn(220f, 380f)
                if (dist < magnetRange && obj.y < bY + 12f) {
                    val strength = (1f - (dist / magnetRange)).coerceIn(0.15f, 1f)
                    val pullSpeed = 380f * strength
                    if (abs(dx) > 1f) {
                        obj.x += (dx / dist) * pullSpeed * dt
                    }
                }
            }

            obj.y += obj.baseSpeed * slowMoFactor * dt

            // Golden ball trailing sparkles
            if (obj.type == FallingObjectType.GOLDEN_BALL) {
                obj.sparkleTimer += dt
                if (obj.sparkleTimer >= 0.045f) {
                    obj.sparkleTimer = 0f
                    particles.add(
                        Particle(
                            x = obj.x + (random.nextFloat() - 0.5f) * obj.radius,
                            y = obj.y - obj.radius * 0.5f,
                            vx = (random.nextFloat() - 0.5f) * 30f,
                            vy = -25f - random.nextFloat() * 35f,
                            radius = 2.5f + random.nextFloat() * 2.5f,
                            color = SolarGoldBright,
                            life = 0.35f,
                            maxLife = 0.35f,
                            isStar = true
                        )
                    )
                }
            }

            // Check catch collision with basket
            val catchesHorizontally = abs(obj.x - basketX) <= (halfW + obj.radius * 0.65f)
            val catchesVertically = (obj.y + obj.radius * 0.75f) >= (bY - halfH) &&
                (obj.y - obj.radius * 0.6f) <= (bY + halfH + 8f)

            if (catchesHorizontally && catchesVertically) {
                handleCaughtObject(obj)
                iterator.remove()
                continue
            }

            // Check missed / fell off bottom
            if (obj.y - obj.radius > worldHeightDp + 10f) {
                handleMissedObject(obj)
                iterator.remove()
            }
        }

        updateVisualEffectsOnly(dt)
    }

    private fun updateVisualEffectsOnly(dt: Float) {
        shakeIntensity *= exp(-10f * dt)
        if (shakeIntensity < 0.2f) shakeIntensity = 0f

        damageFlashAlpha = (damageFlashAlpha - dt * 1.4f).coerceAtLeast(0f)
        basketCatchBounce = (basketCatchBounce - dt * 5.5f).coerceAtLeast(0f)

        val pIter = particles.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vy += 190f * dt
            p.life -= dt
            if (p.life <= 0f) pIter.remove()
        }

        val sIter = shockwaves.iterator()
        while (sIter.hasNext()) {
            val s = sIter.next()
            s.life -= dt
            if (s.life <= 0f) sIter.remove()
        }

        val popIter = popups.iterator()
        while (popIter.hasNext()) {
            val pop = popIter.next()
            pop.y += pop.vy * dt
            pop.life -= dt
            if (pop.life <= 0f) popIter.remove()
        }
    }

    private fun handleCaughtObject(obj: FallingObject) {
        basketCatchBounce = 1f
        when (obj.type) {
            FallingObjectType.NORMAL_BALL -> {
                val previousMultiplier = comboMultiplier
                comboStreak += 1
                comboMultiplier = calculateMultiplier(comboStreak)
                if (comboMultiplier > maxComboMultiplier) maxComboMultiplier = comboMultiplier
                ballsCaught += 1
                val pts = 10 * comboMultiplier
                addScore(pts)
                soundPlayer.playCatchPop(comboMultiplier)
                spawnCatchParticles(obj.x, basketY - 6f, obj.color, count = 14, includeStars = false)
                spawnPopup(obj.x, basketY - 26f, "+$pts", obj.color, scale = 1f)
                if (comboMultiplier > previousMultiplier) {
                    spawnPopup(
                        obj.x,
                        basketY - 54f,
                        if (comboMultiplier == 5) "5x MAX!" else "${comboMultiplier}x!",
                        SolarGold,
                        scale = 1.25f
                    )
                }
            }

            FallingObjectType.GOLDEN_BALL -> {
                val previousMultiplier = comboMultiplier
                comboStreak += 1
                comboMultiplier = calculateMultiplier(comboStreak)
                if (comboMultiplier > maxComboMultiplier) maxComboMultiplier = comboMultiplier
                ballsCaught += 1
                val pts = 50 * comboMultiplier
                addScore(pts)
                soundPlayer.playBonusChime()
                spawnCatchParticles(obj.x, basketY - 6f, SolarGold, count = 24, includeStars = true)
                spawnPopup(obj.x, basketY - 26f, "+$pts", SolarGoldBright, scale = 1.2f)
                if (comboMultiplier > previousMultiplier) {
                    spawnPopup(
                        obj.x,
                        basketY - 56f,
                        if (comboMultiplier == 5) "5x MAX!" else "${comboMultiplier}x!",
                        SolarGold,
                        scale = 1.3f
                    )
                }
            }

            FallingObjectType.POWER_MAGNET -> {
                activePowerUpTimers[PowerUpType.MAGNET] = PowerUpType.MAGNET.durationSeconds
                soundPlayer.playPowerUp()
                spawnCatchParticles(obj.x, basketY - 6f, ElectricViolet, count = 18, includeStars = true)
                spawnPopup(obj.x, basketY - 30f, "MAGNET!", ElectricViolet, scale = 1.15f)
            }

            FallingObjectType.POWER_WIDE -> {
                activePowerUpTimers[PowerUpType.WIDE_BASKET] = PowerUpType.WIDE_BASKET.durationSeconds
                soundPlayer.playPowerUp()
                spawnCatchParticles(obj.x, basketY - 6f, NeonMint, count = 18, includeStars = true)
                spawnPopup(obj.x, basketY - 30f, "2x WIDE!", NeonMint, scale = 1.15f)
            }

            FallingObjectType.POWER_SLOW_MO -> {
                activePowerUpTimers[PowerUpType.SLOW_MO] = PowerUpType.SLOW_MO.durationSeconds
                soundPlayer.playPowerUp()
                spawnCatchParticles(obj.x, basketY - 6f, IceBlue, count = 18, includeStars = true)
                spawnPopup(obj.x, basketY - 30f, "SLOW-MO!", IceBlue, scale = 1.15f)
            }

            FallingObjectType.BOMB -> {
                comboStreak = 0
                comboMultiplier = 1
                lives = (lives - 1).coerceAtLeast(0)
                shakeIntensity = 16f
                damageFlashAlpha = 0.48f
                soundPlayer.playBombExplosion()
                shockwaves.add(
                    Shockwave(
                        x = obj.x,
                        y = basketY,
                        maxRadius = 140f,
                        color = CrimsonDanger,
                        life = 0.42f,
                        maxLife = 0.42f
                    )
                )
                spawnExplosionParticles(obj.x, basketY)
                spawnPopup(obj.x, basketY - 32f, "BOOM! -1♥", CrimsonDanger, scale = 1.25f)
                if (lives <= 0) {
                    triggerGameOver()
                }
            }
        }
    }

    private fun handleMissedObject(obj: FallingObject) {
        if (obj.type == FallingObjectType.NORMAL_BALL || obj.type == FallingObjectType.GOLDEN_BALL) {
            comboStreak = 0
            comboMultiplier = 1
            lives = (lives - 1).coerceAtLeast(0)
            damageFlashAlpha = 0.22f
            soundPlayer.playMiss()
            spawnPopup(
                obj.x.coerceIn(36f, worldWidthDp - 36f),
                worldHeightDp - bottomInsetDp - 24f,
                "MISS!",
                CrimsonDanger,
                scale = 0.95f
            )
            if (lives <= 0) {
                triggerGameOver()
            }
        }
    }

    private fun triggerGameOver() {
        phase = GamePhase.GAME_OVER
        leftKeyHeld = false
        rightKeyHeld = false
        soundPlayer.playGameOver()
    }

    private fun addScore(points: Int) {
        score += points
        if (score > highScore) {
            highScore = score
            scoreStorage.saveHighScore(highScore)
            if (score > initialHighScoreAtRunStart) {
                isNewHighScore = true
            }
        }
    }

    private fun spawnFallingObject() {
        val roll = random.nextFloat()
        val bombChance = (0.10f + (score / 1500f) * 0.12f).coerceIn(0.10f, 0.22f)
        val powerUpChance = 0.11f
        val goldenChance = 0.13f

        val type = when {
            roll < bombChance -> FallingObjectType.BOMB
            roll < bombChance + powerUpChance -> {
                when (random.nextInt(3)) {
                    0 -> FallingObjectType.POWER_MAGNET
                    1 -> FallingObjectType.POWER_WIDE
                    else -> FallingObjectType.POWER_SLOW_MO
                }
            }
            roll < bombChance + powerUpChance + goldenChance -> FallingObjectType.GOLDEN_BALL
            else -> FallingObjectType.NORMAL_BALL
        }

        val radius = when (type) {
            FallingObjectType.NORMAL_BALL -> 16f
            FallingObjectType.GOLDEN_BALL -> 14f
            FallingObjectType.POWER_MAGNET,
            FallingObjectType.POWER_WIDE,
            FallingObjectType.POWER_SLOW_MO -> 17f
            FallingObjectType.BOMB -> 18f
        }

        val margin = radius + 16f
        val maxX = (worldWidthDp - margin).coerceAtLeast(margin + 1f)
        var spawnX = margin + random.nextFloat() * (maxX - margin)
        if (type == FallingObjectType.BOMB && abs(spawnX - lastNormalSpawnX) < 52f) {
            spawnX = if (spawnX < worldWidthDp * 0.5f) {
                (spawnX + 68f).coerceAtMost(maxX)
            } else {
                (spawnX - 68f).coerceAtLeast(margin)
            }
        }
        if (type == FallingObjectType.NORMAL_BALL || type == FallingObjectType.GOLDEN_BALL) {
            lastNormalSpawnX = spawnX
        }

        val speedScale = baseFallSpeed()
        val speed = when (type) {
            FallingObjectType.NORMAL_BALL -> speedScale * (0.94f + random.nextFloat() * 0.14f)
            FallingObjectType.GOLDEN_BALL -> speedScale * 1.45f
            FallingObjectType.POWER_MAGNET,
            FallingObjectType.POWER_WIDE,
            FallingObjectType.POWER_SLOW_MO -> speedScale * 0.92f
            FallingObjectType.BOMB -> speedScale * 1.08f
        }

        val color = when (type) {
            FallingObjectType.NORMAL_BALL -> NORMAL_BALL_PALETTE[random.nextInt(NORMAL_BALL_PALETTE.size)]
            else -> objectColorForType(type)
        }

        val rotSpeed = when (type) {
            FallingObjectType.GOLDEN_BALL -> 3.2f
            FallingObjectType.BOMB -> if (random.nextBoolean()) 1.4f else -1.4f
            else -> 0f
        }

        fallingObjects.add(
            FallingObject(
                id = nextObjectId++,
                type = type,
                x = spawnX,
                y = -radius - 8f,
                radius = radius,
                baseSpeed = speed,
                color = color,
                rotationSpeed = rotSpeed
            )
        )
    }

    private fun spawnCatchParticles(
        x: Float,
        y: Float,
        color: Color,
        count: Int,
        includeStars: Boolean
    ) {
        repeat(count) { i ->
            val angle = -PI.toFloat() * random.nextFloat()
            val speed = 80f + random.nextFloat() * 190f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed - 40f,
                    radius = 2.5f + random.nextFloat() * 3.5f,
                    color = if (includeStars && i % 2 == 0) SolarGoldBright else color,
                    life = 0.45f + random.nextFloat() * 0.25f,
                    maxLife = 0.7f,
                    isStar = includeStars && i % 2 == 0
                )
            )
        }
    }

    private fun spawnExplosionParticles(x: Float, y: Float) {
        repeat(28) { i ->
            val angle = (2f * PI.toFloat() * i) / 28f + random.nextFloat() * 0.2f
            val speed = 110f + random.nextFloat() * 240f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed - 60f,
                    radius = 3f + random.nextFloat() * 4.5f,
                    color = if (i % 3 == 0) SolarGold else if (i % 2 == 0) EmberOrange else CrimsonDanger,
                    life = 0.55f + random.nextFloat() * 0.25f,
                    maxLife = 0.8f,
                    isStar = false
                )
            )
        }
    }

    private fun spawnPopup(x: Float, y: Float, text: String, color: Color, scale: Float) {
        popups.add(
            FloatingPopup(
                x = x.coerceIn(48f, (worldWidthDp - 48f).coerceAtLeast(48f)),
                y = y,
                vy = -68f,
                text = text,
                color = color,
                scale = scale,
                life = 0.75f,
                maxLife = 0.75f
            )
        )
    }

    fun baseBasketWidth(): Float = (worldWidthDp * 0.23f).coerceIn(84f, 138f)

    fun currentSpawnInterval(): Float =
        (1.02f - (score / 1100f) * 0.62f).coerceIn(0.36f, 1.02f)

    fun baseFallSpeed(): Float =
        210f + (score * 0.16f).coerceAtMost(270f)

    private fun clampBasketPosition() {
        val halfW = currentBasketWidth * 0.5f
        val minX = halfW + 8f
        val maxX = (worldWidthDp - halfW - 8f).coerceAtLeast(minX)
        basketX = basketX.coerceIn(minX, maxX)
    }

    fun forceSpawnForTest(
        type: FallingObjectType,
        x: Float,
        y: Float,
        speed: Float = 200f
    ) {
        fallingObjects.add(
            FallingObject(
                id = nextObjectId++,
                type = type,
                x = x,
                y = y,
                radius = 16f,
                baseSpeed = speed,
                color = objectColorForType(type)
            )
        )
    }

    companion object {
        fun calculateMultiplier(streak: Int): Int = when {
            streak >= 10 -> 5
            streak >= 6 -> 3
            streak >= 3 -> 2
            else -> 1
        }
    }
}
