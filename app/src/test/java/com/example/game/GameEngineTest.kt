package com.example.game

import com.example.audio.GameSoundPlayer
import com.example.data.ScoreStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private class FakeScoreStorage(
    private var highScore: Int = 0,
    private var muted: Boolean = false
) : ScoreStorage {
    override fun getHighScore(): Int = highScore
    override fun saveHighScore(score: Int) {
        highScore = score
    }
    override fun isMuted(): Boolean = muted
    override fun saveMuted(muted: Boolean) {
        this.muted = muted
    }
}

private class FakeSoundPlayer : GameSoundPlayer {
    var catches = 0
    var bonuses = 0
    var powerUps = 0
    var explosions = 0
    var misses = 0
    var gameOvers = 0
    var mutedState = false

    override fun playCatchPop(comboMultiplier: Int) { catches++ }
    override fun playBonusChime() { bonuses++ }
    override fun playPowerUp() { powerUps++ }
    override fun playBombExplosion() { explosions++ }
    override fun playMiss() { misses++ }
    override fun playGameOver() { gameOvers++ }
    override fun setMuted(muted: Boolean) { mutedState = muted }
    override fun release() {}
}

class GameEngineTest {

    @Test
    fun `catching normal and golden balls increments score and combo multiplier`() {
        val storage = FakeScoreStorage()
        val sound = FakeSoundPlayer()
        val engine = GameEngine(storage, sound, Random(1))
        engine.updateViewport(400f, 800f, 16f)
        engine.startNewGame()

        // Catch 3 normal balls -> 3rd catch triggers 2x multiplier (10 + 10 + 20 = 40)
        repeat(3) {
            engine.forceSpawnForTest(FallingObjectType.NORMAL_BALL, engine.basketX, engine.basketY)
            engine.tick(0.016f)
        }
        assertEquals(40, engine.score)
        assertEquals(2, engine.comboMultiplier)
        assertEquals(3, sound.catches)
        assertTrue(engine.isNewHighScore)
        assertEquals(40, storage.getHighScore())

        // Catch a golden ball at 2x -> +100
        engine.forceSpawnForTest(FallingObjectType.GOLDEN_BALL, engine.basketX, engine.basketY)
        engine.tick(0.016f)
        assertEquals(140, engine.score)
        assertEquals(1, sound.bonuses)
    }

    @Test
    fun `missing normal ball costs 1 life and resets combo`() {
        val storage = FakeScoreStorage()
        val sound = FakeSoundPlayer()
        val engine = GameEngine(storage, sound, Random(2))
        engine.updateViewport(400f, 800f, 16f)
        engine.startNewGame()

        repeat(3) {
            engine.forceSpawnForTest(FallingObjectType.NORMAL_BALL, engine.basketX, engine.basketY)
            engine.tick(0.016f)
        }
        assertEquals(2, engine.comboMultiplier)

        // Spawn ball below bottom edge
        engine.forceSpawnForTest(FallingObjectType.NORMAL_BALL, 50f, 830f, speed = 200f)
        engine.tick(0.016f)

        assertEquals(2, engine.lives)
        assertEquals(1, engine.comboMultiplier)
        assertEquals(0, engine.comboStreak)
        assertEquals(1, sound.misses)
    }

    @Test
    fun `bomb hit deducts life triggers shake and ends game at 0 lives`() {
        val storage = FakeScoreStorage()
        val sound = FakeSoundPlayer()
        val engine = GameEngine(storage, sound, Random(3))
        engine.updateViewport(400f, 800f, 16f)
        engine.startNewGame()

        repeat(3) {
            engine.forceSpawnForTest(FallingObjectType.BOMB, engine.basketX, engine.basketY)
            engine.tick(0.016f)
        }

        assertEquals(0, engine.lives)
        assertEquals(GamePhase.GAME_OVER, engine.phase)
        assertEquals(3, sound.explosions)
        assertEquals(1, sound.gameOvers)
        assertTrue(engine.shakeIntensity > 0f)
    }

    @Test
    fun `power ups activate for expected durations and wide doubles basket`() {
        val storage = FakeScoreStorage()
        val sound = FakeSoundPlayer()
        val engine = GameEngine(storage, sound, Random(4))
        engine.updateViewport(400f, 800f, 16f)
        engine.startNewGame()

        val baseWidth = engine.baseBasketWidth()

        engine.forceSpawnForTest(FallingObjectType.POWER_MAGNET, engine.basketX, engine.basketY)
        engine.forceSpawnForTest(FallingObjectType.POWER_WIDE, engine.basketX, engine.basketY)
        engine.forceSpawnForTest(FallingObjectType.POWER_SLOW_MO, engine.basketX, engine.basketY)
        engine.tick(0.016f)

        assertTrue(engine.isPowerUpActive(PowerUpType.MAGNET))
        assertTrue(engine.isPowerUpActive(PowerUpType.WIDE_BASKET))
        assertTrue(engine.isPowerUpActive(PowerUpType.SLOW_MO))
        assertEquals(3, sound.powerUps)

        // Advance time smoothly to let wide basket expand
        repeat(30) { engine.tick(0.02f) }
        assertTrue(engine.currentBasketWidth > baseWidth * 1.8f)

        // Advance past slow-mo 5s duration
        repeat(230) { engine.tick(0.02f) }
        assertFalse(engine.isPowerUpActive(PowerUpType.SLOW_MO))
    }
}
