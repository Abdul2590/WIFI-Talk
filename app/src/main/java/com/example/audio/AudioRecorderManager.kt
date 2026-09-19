package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.sqrt

class AudioRecorderManager(
    private val scope: CoroutineScope
) {
    private val tag = "AudioRecorderManager"

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    private val isRecording = AtomicBoolean(false)
    private var recordingJob: Job? = null

    // Mic enable/disable state
    private val _isMicEnabled = MutableStateFlow(true)
    val isMicEnabled: StateFlow<Boolean> = _isMicEnabled.asStateFlow()

    // Noise cancellation toggle
    private val _isNoiseCancellationEnabled = MutableStateFlow(true)
    val isNoiseCancellationEnabled: StateFlow<Boolean> = _isNoiseCancellationEnabled.asStateFlow()

    // Echo cancellation toggle
    private val _isEchoCancellationEnabled = MutableStateFlow(true)
    val isEchoCancellationEnabled: StateFlow<Boolean> = _isEchoCancellationEnabled.asStateFlow()

    // Audio clarity boost toggle
    private val _isAudioClarityBoostEnabled = MutableStateFlow(true)
    val isAudioClarityBoostEnabled: StateFlow<Boolean> = _isAudioClarityBoostEnabled.asStateFlow()

    private val audioProcessor = AudioProcessor()

    private val _outgoingAmplitude = MutableStateFlow(0f)
    val outgoingAmplitude: StateFlow<Float> = _outgoingAmplitude.asStateFlow()

    fun setMicEnabled(enabled: Boolean) {
        _isMicEnabled.value = enabled
        if (!enabled) {
            _outgoingAmplitude.value = 0f
        }
    }

    fun setNoiseCancellationEnabled(enabled: Boolean) {
        _isNoiseCancellationEnabled.value = enabled
        audioProcessor.isNoiseCancellationEnabled = enabled
        try {
            noiseSuppressor?.enabled = enabled
        } catch (_: Exception) {}
    }

    fun setEchoCancellationEnabled(enabled: Boolean) {
        _isEchoCancellationEnabled.value = enabled
        audioProcessor.isEchoCancellationEnabled = enabled
        try {
            echoCanceler?.enabled = enabled
        } catch (_: Exception) {}
    }

    fun setAudioClarityBoostEnabled(enabled: Boolean) {
        _isAudioClarityBoostEnabled.value = enabled
        audioProcessor.isAudioClarityBoostEnabled = enabled
    }

    @SuppressLint("MissingPermission")
    fun startRecording(onChunkCaptured: (ByteArray) -> Unit): Boolean {
        if (isRecording.get()) return true

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                AudioConstants.SAMPLE_RATE,
                AudioConstants.CHANNEL_IN_CONFIG,
                AudioConstants.AUDIO_FORMAT
            )
            if (minBufferSize <= 0) {
                Log.e(tag, "Invalid minBufferSize: $minBufferSize")
                return false
            }

            val bufferSize = max(minBufferSize, AudioConstants.CHUNK_SIZE_BYTES * 2)

            // Try VOICE_COMMUNICATION first for AEC, fallback to MIC
            var record: AudioRecord? = null
            try {
                record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    AudioConstants.SAMPLE_RATE,
                    AudioConstants.CHANNEL_IN_CONFIG,
                    AudioConstants.AUDIO_FORMAT,
                    bufferSize
                )
            } catch (e: Exception) {
                Log.w(tag, "VOICE_COMMUNICATION source failed, falling back to MIC", e)
            }

            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    AudioConstants.SAMPLE_RATE,
                    AudioConstants.CHANNEL_IN_CONFIG,
                    AudioConstants.AUDIO_FORMAT,
                    bufferSize
                )
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(tag, "AudioRecord failed to initialize")
                record.release()
                return false
            }

            // Enable hardware Echo Cancellation and Noise Suppression if available and enabled
            val sessionId = record.audioSessionId
            if (AcousticEchoCanceler.isAvailable()) {
                try {
                    echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                        enabled = _isEchoCancellationEnabled.value
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Failed to enable AcousticEchoCanceler", e)
                }
            }

            if (NoiseSuppressor.isAvailable()) {
                try {
                    noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                        enabled = _isNoiseCancellationEnabled.value
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Failed to enable NoiseSuppressor", e)
                }
            }

            record.startRecording()
            audioRecord = record
            isRecording.set(true)

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(AudioConstants.CHUNK_SIZE_BYTES)
                audioProcessor.reset()

                while (isActive && isRecording.get()) {
                    val bytesRead = record.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        val rawChunk = buffer.copyOf(bytesRead)

                        // If device microphone is disabled, send silence chunk and zero amplitude
                        if (!_isMicEnabled.value) {
                            _outgoingAmplitude.value = 0f
                            val silence = ByteArray(bytesRead)
                            onChunkCaptured(silence)
                            continue
                        }

                        // Apply DSP: Noise Cancellation, Echo Suppression & Audio Clarity Formant Boost
                        val processedChunk = audioProcessor.processCapture(rawChunk)

                        // Calculate RMS amplitude for visualizer
                        val amp = calculateRms(processedChunk)
                        _outgoingAmplitude.value = amp

                        onChunkCaptured(processedChunk)
                    }
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(tag, "Error starting AudioRecord", e)
            stopRecording()
            return false
        }
    }

    fun stopRecording() {
        if (!isRecording.getAndSet(false)) return

        recordingJob?.cancel()
        recordingJob = null
        _outgoingAmplitude.value = 0f

        try {
            audioRecord?.stop()
            echoCanceler?.release()
            noiseSuppressor?.release()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping AudioRecord", e)
        } finally {
            echoCanceler = null
            noiseSuppressor = null
            audioRecord = null
        }
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
        return (rms / 12000.0).toFloat().coerceIn(0f, 1f)
    }

    fun release() {
        stopRecording()
    }
}
