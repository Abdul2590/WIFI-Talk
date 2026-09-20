package com.example.audio

import android.media.AudioFormat

object AudioConstants {
    const val SAMPLE_RATE = 16000
    const val CHANNEL_IN_CONFIG = AudioFormat.CHANNEL_IN_MONO
    const val CHANNEL_OUT_CONFIG = AudioFormat.CHANNEL_OUT_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    // Ultra-low latency frame: 20ms @ 16kHz = 320 samples = 640 bytes (standard telecom frame)
    const val CHUNK_DURATION_MS = 20
    const val SAMPLES_PER_CHUNK = (SAMPLE_RATE * CHUNK_DURATION_MS) / 1000 // 320 samples
    const val BYTES_PER_SAMPLE = 2
    const val CHUNK_SIZE_BYTES = SAMPLES_PER_CHUNK * BYTES_PER_SAMPLE // 640 bytes

    const val DEFAULT_PORT = 50005
}
