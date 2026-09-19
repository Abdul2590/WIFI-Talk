package com.example.audio

import kotlin.math.PI
import kotlin.math.sin

object ToneGenerator {

    /**
     * Generates a 16-bit mono PCM buffer at 16000 Hz with the given frequency, duration, and volume.
     * Includes a smooth cosine attack/decay envelope to prevent speaker popping/clicking.
     */
    fun generateTone(
        frequencyHz: Double,
        durationMs: Int,
        volume: Float = 0.5f,
        sampleRate: Int = AudioConstants.SAMPLE_RATE
    ): ByteArray {
        val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val pcm = ByteArray(totalSamples * 2)

        val fadeSamples = (sampleRate * 0.005).toInt() // 5ms fade in/out
        for (i in 0 until totalSamples) {
            val angle = 2.0 * PI * i / (sampleRate / frequencyHz)
            var sampleValue = sin(angle) * volume

            // Envelope
            if (i < fadeSamples) {
                sampleValue *= (i.toDouble() / fadeSamples)
            } else if (i > totalSamples - fadeSamples) {
                sampleValue *= ((totalSamples - i).toDouble() / fadeSamples)
            }

            val shortVal = (sampleValue * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            pcm[i * 2] = (shortVal.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((shortVal.toInt() shr 8) and 0xFF).toByte()
        }

        return pcm
    }

    /**
     * Generates the classic tactical "Roger Beep" (single 1050 Hz beep).
     */
    fun createRogerBeep(): ByteArray {
        return generateTone(1050.0, 60, volume = 0.45f)
    }

    /**
     * Generates a two-tone PTT start radio chirp (750 Hz + 1100 Hz).
     */
    fun createPttStartChirp(): ByteArray {
        val tone1 = generateTone(750.0, 25, volume = 0.35f)
        val tone2 = generateTone(1150.0, 35, volume = 0.4f)
        return tone1 + tone2
    }
}
