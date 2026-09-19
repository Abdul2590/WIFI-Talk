package com.example.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import com.example.model.AudioOutputDevice
import com.example.model.AudioOutputType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioOutputManager(
    private val context: Context,
    private val getAudioTrack: () -> AudioTrack?
) {
    private val tag = "AudioOutputManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs: SharedPreferences = context.getSharedPreferences("walkie_audio_prefs", Context.MODE_PRIVATE)

    private val _detectedDevices = MutableStateFlow<List<AudioOutputDevice>>(emptyList())
    val detectedDevices: StateFlow<List<AudioOutputDevice>> = _detectedDevices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<AudioOutputDevice?>(null)
    val selectedDevice: StateFlow<AudioOutputDevice?> = _selectedDevice.asStateFlow()

    private var deviceCallback: AudioDeviceCallback? = null

    init {
        registerDeviceCallback()
        refreshDetectedDevices()
    }

    private fun registerDeviceCallback() {
        deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                Log.d(tag, "Audio devices added: ${addedDevices?.size}")
                refreshDetectedDevices()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                Log.d(tag, "Audio devices removed: ${removedDevices?.size}")
                refreshDetectedDevices()
            }
        }
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
    }

    fun refreshDetectedDevices() {
        val rawDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val list = mutableListOf<AudioOutputDevice>()

        var hasSpeaker = false
        var hasEarpiece = false

        for (info in rawDevices) {
            val type = mapAudioDeviceType(info.type)
            if (type == AudioOutputType.LOUDSPEAKER) hasSpeaker = true
            if (type == AudioOutputType.EARPIECE) hasEarpiece = true

            val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.productName.isNotEmpty()) {
                info.productName.toString()
            } else {
                defaultNameForType(type)
            }

            list.add(
                AudioOutputDevice(
                    id = info.id.toString(),
                    name = deviceName,
                    type = type,
                    isSelected = false,
                    description = descriptionForType(type)
                )
            )
        }

        // Guarantee primary outputs are always present as valid targets even if device flags vary
        if (!hasSpeaker) {
            list.add(
                0,
                AudioOutputDevice(
                    id = "builtin_speaker",
                    name = "Built-in Speaker",
                    type = AudioOutputType.LOUDSPEAKER,
                    isSelected = false,
                    description = "Loudspeaker broadcast (Default)"
                )
            )
        }
        if (!hasEarpiece) {
            list.add(
                AudioOutputDevice(
                    id = "builtin_earpiece",
                    name = "Phone Receiver / Earpiece",
                    type = AudioOutputType.EARPIECE,
                    isSelected = false,
                    description = "Private discrete audio"
                )
            )
        }

        // Deduplicate by type and name
        val distinctDevices = list.distinctBy { it.type to it.name }

        // Restore previously preferred or default device
        val savedId = prefs.getString("preferred_speaker_id", null)
        val targetDevice = distinctDevices.firstOrNull { it.id == savedId }
            ?: distinctDevices.firstOrNull { it.type == AudioOutputType.LOUDSPEAKER }
            ?: distinctDevices.firstOrNull()

        val updatedList = distinctDevices.map { dev ->
            dev.copy(
                isSelected = dev.id == targetDevice?.id,
                isDefault = dev.type == AudioOutputType.LOUDSPEAKER
            )
        }

        _detectedDevices.value = updatedList
        _selectedDevice.value = targetDevice?.copy(isSelected = true)

        targetDevice?.let { applyAudioRouting(it) }
    }

    fun selectSpeaker(device: AudioOutputDevice) {
        prefs.edit().putString("preferred_speaker_id", device.id).apply()

        _selectedDevice.value = device.copy(isSelected = true)
        _detectedDevices.value = _detectedDevices.value.map {
            it.copy(isSelected = it.id == device.id)
        }

        applyAudioRouting(device)
    }

    fun toggleSpeakerOutput() {
        val current = _selectedDevice.value
        val all = _detectedDevices.value
        if (all.size <= 1) return

        // Toggle between Loudspeaker and the next available device (e.g. Bluetooth or Earpiece)
        val next = if (current?.type == AudioOutputType.LOUDSPEAKER) {
            all.firstOrNull { it.type != AudioOutputType.LOUDSPEAKER } ?: all[0]
        } else {
            all.firstOrNull { it.type == AudioOutputType.LOUDSPEAKER } ?: all[0]
        }
        selectSpeaker(next)
    }

    private fun applyAudioRouting(device: AudioOutputDevice) {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val availableCommDevices = audioManager.availableCommunicationDevices
                val matched = availableCommDevices.firstOrNull { info ->
                    info.id.toString() == device.id || mapAudioDeviceType(info.type) == device.type
                }
                if (matched != null) {
                    val success = audioManager.setCommunicationDevice(matched)
                    Log.d(tag, "setCommunicationDevice (${matched.productName}) result: $success")
                } else if (device.type == AudioOutputType.LOUDSPEAKER) {
                    val speakerInfo = availableCommDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speakerInfo != null) {
                        audioManager.setCommunicationDevice(speakerInfo)
                    } else {
                        audioManager.clearCommunicationDevice()
                    }
                } else {
                    audioManager.clearCommunicationDevice()
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = (device.type == AudioOutputType.LOUDSPEAKER)

                @Suppress("DEPRECATION")
                if (device.type == AudioOutputType.BLUETOOTH) {
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                } else {
                    if (audioManager.isBluetoothScoOn) {
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                    }
                }
            }

            // Route AudioTrack directly if possible
            val track = getAudioTrack()
            if (track != null) {
                val rawDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val matchedInfo = rawDevices.firstOrNull {
                    it.id.toString() == device.id || mapAudioDeviceType(it.type) == device.type
                }
                if (matchedInfo != null) {
                    track.preferredDevice = matchedInfo
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to route audio to ${device.name}", e)
        }
    }

    fun release() {
        try {
            deviceCallback?.let { audioManager.unregisterAudioDeviceCallback(it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.e(tag, "Error releasing AudioOutputManager", e)
        }
    }

    private fun mapAudioDeviceType(type: Int): AudioOutputType {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioOutputType.LOUDSPEAKER
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioOutputType.EARPIECE
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> AudioOutputType.BLUETOOTH
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> AudioOutputType.WIRED_HEADSET
            else -> AudioOutputType.UNKNOWN
        }
    }

    private fun defaultNameForType(type: AudioOutputType): String {
        return when (type) {
            AudioOutputType.LOUDSPEAKER -> "Built-in Speaker"
            AudioOutputType.EARPIECE -> "Earpiece / Receiver"
            AudioOutputType.BLUETOOTH -> "Bluetooth Audio Device"
            AudioOutputType.WIRED_HEADSET -> "Wired Headphones"
            AudioOutputType.USB_AUDIO -> "USB Audio Device"
            AudioOutputType.UNKNOWN -> "External Speaker"
        }
    }

    private fun descriptionForType(type: AudioOutputType): String {
        return when (type) {
            AudioOutputType.LOUDSPEAKER -> "Hands-free tactical broadcast"
            AudioOutputType.EARPIECE -> "Private ear-to-phone comms"
            AudioOutputType.BLUETOOTH -> "Wireless tactical headset"
            AudioOutputType.WIRED_HEADSET -> "Low latency wired audio"
            AudioOutputType.USB_AUDIO -> "USB digital audio interface"
            AudioOutputType.UNKNOWN -> "Auxiliary audio output"
        }
    }
}
