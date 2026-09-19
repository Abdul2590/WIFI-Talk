package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log
import com.example.audio.AudioConstants
import com.example.model.ChatMessage
import com.example.model.PeerDevice
import com.example.model.TransmissionState
import com.example.model.WalkieChannel
import com.example.model.WifiConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap

class NetworkManager(
    private val context: Context,
    private val scope: CoroutineScope,
    val myDeviceId: String,
    initialCallSign: String
) {
    private val tag = "NetworkManager"

    var myCallSign: String = initialCallSign
    var myUserName: String = ""
    var myMobileNumber: String = ""
    var isMessagingEnabled: Boolean = false

    private var multicastSocket: MulticastSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val _wifiState = MutableStateFlow(WifiConnectionState())
    val wifiState: StateFlow<WifiConnectionState> = _wifiState.asStateFlow()

    private val _peers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val peers: StateFlow<List<PeerDevice>> = _peers.asStateFlow()

    private val peerMap = ConcurrentHashMap<String, PeerDevice>()

    private val _incomingAudio = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val incomingAudio: SharedFlow<ByteArray> = _incomingAudio.asSharedFlow()

    private val _incomingMessages = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 128)
    val incomingMessages: SharedFlow<ChatMessage> = _incomingMessages.asSharedFlow()

    private val _remoteTransmissionState = MutableStateFlow<TransmissionState>(TransmissionState.Idle)
    val remoteTransmissionState: StateFlow<TransmissionState> = _remoteTransmissionState.asStateFlow()

    var currentChannel: WalkieChannel = WalkieChannel.ALL_CHANNELS[0]
        private set

    var isTransmittingLocally: Boolean = false
    var isLoopbackTestEnabled: Boolean = false

    private var receiverJob: Job? = null
    private var heartbeatJob: Job? = null
    private var activeSpeakerTimeoutJob: Job? = null

    private var broadcastInetAddress: InetAddress? = null
    private var multicastInetAddress: InetAddress? = null
    private var activeNetworkInterface: NetworkInterface? = null

    init {
        acquireLocks()
        refreshNetworkInfo()
        setupSocket()
        startReceiver()
        startHeartbeat()
    }

    private fun acquireLocks() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("WalkieTalkieMulticast").apply {
                setReferenceCounted(true)
                acquire()
            }

            val powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WalkieTalkie:WakeLock").apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10 min safety timeout
            }

            _wifiState.value = _wifiState.value.copy(isMulticastLockAcquired = multicastLock?.isHeld == true)
        } catch (e: Exception) {
            Log.e(tag, "Failed to acquire multicast/wake locks", e)
        }
    }

    fun refreshNetworkInfo() {
        try {
            var localIp = "127.0.0.1"
            var broadcastIp = "255.255.255.255"
            var matchedInterface: NetworkInterface? = null

            val interfaces = NetworkInterface.getNetworkInterfaces()
            val candidateInterfaces = mutableListOf<Pair<NetworkInterface, String>>()

            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (intf.isLoopback || !intf.isUp) continue

                for (addr in intf.interfaceAddresses) {
                    val inetAddr = addr.address
                    if (inetAddr is Inet4Address && !inetAddr.isLoopbackAddress) {
                        val hostIp = inetAddr.hostAddress ?: ""
                        candidateInterfaces.add(Pair(intf, hostIp))

                        addr.broadcast?.let {
                            broadcastIp = it.hostAddress ?: broadcastIp
                            broadcastInetAddress = it
                        }
                    }
                }
            }

            // Prioritize Wi-Fi or tethering/hotspot interfaces (wlan, ap, softap) over cellular (rmnet, ccmni)
            val preferred = candidateInterfaces.firstOrNull { (intf, _) ->
                val name = intf.name.lowercase()
                name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("softap") || name.startsWith("rndis")
            } ?: candidateInterfaces.firstOrNull()

            if (preferred != null) {
                matchedInterface = preferred.first
                localIp = preferred.second
            }

            activeNetworkInterface = matchedInterface
            if (broadcastInetAddress == null) {
                broadcastInetAddress = InetAddress.getByName(broadcastIp)
            }

            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            _wifiState.value = WifiConnectionState(
                isConnected = isWifi || localIp != "127.0.0.1",
                ssid = if (isWifi) "Wi-Fi Connected" else if (localIp != "127.0.0.1") "Direct Mesh (${matchedInterface?.name ?: "Wi-Fi"})" else "No Wi-Fi",
                ipAddress = localIp,
                broadcastAddress = broadcastIp,
                isMulticastLockAcquired = multicastLock?.isHeld == true
            )
        } catch (e: Exception) {
            Log.e(tag, "Error inspecting network interface", e)
        }
    }

    @Synchronized
    fun reconnectSocket() {
        refreshNetworkInfo()
        setupSocket()
        sendHeartbeat()
    }

    @Synchronized
    private fun setupSocket() {
        try {
            multicastSocket?.close()

            val socket = MulticastSocket(AudioConstants.DEFAULT_PORT).apply {
                reuseAddress = true
                timeToLive = 4 // Subnet scope
                broadcast = true
            }

            multicastSocket = socket
            joinMulticastGroup(currentChannel)
        } catch (e: Exception) {
            Log.e(tag, "Error setting up MulticastSocket", e)
        }
    }

    @Synchronized
    fun switchChannel(channel: WalkieChannel) {
        if (currentChannel.id == channel.id) return

        leaveMulticastGroup(currentChannel)
        currentChannel = channel
        joinMulticastGroup(channel)

        // Reset any current incoming transmission
        _remoteTransmissionState.value = TransmissionState.Idle

        // Send immediate presence announcement on new channel
        sendHeartbeat()
    }

    private fun joinMulticastGroup(channel: WalkieChannel) {
        val socket = multicastSocket ?: return
        try {
            val group = InetAddress.getByName(channel.multicastGroup)
            multicastInetAddress = group

            if (activeNetworkInterface != null) {
                socket.joinGroup(InetSocketAddress(group, AudioConstants.DEFAULT_PORT), activeNetworkInterface)
            } else {
                @Suppress("DEPRECATION")
                socket.joinGroup(group)
            }
            Log.d(tag, "Joined multicast group: ${channel.multicastGroup} on port ${AudioConstants.DEFAULT_PORT}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to join multicast group ${channel.multicastGroup}", e)
        }
    }

    private fun leaveMulticastGroup(channel: WalkieChannel) {
        val socket = multicastSocket ?: return
        try {
            val group = InetAddress.getByName(channel.multicastGroup)
            if (activeNetworkInterface != null) {
                socket.leaveGroup(InetSocketAddress(group, AudioConstants.DEFAULT_PORT), activeNetworkInterface)
            } else {
                @Suppress("DEPRECATION")
                socket.leaveGroup(group)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to leave multicast group ${channel.multicastGroup}", e)
        }
    }

    private fun startReceiver() {
        receiverJob?.cancel()
        receiverJob = scope.launch(Dispatchers.IO) {
            val receiveBuffer = ByteArray(2048)

            while (isActive) {
                try {
                    val socket = multicastSocket ?: run {
                        delay(500)
                        return@launch
                    }

                    val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)
                    socket.receive(packet)

                    val receivedBytes = packet.data
                    val length = packet.length
                    val senderIp = packet.address.hostAddress ?: ""

                    val walkiePacket = WalkiePacket.deserialize(receivedBytes, length) ?: continue

                    // Ignore own packets unless loopback self-test is active
                    if (walkiePacket.senderId == myDeviceId && !isLoopbackTestEnabled) {
                        continue
                    }

                    handleIncomingPacket(walkiePacket, senderIp)
                } catch (e: Exception) {
                    if (isActive) {
                        delay(200)
                    }
                }
            }
        }
    }

    private fun handleIncomingPacket(packet: WalkiePacket, senderIp: String) {
        when (packet) {
            is WalkiePacket.PttStart -> {
                if (packet.channelId == currentChannel.id) {
                    _remoteTransmissionState.value = TransmissionState.Receiving(
                        speakerId = packet.senderId,
                        speakerCallSign = packet.callSign,
                        channel = currentChannel,
                        startedAtMs = packet.timestamp
                    )
                    resetSpeakerTimeout(packet.senderId)
                }
                updatePeer(packet.senderId, packet.callSign, senderIp, packet.channelId, isTransmitting = true)
            }

            is WalkiePacket.AudioData -> {
                if (packet.channelId == currentChannel.id) {
                    _incomingAudio.tryEmit(packet.pcmData)

                    if (_remoteTransmissionState.value !is TransmissionState.Receiving) {
                        val peer = peerMap[packet.senderId]
                        _remoteTransmissionState.value = TransmissionState.Receiving(
                            speakerId = packet.senderId,
                            speakerCallSign = peer?.callSign ?: "Radio ${packet.senderId.take(4)}",
                            channel = currentChannel
                        )
                    }
                    resetSpeakerTimeout(packet.senderId)
                }
            }

            is WalkiePacket.PttEnd -> {
                if (packet.channelId == currentChannel.id) {
                    activeSpeakerTimeoutJob?.cancel()
                    _remoteTransmissionState.value = TransmissionState.Idle
                }
                peerMap[packet.senderId]?.let {
                    peerMap[packet.senderId] = it.copy(isTransmitting = false, lastSeenMs = System.currentTimeMillis())
                    syncPeersList()
                }
            }

            is WalkiePacket.Heartbeat -> {
                updatePeer(
                    id = packet.senderId,
                    callSign = packet.callSign,
                    ip = senderIp,
                    channel = packet.channelId,
                    isTransmitting = packet.isTransmitting,
                    userName = packet.userName,
                    mobileNumber = packet.mobileNumber,
                    isMessagingEnabled = packet.isMessagingEnabled
                )
            }

            is WalkiePacket.TextMessage -> {
                // Ignore if targeted to someone else
                if (packet.recipientId == null || packet.recipientId == myDeviceId) {
                    val chatMessage = ChatMessage(
                        id = packet.messageId,
                        senderId = packet.senderId,
                        senderName = packet.senderName,
                        senderMobile = packet.senderMobile,
                        senderCallSign = packet.senderCallSign,
                        text = packet.text,
                        timestamp = packet.timestamp,
                        channelId = packet.channelId,
                        isFromMe = packet.senderId == myDeviceId,
                        recipientId = packet.recipientId
                    )
                    _incomingMessages.tryEmit(chatMessage)
                }

                // Also update peer presence
                updatePeer(
                    id = packet.senderId,
                    callSign = packet.senderCallSign,
                    ip = senderIp,
                    channel = packet.channelId,
                    isTransmitting = false,
                    userName = packet.senderName,
                    mobileNumber = packet.senderMobile,
                    isMessagingEnabled = true
                )
            }
        }
    }

    private fun resetSpeakerTimeout(speakerId: String) {
        activeSpeakerTimeoutJob?.cancel()
        activeSpeakerTimeoutJob = scope.launch(Dispatchers.Default) {
            // If no audio packet or PTT_END arrives within 750ms, assume transmission finished
            delay(750)
            if (_remoteTransmissionState.value is TransmissionState.Receiving) {
                _remoteTransmissionState.value = TransmissionState.Idle
                peerMap[speakerId]?.let {
                    peerMap[speakerId] = it.copy(isTransmitting = false)
                    syncPeersList()
                }
            }
        }
    }

    private fun updatePeer(
        id: String,
        callSign: String,
        ip: String,
        channel: Int,
        isTransmitting: Boolean,
        userName: String = "",
        mobileNumber: String = "",
        isMessagingEnabled: Boolean = false
    ) {
        if (id == myDeviceId) return
        val existing = peerMap[id]
        val peer = PeerDevice(
            id = id,
            callSign = callSign,
            ipAddress = ip,
            channel = channel,
            isTransmitting = isTransmitting,
            lastSeenMs = System.currentTimeMillis(),
            userName = if (userName.isNotBlank()) userName else existing?.userName ?: "",
            mobileNumber = if (mobileNumber.isNotBlank()) mobileNumber else existing?.mobileNumber ?: "",
            isMessagingEnabled = isMessagingEnabled || (existing?.isMessagingEnabled == true)
        )
        peerMap[id] = peer
        syncPeersList()
    }

    private fun syncPeersList() {
        val now = System.currentTimeMillis()
        // Prune peers not heard from in 7 seconds
        peerMap.entries.removeIf { now - it.value.lastSeenMs > 7000 }
        _peers.value = peerMap.values.toList().sortedBy { it.callSign }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                sendHeartbeat()
                delay(2500)
                syncPeersList()
            }
        }
    }

    fun sendHeartbeat() {
        val heartbeat = WalkiePacket.Heartbeat(
            senderId = myDeviceId,
            callSign = myCallSign,
            channelId = currentChannel.id,
            isTransmitting = isTransmittingLocally,
            userName = myUserName,
            mobileNumber = myMobileNumber,
            isMessagingEnabled = isMessagingEnabled
        )
        sendPacket(heartbeat)
    }

    fun broadcastTextMessage(message: ChatMessage) {
        val packet = WalkiePacket.TextMessage(
            messageId = message.id,
            senderId = myDeviceId,
            senderName = message.senderName,
            senderMobile = message.senderMobile,
            senderCallSign = myCallSign,
            channelId = message.channelId,
            text = message.text,
            timestamp = message.timestamp,
            recipientId = message.recipientId
        )
        sendPacket(packet)
    }

    fun broadcastPttStart() {
        isTransmittingLocally = true
        val packet = WalkiePacket.PttStart(
            senderId = myDeviceId,
            callSign = myCallSign,
            channelId = currentChannel.id,
            timestamp = System.currentTimeMillis()
        )
        sendPacket(packet)
    }

    fun broadcastAudioChunk(pcmData: ByteArray, sequenceNumber: Int) {
        val packet = WalkiePacket.AudioData(
            senderId = myDeviceId,
            channelId = currentChannel.id,
            sequenceNumber = sequenceNumber,
            pcmData = pcmData
        )
        sendPacket(packet)
    }

    fun broadcastPttEnd() {
        isTransmittingLocally = false
        val packet = WalkiePacket.PttEnd(
            senderId = myDeviceId,
            channelId = currentChannel.id
        )
        sendPacket(packet)
    }

    private fun sendPacket(packet: WalkiePacket) {
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = WalkiePacket.serialize(packet)
                val socket = multicastSocket ?: return@launch

                // 1. Send to Multicast Group IP
                multicastInetAddress?.let { mcastAddr ->
                    try {
                        val mcastDatagram = DatagramPacket(bytes, bytes.size, mcastAddr, AudioConstants.DEFAULT_PORT)
                        socket.send(mcastDatagram)
                    } catch (e: Exception) {
                        Log.w(tag, "Multicast send error: ${e.message}")
                    }
                }

                // 2. Send to Broadcast IP (e.g. 192.168.43.255, 192.168.49.255 or 255.255.255.255)
                // Ensures transmission succeeds even on routers/hotspots blocking IGMP/Multicast
                broadcastInetAddress?.let { bcastAddr ->
                    try {
                        val bcastDatagram = DatagramPacket(bytes, bytes.size, bcastAddr, AudioConstants.DEFAULT_PORT)
                        socket.send(bcastDatagram)
                    } catch (e: Exception) {
                        Log.w(tag, "Broadcast send error: ${e.message}")
                    }
                }

                // 3. Fallback direct unicast to all known peer IP addresses (essential for tethering/AP mode)
                // In Wi-Fi Direct or Android Local-Only Hotspot, multicast between client & AP is often blocked by the OS kernel.
                // Unicasting directly to peer IPs guarantees two-way voice and message packet delivery.
                val peerIps = peerMap.values.mapNotNull {
                    if (it.ipAddress.isNotBlank() && it.ipAddress != "127.0.0.1" && it.ipAddress != _wifiState.value.ipAddress) {
                        it.ipAddress
                    } else null
                }.distinct()

                for (peerIp in peerIps) {
                    try {
                        val peerAddr = InetAddress.getByName(peerIp)
                        val unicastDatagram = DatagramPacket(bytes, bytes.size, peerAddr, AudioConstants.DEFAULT_PORT)
                        socket.send(unicastDatagram)
                    } catch (e: Exception) {
                        Log.d(tag, "Unicast to $peerIp skipped: ${e.message}")
                    }
                }

                // If this device is a client connected to a hotspot host (common default gateway: 192.168.43.1 / 192.168.49.1),
                // unicast heartbeat/packets to the gateway IP if not yet discovered
                val localIp = _wifiState.value.ipAddress
                if (localIp.startsWith("192.168.43.") && localIp != "192.168.43.1") {
                    try {
                        val hostAddr = InetAddress.getByName("192.168.43.1")
                        socket.send(DatagramPacket(bytes, bytes.size, hostAddr, AudioConstants.DEFAULT_PORT))
                    } catch (_: Exception) {}
                } else if (localIp.startsWith("192.168.49.") && localIp != "192.168.49.1") {
                    try {
                        val hostAddr = InetAddress.getByName("192.168.49.1")
                        socket.send(DatagramPacket(bytes, bytes.size, hostAddr, AudioConstants.DEFAULT_PORT))
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to send packet", e)
            }
        }
    }

    fun release() {
        heartbeatJob?.cancel()
        receiverJob?.cancel()
        activeSpeakerTimeoutJob?.cancel()

        try {
            multicastSocket?.close()
            multicastSocket = null
        } catch (e: Exception) {
            Log.e(tag, "Error closing socket", e)
        }

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error releasing locks", e)
        }
    }
}
