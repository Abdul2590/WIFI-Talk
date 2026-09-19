package com.example.model

/**
 * Tactical channel configuration.
 */
data class WalkieChannel(
    val id: Int,
    val name: String,
    val frequency: String,
    val description: String,
    val multicastGroup: String = "239.255.42.$id",
    val port: Int = 50005
) {
    companion object {
        val ALL_CHANNELS = listOf(
            WalkieChannel(1, "CH-1 GENERAL", "446.006 MHz", "Default general broadcast channel"),
            WalkieChannel(2, "CH-2 OPERATIONS", "446.018 MHz", "Team ops & coordination"),
            WalkieChannel(3, "CH-3 TACTICAL", "446.031 MHz", "Field tactical comms"),
            WalkieChannel(4, "CH-4 LOGISTICS", "446.044 MHz", "Supply and logistics"),
            WalkieChannel(5, "CH-5 SECURITY", "446.056 MHz", "Security and safety patrol"),
            WalkieChannel(6, "CH-6 EMERGENCY", "446.069 MHz", "Priority emergency broadcast"),
            WalkieChannel(7, "CH-7 DISPATCH", "446.081 MHz", "Base station dispatch"),
            WalkieChannel(8, "CH-8 PRIVATE", "446.094 MHz", "Direct peer-to-peer comms")
        )

        fun getById(id: Int): WalkieChannel {
            return ALL_CHANNELS.firstOrNull { it.id == id } ?: ALL_CHANNELS[0]
        }
    }
}

/**
 * User profile for messaging identification (Name & Mobile Number).
 */
data class UserProfile(
    val userName: String = "",
    val mobileNumber: String = "",
    val isMessagingEnabled: Boolean = false
) {
    val isValid: Boolean
        get() = userName.isNotBlank() && mobileNumber.isNotBlank() && mobileNumber.filter { it.isDigit() }.length >= 7
}

/**
 * Message sent or received between connected devices.
 */
data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderMobile: String,
    val senderCallSign: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val channelId: Int = 0, // 0 = Direct peer chat or broadcast channel
    val isFromMe: Boolean = false,
    val recipientId: String? = null // null = broadcast to all peers on channel/mesh
)

/**
 * A device detected on the local Wi-Fi network.
 */
data class PeerDevice(
    val id: String,
    val callSign: String,
    val ipAddress: String,
    val channel: Int,
    val isTransmitting: Boolean = false,
    val lastSeenMs: Long = System.currentTimeMillis(),
    val userName: String = "",
    val mobileNumber: String = "",
    val isMessagingEnabled: Boolean = false
)

/**
 * Current transmission state of the radio.
 */
sealed interface TransmissionState {
    object Idle : TransmissionState

    data class Transmitting(
        val channel: WalkieChannel,
        val startedAtMs: Long = System.currentTimeMillis()
    ) : TransmissionState

    data class Receiving(
        val speakerId: String,
        val speakerCallSign: String,
        val channel: WalkieChannel,
        val startedAtMs: Long = System.currentTimeMillis()
    ) : TransmissionState
}

/**
 * PTT Interaction mode: Hold to talk vs Tap to toggle.
 */
enum class PttMode {
    HOLD_TO_TALK,
    TAP_TO_TOGGLE
}

/**
 * Network connection status.
 */
data class WifiConnectionState(
    val isConnected: Boolean = false,
    val ssid: String = "No Wi-Fi",
    val ipAddress: String = "0.0.0.0",
    val broadcastAddress: String = "255.255.255.255",
    val isMulticastLockAcquired: Boolean = false
)

/**
 * Audio output hardware speaker types.
 */
enum class AudioOutputType {
    LOUDSPEAKER,
    EARPIECE,
    BLUETOOTH,
    WIRED_HEADSET,
    USB_AUDIO,
    UNKNOWN
}

/**
 * Detected audio speaker device.
 */
data class AudioOutputDevice(
    val id: String,
    val name: String,
    val type: AudioOutputType,
    val isSelected: Boolean = false,
    val isDefault: Boolean = false,
    val description: String = ""
)

/**
 * 2.4 GHz Local-Only Hotspot state created by this device.
 */
data class WifiHotspotState(
    val isHosting: Boolean = false,
    val isStarting: Boolean = false,
    val ssid: String = "",
    val password: String = "",
    val isOpenNoPassword: Boolean = true,
    val band: String = "2.4 GHz",
    val ipAddress: String = "192.168.43.1",
    val statusMessage: String = "Hotspot Inactive",
    val connectedPeersCount: Int = 0
)

/**
 * Nearby Wi-Fi network discovered during scanning.
 */
data class DiscoveredWifiNetwork(
    val ssid: String,
    val bssid: String,
    val frequencyMhz: Int = 2412,
    val level: Int = -50,
    val isWalkieNetwork: Boolean = false,
    val isConnected: Boolean = false,
    val isOpen: Boolean = true,
    val securityInfo: String = "Open (No password)"
) {
    val is24Ghz: Boolean
        get() = frequencyMhz in 2400..2500
}

/**
 * State for automatic discovery & connection to peer walkie SSIDs.
 */
data class AutoWifiState(
    val isScanning: Boolean = false,
    val isConnecting: Boolean = false,
    val targetSsid: String? = null,
    val connectedSsid: String? = null,
    val statusMessage: String = "Ready",
    val autoConnectEnabled: Boolean = true
)

