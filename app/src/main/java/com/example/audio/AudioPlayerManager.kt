package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class AudioPlayerManager(
    private val scope: CoroutineScope
) {
    private val tag = "AudioPlayerManager"

    private var audioTrack: AudioTrack? = null
    private var isInitialized = false

    fun getAudioTrack(): AudioTrack? = audioTrack

    private val _incomingAmplitude = MutableStateFlow(0f)
    val incomingAmplitude: StateFlow<Float> = _incomingAmplitude.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    // Default to a strong 2.2x audio boost (increased sound volume)
    var volumeGain: Float = 2.2f

    private var amplitudeDecayJob: Job? = null

    init {
        initAudioTrack()
    }

    @Synchronized
    private fun initAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                AudioConstants.SAMPLE_RATE,
                AudioConstants.CHANNEL_OUT_CONFIG,
                AudioConstants.AUDIO_FORMAT
            )
            // Use minimal buffer size to avoid playback queue latency (< 20ms)
            val bufferSize = max(minBufferSize, AudioConstants.CHUNK_SIZE_BYTES * 2)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(AudioConstants.SAMPLE_RATE)
                .setChannelMask(AudioConstants.CHANNEL_OUT_CONFIG)
                .setEncoding(AudioConstants.AUDIO_FORMAT)
                .build()

            val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .build()
            } else {
                AudioTrack(
                    audioAttributes,
                    audioFormat,
                    bufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
            }

            // Set track volume to max
            track.setVolume(1.0f)
            track.play()
            audioTrack = track
            isInitialized = true
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize AudioTrack", e)
            isInitialized = false
        }
    }

    fun playChunk(pcmData: ByteArray) {
        if (_isMuted.value) return

        if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            initAudioTrack()
        }

        val track = audioTrack ?: return

        try {
            // Apply volume boost with soft knee limiter to prevent harsh clipping distortion
            val processedPcm = if (volumeGain != 1.0f) {
                applyGain(pcmData, volumeGain)
            } else {
                pcmData
            }

            // Calculate RMS amplitude for visualizer
            val amp = calculateRms(processedPcm)
            _incomingAmplitude.value = amp

            // Decay amplitude after short delay
            amplitudeDecayJob?.cancel()
            amplitudeDecayJob = scope.launch(Dispatchers.Default) {
                kotlinx.coroutines.delay(60)
                _incomingAmplitude.value = 0f
            }

            // WRITE_NON_BLOCKING for minimal delay
            track.write(processedPcm, 0, processedPcm.size, AudioTrack.WRITE_NON_BLOCKING)
        } catch (e: Exception) {
            Log.e(tag, "Error playing audio chunk", e)
        }
    }

    fun playRogerBeep() {
        scope.launch(Dispatchers.IO) {
            val beep = ToneGenerator.createRogerBeep()
            audioTrack?.write(beep, 0, beep.size)
        }
    }

    fun playStartChirp() {
        scope.launch(Dispatchers.IO) {
            val chirp = ToneGenerator.createPttStartChirp()
            audioTrack?.write(chirp, 0, chirp.size)
        }
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
    }

    private fun applyGain(pcm: ByteArray, gain: Float): ByteArray {
        val result = ByteArray(pcm.size)
        // Soft clipping / dynamic compression curve for louder perceived volume
        for (i in 0 until pcm.size step 2) {
            if (i + 1 >= pcm.size) break
            val low = pcm[i].toInt() and 0xFF
            val high = pcm[i + 1].toInt()
            val sample = (high shl 8) or low
            val amplified = sample * gain

            // Soft-clip saturation when exceeding 85% of peak to prevent harsh digital square-wave clipping
            val maxVal = Short.MAX_VALUE.toFloat()
            val threshold = maxVal * 0.82f

            val boostedSample = when {
                amplified > threshold -> {
                    val excess = amplified - threshold
                    val margin = maxVal - threshold
                    (threshold + margin * (1f - kotlin.math.exp(-excess / margin))).toInt()
                }
                amplified < -threshold -> {
                    val excess = -amplified - threshold
                    val margin = maxVal - threshold
                    -(threshold + margin * (1f - kotlin.math.exp(-excess / margin))).toInt()
                }
                else -> amplified.toInt()
            }.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

            result[i] = (boostedSample and 0xFF).toByte()
            result[i + 1] = ((boostedSample shr 8) and 0xFF).toByte()
        }
        return result
    }

    private fun calculateRms(pcm: ByteArray): Float {
        var sumSquares = 0.0
        val sampleCount = pcm.size / 2
        if (sampleCount == 0) return 0f

        for (i in 0 until pcm.size step 2) {
            if (i + 1 >= pcm.size) break
            val low = pcm[i].toInt() and 0xFF
            val high = pcm[i + 1].toInt()
            val sample = (high shl 8) or low
            sumSquares += (sample * sample).toDouble()
        }

        val rms = sqrt(sumSquares / sampleCount)
        // Normalize 0..32767 to 0..1 with non-linear curve for visible responsiveness
        val normalized = (rms / 12000.0).toFloat().coerceIn(0f, 1f)
        return normalized
    }

    fun release() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(tag, "Error releasing AudioTrack", e)
        }
    }
}
