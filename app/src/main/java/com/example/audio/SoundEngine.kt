package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

interface GameSoundPlayer {
    fun playCatchPop(comboMultiplier: Int)
    fun playBonusChime()
    fun playPowerUp()
    fun playBombExplosion()
    fun playMiss()
    fun playGameOver()
    fun setMuted(muted: Boolean)
    fun release()
}

class SynthesizedSoundEngine(initialMuted: Boolean = false) : GameSoundPlayer {
    @Volatile
    private var isMuted: Boolean = initialMuted

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val bonusChimeBuffer: ShortArray = synthesizeArpeggio(
        frequencies = listOf(880.0, 1174.66, 1567.98),
        noteDurationMs = 70,
        volume = 0.35
    )

    private val powerUpBuffer: ShortArray = synthesizeSweep(
        startFreq = 380.0,
        endFreq = 1140.0,
        durationMs = 240,
        volume = 0.35
    )

    private val bombExplosionBuffer: ShortArray = synthesizeExplosion(
        durationMs = 340,
        volume = 0.55
    )

    private val missBuffer: ShortArray = synthesizeSweep(
        startFreq = 260.0,
        endFreq = 130.0,
        durationMs = 150,
        volume = 0.25
    )

    private val gameOverBuffer: ShortArray = synthesizeArpeggio(
        frequencies = listOf(440.0, 349.23, 293.66, 220.0),
        noteDurationMs = 135,
        volume = 0.35
    )

    override fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    override fun playCatchPop(comboMultiplier: Int) {
        if (isMuted) return
        val pitchBoost = when {
            comboMultiplier >= 5 -> 1.45
            comboMultiplier >= 3 -> 1.28
            comboMultiplier >= 2 -> 1.14
            else -> 1.0
        }
        scope.launch {
            val samples = synthesizeSweep(
                startFreq = 460.0 * pitchBoost,
                endFreq = 780.0 * pitchBoost,
                durationMs = 85,
                volume = 0.32
            )
            playSamples(samples)
        }
    }

    override fun playBonusChime() {
        if (isMuted) return
        scope.launch { playSamples(bonusChimeBuffer) }
    }

    override fun playPowerUp() {
        if (isMuted) return
        scope.launch { playSamples(powerUpBuffer) }
    }

    override fun playBombExplosion() {
        if (isMuted) return
        scope.launch { playSamples(bombExplosionBuffer) }
    }

    override fun playMiss() {
        if (isMuted) return
        scope.launch { playSamples(missBuffer) }
    }

    override fun playGameOver() {
        if (isMuted) return
        scope.launch { playSamples(gameOverBuffer) }
    }

    override fun release() {
        // Individual static AudioTracks are released immediately after playback.
    }

    private fun playSamples(samples: ShortArray) {
        if (isMuted || samples.isEmpty()) return
        try {
            val byteSize = samples.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(byteSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                return
            }

            track.write(samples, 0, samples.size)
            track.play()

            val durationMs = (samples.size * 1000L) / SAMPLE_RATE + 40L
            Thread.sleep(durationMs)
            track.stop()
            track.release()
        } catch (_: Throwable) {
            // Audio hardware may be unavailable in headless test environments.
        }
    }

    private fun synthesizeSweep(
        startFreq: Double,
        endFreq: Double,
        durationMs: Int,
        volume: Double
    ): ShortArray {
        val count = (SAMPLE_RATE * durationMs) / 1000
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / count
            val freq = startFreq + (endFreq - startFreq) * t
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val env = (1.0 - t) * (if (i < 120) i / 120.0 else 1.0)
            val wave = sin(phase)
            out[i] = (wave * env * volume * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun synthesizeArpeggio(
        frequencies: List<Double>,
        noteDurationMs: Int,
        volume: Double
    ): ShortArray {
        val samplesPerNote = (SAMPLE_RATE * noteDurationMs) / 1000
        val total = samplesPerNote * frequencies.size
        val out = ShortArray(total)
        frequencies.forEachIndexed { idx, freq ->
            val offset = idx * samplesPerNote
            for (i in 0 until samplesPerNote) {
                val t = i.toDouble() / samplesPerNote
                val env = exp(-3.2 * t) * (if (i < 80) i / 80.0 else 1.0)
                val wave = 0.75 * sin(2.0 * PI * freq * i / SAMPLE_RATE) +
                    0.25 * sin(4.0 * PI * freq * i / SAMPLE_RATE)
                out[offset + i] = (wave * env * volume * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        return out
    }

    private fun synthesizeExplosion(durationMs: Int, volume: Double): ShortArray {
        val count = (SAMPLE_RATE * durationMs) / 1000
        val out = ShortArray(count)
        val rng = Random(42)
        var phase = 0.0
        var lowPass = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / count
            val subFreq = 135.0 * (1.0 - 0.72 * t)
            phase += 2.0 * PI * subFreq / SAMPLE_RATE
            val sub = sin(phase) * exp(-4.5 * t)
            val rawNoise = rng.nextDouble(-1.0, 1.0)
            lowPass = lowPass * 0.78 + rawNoise * 0.22
            val noiseBurst = lowPass * exp(-6.0 * t)
            val combined = (0.55 * sub + 0.45 * noiseBurst) * volume
            out[i] = (combined * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private companion object {
        const val SAMPLE_RATE = 22050
    }
}
