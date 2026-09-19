package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioOutputManager
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.model.AudioOutputDevice
import com.example.model.AutoWifiState
import com.example.model.ChatMessage
import com.example.model.DiscoveredWifiNetwork
import com.example.model.PeerDevice
import com.example.model.PttMode
import com.example.model.TransmissionState
import com.example.model.UserProfile
import com.example.model.WalkieChannel
import com.example.model.WifiConnectionState
import com.example.model.WifiHotspotState
import com.example.network.NetworkManager
import com.example.network.WifiHotspotManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class WalkieViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("walkie_talkie_prefs", Context.MODE_PRIVATE)

    val deviceId: String = prefs.getString("device_id", null) ?: run {
        val newId = UUID.randomUUID().toString().take(8).uppercase()
        prefs.edit().putString("device_id", newId).apply()
        newId
    }

    private val _callSign = MutableStateFlow(
        prefs.getString("call_sign", "ALPHA-${deviceId.take(4)}") ?: "ALPHA-${deviceId.take(4)}"
    )
    val callSign: StateFlow<String> = _callSign.asStateFlow()

    private val _pttMode = MutableStateFlow(
        PttMode.valueOf(prefs.getString("ptt_mode", PttMode.HOLD_TO_TALK.name) ?: PttMode.HOLD_TO_TALK.name)
    )
    val pttMode: StateFlow<PttMode> = _pttMode.asStateFlow()

    private val _isRogerBeepEnabled = MutableStateFlow(
        prefs.getBoolean("roger_beep", true)
    )
    val isRogerBeepEnabled: StateFlow<Boolean> = _isRogerBeepEnabled.asStateFlow()

    private val _isLoopbackTestEnabled = MutableStateFlow(false)
    val isLoopbackTestEnabled: StateFlow<Boolean> = _isLoopbackTestEnabled.asStateFlow()

    private val _hasRecordPermission = MutableStateFlow(false)
    val hasRecordPermission: StateFlow<Boolean> = _hasRecordPermission.asStateFlow()

    private val _activeChannel = MutableStateFlow(
        WalkieChannel.getById(prefs.getInt("active_channel_id", 1))
    )
    val activeChannel: StateFlow<WalkieChannel> = _activeChannel.asStateFlow()

    private val networkManager = NetworkManager(
        context = application,
        scope = viewModelScope,
        myDeviceId = deviceId,
        initialCallSign = _callSign.value
    )

    private val audioRecorderManager = AudioRecorderManager(viewModelScope)
    private val audioPlayerManager = AudioPlayerManager(viewModelScope)

    private val audioOutputManager = AudioOutputManager(
        context = application,
        getAudioTrack = { audioPlayerManager.getAudioTrack() }
    )

    private val wifiHotspotManager = WifiHotspotManager(
        context = application,
        scope = viewModelScope,
        deviceId = deviceId,
        onNetworkChanged = {
            networkManager.reconnectSocket()
        }
    )

    val detectedAudioDevices: StateFlow<List<AudioOutputDevice>> = audioOutputManager.detectedDevices
    val selectedAudioDevice: StateFlow<AudioOutputDevice?> = audioOutputManager.selectedDevice

    val wifiHotspotState: StateFlow<WifiHotspotState> = wifiHotspotManager.hotspotState
    val autoWifiState: StateFlow<AutoWifiState> = wifiHotspotManager.autoWifiState
    val discoveredWalkieNetworks: StateFlow<List<DiscoveredWifiNetwork>> = wifiHotspotManager.discoveredNetworks

    val wifiState: StateFlow<WifiConnectionState> = networkManager.wifiState
    val peers: StateFlow<List<PeerDevice>> = networkManager.peers

    val isMuted: StateFlow<Boolean> = audioPlayerManager.isMuted
    val isMicEnabled: StateFlow<Boolean> = audioRecorderManager.isMicEnabled
    val isNoiseCancellationEnabled: StateFlow<Boolean> = audioRecorderManager.isNoiseCancellationEnabled
    val isEchoCancellationEnabled: StateFlow<Boolean> = audioRecorderManager.isEchoCancellationEnabled
    val isAudioClarityBoostEnabled: StateFlow<Boolean> = audioRecorderManager.isAudioClarityBoostEnabled

    // Messaging & Chat State
    private val _userProfile = MutableStateFlow(
        UserProfile(
            userName = prefs.getString("user_name", "") ?: "",
            mobileNumber = prefs.getString("mobile_number", "") ?: "",
            isMessagingEnabled = prefs.getBoolean("messaging_enabled", false)
        )
    )
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _unreadMessageCount = MutableStateFlow(0)
    val unreadMessageCount: StateFlow<Int> = _unreadMessageCount.asStateFlow()

    private val _volumeGain = MutableStateFlow(
        prefs.getFloat("volume_gain", audioPlayerManager.volumeGain).coerceIn(1.0f, 3.5f)
    )
    val volumeGain: StateFlow<Float> = _volumeGain.asStateFlow()

    private val _localTransmitting = MutableStateFlow(false)

    // Combined transmission state: Local Transmitting takes precedence over Remote Receiving
    val transmissionState: StateFlow<TransmissionState> = combine(
        _localTransmitting,
        networkManager.remoteTransmissionState,
        _activeChannel
    ) { localTx, remoteState, channel ->
        if (localTx) {
            TransmissionState.Transmitting(channel)
        } else {
            remoteState
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TransmissionState.Idle)

    // Combined audio level for UI VU meter / waveform
    val liveAudioLevel: StateFlow<Float> = combine(
        _localTransmitting,
        audioRecorderManager.outgoingAmplitude,
        audioPlayerManager.incomingAmplitude
    ) { localTx, outgoingAmp, incomingAmp ->
        if (localTx) outgoingAmp else incomingAmp
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    // Peers on the currently active channel
    val peersOnCurrentChannel: StateFlow<List<PeerDevice>> = combine(
        peers,
        _activeChannel
    ) { peerList, channel ->
        peerList.filter { it.channel == channel.id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val sequenceNumber = AtomicInteger(0)

    init {
        audioPlayerManager.volumeGain = _volumeGain.value

        // Restore microphone and audio clarity DSP preferences
        val savedMic = prefs.getBoolean("device_mic_enabled", true)
        audioRecorderManager.setMicEnabled(savedMic)

        val savedNoiseCancellation = prefs.getBoolean("noise_cancellation", true)
        audioRecorderManager.setNoiseCancellationEnabled(savedNoiseCancellation)

        val savedEchoCancellation = prefs.getBoolean("echo_cancellation", true)
        audioRecorderManager.setEchoCancellationEnabled(savedEchoCancellation)

        val savedClarity = prefs.getBoolean("audio_clarity_boost", true)
        audioRecorderManager.setAudioClarityBoostEnabled(savedClarity)

        // Sync user profile with network manager
        networkManager.myUserName = _userProfile.value.userName
        networkManager.myMobileNumber = _userProfile.value.mobileNumber
        networkManager.isMessagingEnabled = _userProfile.value.isMessagingEnabled

        // Pipe incoming network audio packets to AudioPlayer
        viewModelScope.launch {
            networkManager.incomingAudio.collect { pcmData ->
                // Play audio if not transmitting ourselves
                if (!_localTransmitting.value) {
                    audioPlayerManager.playChunk(pcmData)
                }
            }
        }

        // Collect incoming chat messages
        viewModelScope.launch {
            networkManager.incomingMessages.collect { message ->
                _chatMessages.value = _chatMessages.value + message
                _unreadMessageCount.value += 1
                vibrate(100)
            }
        }
    }

    fun setHasRecordPermission(granted: Boolean) {
        _hasRecordPermission.value = granted
    }

    fun selectChannel(channel: WalkieChannel) {
        if (_localTransmitting.value) {
            stopTransmitting()
        }
        _activeChannel.value = channel
        prefs.edit().putInt("active_channel_id", channel.id).apply()
        networkManager.switchChannel(channel)
    }

    fun startTransmitting() {
        if (!_hasRecordPermission.value) return
        if (_localTransmitting.value) return

        _localTransmitting.value = true
        vibrate(40)

        if (_isRogerBeepEnabled.value) {
            audioPlayerManager.playStartChirp()
        }

        sequenceNumber.set(0)
        networkManager.broadcastPttStart()

        val started = audioRecorderManager.startRecording { chunk ->
            val seq = sequenceNumber.incrementAndGet()
            networkManager.broadcastAudioChunk(chunk, seq)

            // If loopback test mode is active, also play back locally
            if (_isLoopbackTestEnabled.value) {
                audioPlayerManager.playChunk(chunk)
            }
        }

        if (!started) {
            _localTransmitting.value = false
            networkManager.broadcastPttEnd()
        }
    }

    fun stopTransmitting() {
        if (!_localTransmitting.value) return

        _localTransmitting.value = false
        audioRecorderManager.stopRecording()
        networkManager.broadcastPttEnd()

        vibrate(30)

        if (_isRogerBeepEnabled.value) {
            audioPlayerManager.playRogerBeep()
        }
    }

    fun handlePttPress() {
        if (_pttMode.value == PttMode.HOLD_TO_TALK) {
            startTransmitting()
        } else {
            // Toggle mode
            if (_localTransmitting.value) {
                stopTransmitting()
            } else {
                startTransmitting()
            }
        }
    }

    fun handlePttRelease() {
        if (_pttMode.value == PttMode.HOLD_TO_TALK) {
            stopTransmitting()
        }
    }

    fun updateCallSign(newCallSign: String) {
        val trimmed = newCallSign.trim().take(18)
        if (trimmed.isNotEmpty()) {
            _callSign.value = trimmed
            prefs.edit().putString("call_sign", trimmed).apply()
            networkManager.myCallSign = trimmed
        }
    }

    fun setPttMode(mode: PttMode) {
        if (_localTransmitting.value) {
            stopTransmitting()
        }
        _pttMode.value = mode
        prefs.edit().putString("ptt_mode", mode.name).apply()
    }

    fun toggleRogerBeep() {
        val updated = !_isRogerBeepEnabled.value
        _isRogerBeepEnabled.value = updated
        prefs.edit().putBoolean("roger_beep", updated).apply()
    }

    fun toggleMute() {
        audioPlayerManager.setMuted(!audioPlayerManager.isMuted.value)
    }

    fun toggleMic() {
        val updated = !audioRecorderManager.isMicEnabled.value
        audioRecorderManager.setMicEnabled(updated)
        prefs.edit().putBoolean("device_mic_enabled", updated).apply()
    }

    fun setMicEnabled(enabled: Boolean) {
        audioRecorderManager.setMicEnabled(enabled)
        prefs.edit().putBoolean("device_mic_enabled", enabled).apply()
    }

    fun toggleNoiseCancellation() {
        val updated = !audioRecorderManager.isNoiseCancellationEnabled.value
        audioRecorderManager.setNoiseCancellationEnabled(updated)
        prefs.edit().putBoolean("noise_cancellation", updated).apply()
    }

    fun toggleEchoCancellation() {
        val updated = !audioRecorderManager.isEchoCancellationEnabled.value
        audioRecorderManager.setEchoCancellationEnabled(updated)
        prefs.edit().putBoolean("echo_cancellation", updated).apply()
    }

    fun toggleAudioClarityBoost() {
        val updated = !audioRecorderManager.isAudioClarityBoostEnabled.value
        audioRecorderManager.setAudioClarityBoostEnabled(updated)
        prefs.edit().putBoolean("audio_clarity_boost", updated).apply()
    }

    fun setVolumeGain(gain: Float) {
        val coerced = gain.coerceIn(1.0f, 3.5f)
        _volumeGain.value = coerced
        audioPlayerManager.volumeGain = coerced
        prefs.edit().putFloat("volume_gain", coerced).apply()
    }

    fun toggleLoopbackTest() {
        val updated = !_isLoopbackTestEnabled.value
        _isLoopbackTestEnabled.value = updated
        networkManager.isLoopbackTestEnabled = updated
    }

    fun refreshNetwork() {
        networkManager.refreshNetworkInfo()
        wifiHotspotManager.scanForWalkieNetworks()
    }

    // Audio Output Speaker Controls
    fun selectAudioOutput(device: AudioOutputDevice) {
        audioOutputManager.selectSpeaker(device)
    }

    fun toggleSpeakerOutput() {
        audioOutputManager.toggleSpeakerOutput()
    }

    fun refreshAudioDevices() {
        audioOutputManager.refreshDetectedDevices()
    }

    // Wi-Fi 2.4 GHz Hotspot & Auto-connect Controls
    fun hasHotspotPermission(): Boolean = wifiHotspotManager.hasHotspotPermission()

    fun create24GhzHotspot() {
        wifiHotspotManager.create24GhzHotspot()
    }

    fun stopHotspot() {
        wifiHotspotManager.stopHotspot()
    }

    fun connectToWalkieNetwork(network: DiscoveredWifiNetwork) {
        wifiHotspotManager.connectToWalkieNetwork(network.ssid)
    }

    fun disconnectWalkieNetwork() {
        wifiHotspotManager.disconnectFromPeerNetwork()
    }

    fun toggleAutoConnect(enabled: Boolean) {
        wifiHotspotManager.toggleAutoConnect(enabled)
    }

    fun scanForWalkieNetworks() {
        wifiHotspotManager.scanForWalkieNetworks()
    }

    /**
     * Updates user profile (Name and Mobile Number) and enables/disables messaging.
     * Name and valid mobile number are strictly required to enable messaging.
     */
    fun saveUserProfile(name: String, mobile: String, enableMessaging: Boolean = true): Boolean {
        val cleanName = name.trim()
        val cleanMobile = mobile.trim()
        val digitCount = cleanMobile.filter { it.isDigit() }.length

        if (cleanName.isBlank() || digitCount < 7) {
            return false
        }

        val newProfile = UserProfile(
            userName = cleanName,
            mobileNumber = cleanMobile,
            isMessagingEnabled = enableMessaging
        )
        _userProfile.value = newProfile

        prefs.edit()
            .putString("user_name", cleanName)
            .putString("mobile_number", cleanMobile)
            .putBoolean("messaging_enabled", enableMessaging)
            .apply()

        networkManager.myUserName = cleanName
        networkManager.myMobileNumber = cleanMobile
        networkManager.isMessagingEnabled = enableMessaging

        // Send updated profile heartbeat immediately to all peers
        networkManager.sendHeartbeat()
        return true
    }

    fun disableMessaging() {
        val current = _userProfile.value
        val updated = current.copy(isMessagingEnabled = false)
        _userProfile.value = updated
        prefs.edit().putBoolean("messaging_enabled", false).apply()
        networkManager.isMessagingEnabled = false
        networkManager.sendHeartbeat()
    }

    /**
     * Sends a text chat message to connected devices on the current channel or direct recipient.
     */
    fun sendChatMessage(text: String, recipientId: String? = null): Boolean {
        val profile = _userProfile.value
        if (!profile.isMessagingEnabled || !profile.isValid) {
            return false
        }

        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false

        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = deviceId,
            senderName = profile.userName,
            senderMobile = profile.mobileNumber,
            senderCallSign = _callSign.value,
            text = trimmed,
            timestamp = System.currentTimeMillis(),
            channelId = _activeChannel.value.id,
            isFromMe = true,
            recipientId = recipientId
        )

        // Store locally immediately
        _chatMessages.value = _chatMessages.value + msg

        // Broadcast over Wi-Fi
        networkManager.broadcastTextMessage(msg)
        return true
    }

    fun markMessagesAsRead() {
        _unreadMessageCount.value = 0
    }

    fun clearChatMessages() {
        _chatMessages.value = emptyList()
        _unreadMessageCount.value = 0
    }

    private fun vibrate(durationMs: Long) {
        try {
            val context = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(durationMs)
            }
        } catch (e: Exception) {
            // Ignore if vibration unavailable
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioOutputManager.release()
        wifiHotspotManager.release()
        audioRecorderManager.release()
        audioPlayerManager.release()
        networkManager.release()
    }
}
