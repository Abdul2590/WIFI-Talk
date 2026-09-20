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
import java.io.File
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet

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

    // Mesh packet deduplication cache: prevents loops, feedback storms, and duplicate audio in multi-hop mesh
    private val seenMeshPacketKeys = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(512, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                return size > 600
            }
        }
    )

    private val _remoteTransmissionState = MutableStateFlow<TransmissionState>(TransmissionState.Idle)
    val remoteTransmissionState: StateFlow<TransmissionState> = _remoteTransmissionState.asStateFlow()

    var currentChannel: WalkieChannel = WalkieChannel.ALL_CHANNELS[0]
        private set

    var isTransmittingLocally: Boolean = false
    var isLoopbackTestEnabled: Boolean = false

    private var receiverJob: Job? = null
    private var heartbeatJob: Job? = null
    private var activeSpeakerTimeoutJob: Job? = null

    // Multi-interface tracking for Hotspot + Wi-Fi Mesh Chain
    private val allActiveInterfaces = CopyOnWriteArrayList<NetworkInterface>()
    private val allBroadcastAddresses = CopyOnWriteArraySet<InetAddress>()
    private val allLocalIps = CopyOnWriteArraySet<String>()
    private var multicastInetAddress: InetAddress? = null

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
                acquire(15 * 60 * 1000L) // 15 min safety timeout
            }

            _wifiState.value = _wifiState.value.copy(isMulticastLockAcquired = multicastLock?.isHeld == true)
        } catch (e: Exception) {
            Log.e(tag, "Failed to acquire multicast/wake locks", e)
        }
    }

    /**
     * Inspects ALL available network interfaces (Wi-Fi wlan0, Mobile Hotspot ap0/softap0, Wi-Fi Direct p2p0, USB rndis).
     * Collects all local IPs and broadcast destinations for multi-hop mesh routing.
     */
    fun refreshNetworkInfo() {
        try {
            allActiveInterfaces.clear()
            allBroadcastAddresses.clear()
            allLocalIps.clear()

            var primaryIp = "127.0.0.1"
            var primaryBcast = "255.255.255.255"
            var primaryInterfaceName = "None"

            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (intf.isLoopback || !intf.isUp) continue

                var hasIpv4 = false
                for (addr in intf.interfaceAddresses) {
                    val inetAddr = addr.address
                    if (inetAddr is Inet4Address && !inetAddr.isLoopbackAddress) {
                        hasIpv4 = true
                        val hostIp = inetAddr.hostAddress ?: continue
                        allLocalIps.add(hostIp)

                        addr.broadcast?.let { bcast ->
                            allBroadcastAddresses.add(bcast)
                        }

                        if (primaryIp == "127.0.0.1") {
                            primaryIp = hostIp
                            primaryBcast = addr.broadcast?.hostAddress ?: "255.255.255.255"
                            primaryInterfaceName = intf.name
                        }
                    }
                }

                if (hasIpv4) {
                    allActiveInterfaces.add(intf)
                }
            }

            // Always add universal and common hotspot/tethering broadcast targets
            listOf(
                "255.255.255.255",
                "192.168.43.255", // Standard Android Mobile Hotspot broadcast
                "192.168.49.255", // Standard Wi-Fi Direct broadcast
                "192.168.50.255",
                "192.168.1.255",
                "192.168.0.255",
                "10.0.0.255"
            ).forEach { ip ->
                try {
                    allBroadcastAddresses.add(InetAddress.getByName(ip))
                } catch (_: Exception) {}
            }

            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            _wifiState.value = WifiConnectionState(
                isConnected = isWifi || allActiveInterfaces.isNotEmpty(),
                ssid = if (isWifi) "Wi-Fi Connected"
                       else if (allActiveInterfaces.isNotEmpty()) "Mesh Relay (${allActiveInterfaces.joinToString { it.name }})"
                       else "No Wi-Fi",
                ipAddress = primaryIp,
                broadcastAddress = primaryBcast,
                isMulticastLockAcquired = multicastLock?.isHeld == true
            )
        } catch (e: Exception) {
            Log.e(tag, "Error inspecting network interfaces", e)
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
                timeToLive = 8 // Scope across multi-hop subnet boundaries
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

        _remoteTransmissionState.value = TransmissionState.Idle
        sendHeartbeat()
    }

    private fun joinMulticastGroup(channel: WalkieChannel) {
        val socket = multicastSocket ?: return
        try {
            val group = InetAddress.getByName(channel.multicastGroup)
            multicastInetAddress = group

            // Join the multicast group on EVERY active network interface (Wi-Fi + Hotspot + Tethering)
            for (intf in allActiveInterfaces) {
                try {
                    socket.joinGroup(InetSocketAddress(group, AudioConstants.DEFAULT_PORT), intf)
                    Log.d(tag, "Joined multicast group ${channel.multicastGroup} on interface ${intf.name}")
                } catch (e: Exception) {
                    Log.d(tag, "Interface ${intf.name} multicast join skipped: ${e.message}")
                }
            }

            // Fallback wildcard join
            try {
                @Suppress("DEPRECATION")
                socket.joinGroup(group)
            } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.e(tag, "Failed to join multicast group ${channel.multicastGroup}", e)
        }
    }

    private fun leaveMulticastGroup(channel: WalkieChannel) {
        val socket = multicastSocket ?: return
        try {
            val group = InetAddress.getByName(channel.multicastGroup)
            for (intf in allActiveInterfaces) {
                try {
                    socket.leaveGroup(InetSocketAddress(group, AudioConstants.DEFAULT_PORT), intf)
                } catch (_: Exception) {}
            }
            try {
                @Suppress("DEPRECATION")
                socket.leaveGroup(group)
            } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.e(tag, "Failed to leave multicast group ${channel.multicastGroup}", e)
        }
    }

    private fun getPacketUniqueKey(packet: WalkiePacket): String {
        return when (packet) {
            is WalkiePacket.AudioData -> "${packet.senderId}_audio_${packet.sequenceNumber}"
            is WalkiePacket.TextMessage -> packet.messageId
            is WalkiePacket.PttStart -> "${packet.senderId}_start_${packet.timestamp}"
            is WalkiePacket.PttEnd -> "${packet.senderId}_end_${packet.channelId}"
            is WalkiePacket.Heartbeat -> "${packet.senderId}_hb_${packet.timestamp / 2000}"
        }
    }

    private fun startReceiver() {
        receiverJob?.cancel()
        receiverJob = scope.launch(Dispatchers.IO) {
            val receiveBuffer = ByteArray(4096)

            while (isActive) {
                try {
                    val socket = multicastSocket ?: run {
                        delay(500)
                        return@launch
                    }

                    val datagram = DatagramPacket(receiveBuffer, receiveBuffer.size)
                    socket.receive(datagram)

                    val receivedBytes = datagram.data
                    val length = datagram.length
                    val senderIp = datagram.address.hostAddress ?: ""

                    val walkiePacket = WalkiePacket.deserialize(receivedBytes, length) ?: continue

                    // Ignore own packets unless loopback self-test is active
                    if (walkiePacket.senderId == myDeviceId && !isLoopbackTestEnabled) {
                        continue
                    }

                    // Ignore if this node was the immediate relayer
                    if (walkiePacket.relayNodeId == myDeviceId) {
                        continue
                    }

                    // Mesh deduplication: if already processed within last 8 seconds, drop to break loops
                    val packetKey = getPacketUniqueKey(walkiePacket)
                    val now = System.currentTimeMillis()
                    val lastSeen = seenMeshPacketKeys[packetKey]
                    if (lastSeen != null && (now - lastSeen) < 8000) {
                        continue
                    }
                    seenMeshPacketKeys[packetKey] = now

                    // Handle locally
                    handleIncomingPacket(walkiePacket, senderIp)

                    // Blockchain-style Multi-Hop Mesh Relay:
                    // If packet has TTL > 1, decrement TTL, increment hopCount, and re-broadcast to other interfaces and peers
                    if (walkiePacket.ttl > 1 && walkiePacket.senderId != myDeviceId) {
                        val forwardedPacket = when (walkiePacket) {
                            is WalkiePacket.PttStart -> walkiePacket.copy(
                                hopCount = walkiePacket.hopCount + 1,
                                ttl = walkiePacket.ttl - 1,
                                relayNodeId = myDeviceId
                            )
                            is WalkiePacket.AudioData -> walkiePacket.copy(
                                hopCount = walkiePacket.hopCount + 1,
                                ttl = walkiePacket.ttl - 1,
                                relayNodeId = myDeviceId
                            )
                            is WalkiePacket.PttEnd -> walkiePacket.copy(
                                hopCount = walkiePacket.hopCount + 1,
                                ttl = walkiePacket.ttl - 1,
                                relayNodeId = myDeviceId
                            )
                            is WalkiePacket.Heartbeat -> walkiePacket.copy(
                                hopCount = walkiePacket.hopCount + 1,
                                ttl = walkiePacket.ttl - 1,
                                relayNodeId = myDeviceId
                            )
                            is WalkiePacket.TextMessage -> walkiePacket.copy(
                                hopCount = walkiePacket.hopCount + 1,
                                ttl = walkiePacket.ttl - 1,
                                relayNodeId = myDeviceId
                            )
                        }

                        scope.launch(Dispatchers.IO) {
                            dispatchPacket(forwardedPacket, excludeIp = senderIp)
                        }
                    }
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
                        startedAtMs = packet.timestamp,
                        hopCount = packet.hopCount
                    )
                    resetSpeakerTimeout(packet.senderId)
                }
                updatePeer(
                    id = packet.senderId,
                    callSign = packet.callSign,
                    ip = senderIp,
                    channel = packet.channelId,
                    isTransmitting = true,
                    hopCount = packet.hopCount,
                    relayVia = if (packet.hopCount > 0) senderIp else ""
                )
            }

            is WalkiePacket.AudioData -> {
                if (packet.channelId == currentChannel.id) {
                    _incomingAudio.tryEmit(packet.pcmData)

                    if (_remoteTransmissionState.value !is TransmissionState.Receiving) {
                        val peer = peerMap[packet.senderId]
                        _remoteTransmissionState.value = TransmissionState.Receiving(
                            speakerId = packet.senderId,
                            speakerCallSign = peer?.callSign ?: "Radio ${packet.senderId.take(4)}",
                            channel = currentChannel,
                            hopCount = packet.hopCount
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
                    isMessagingEnabled = packet.isMessagingEnabled,
                    hopCount = packet.hopCount,
                    relayVia = if (packet.hopCount > 0) senderIp else ""
                )
            }

            is WalkiePacket.TextMessage -> {
                // Deliver if broadcast or addressed to me
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
                        recipientId = packet.recipientId,
                        hopCount = packet.hopCount
                    )
                    _incomingMessages.tryEmit(chatMessage)
                }

                // Update peer info
                updatePeer(
                    id = packet.senderId,
                    callSign = packet.senderCallSign,
                    ip = senderIp,
                    channel = packet.channelId,
                    isTransmitting = false,
                    userName = packet.senderName,
                    mobileNumber = packet.senderMobile,
                    isMessagingEnabled = true,
                    hopCount = packet.hopCount,
                    relayVia = if (packet.hopCount > 0) senderIp else ""
                )
            }
        }
    }

    private fun resetSpeakerTimeout(speakerId: String) {
        activeSpeakerTimeoutJob?.cancel()
        activeSpeakerTimeoutJob = scope.launch(Dispatchers.Default) {
            delay(850)
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
        isMessagingEnabled: Boolean = false,
        hopCount: Int = 0,
        relayVia: String = ""
    ) {
        if (id == myDeviceId) return
        val existing = peerMap[id]
        val now = System.currentTimeMillis()

        // Keep lowest hop count if seen recently
        val effectiveHopCount = if (existing != null && (now - existing.lastSeenMs < 8000)) {
            minOf(existing.hopCount, hopCount)
        } else {
            hopCount
        }

        val peer = PeerDevice(
            id = id,
            callSign = callSign,
            ipAddress = ip,
            channel = channel,
            isTransmitting = isTransmitting,
            lastSeenMs = now,
            userName = if (userName.isNotBlank()) userName else existing?.userName ?: "",
            mobileNumber = if (mobileNumber.isNotBlank()) mobileNumber else existing?.mobileNumber ?: "",
            isMessagingEnabled = isMessagingEnabled || (existing?.isMessagingEnabled == true),
            hopCount = effectiveHopCount,
            relayVia = if (effectiveHopCount > 0) {
                if (relayVia.isNotBlank()) relayVia else existing?.relayVia ?: ip
            } else ""
        )
        peerMap[id] = peer
        syncPeersList()
    }

    private fun syncPeersList() {
        val now = System.currentTimeMillis()
        // Prune peers not heard from within 12 seconds
        peerMap.entries.removeIf { now - it.value.lastSeenMs > 12000 }
        _peers.value = peerMap.values.toList().sortedWith(
            compareBy<PeerDevice> { it.hopCount }.thenBy { it.callSign }
        )
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
            isMessagingEnabled = isMessagingEnabled,
            timestamp = System.currentTimeMillis(),
            hopCount = 0,
            ttl = 6,
            relayNodeId = ""
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
            recipientId = message.recipientId,
            hopCount = 0,
            ttl = 6,
            relayNodeId = ""
        )
        sendPacket(packet)
    }

    fun broadcastPttStart() {
        isTransmittingLocally = true
        val packet = WalkiePacket.PttStart(
            senderId = myDeviceId,
            callSign = myCallSign,
            channelId = currentChannel.id,
            timestamp = System.currentTimeMillis(),
            hopCount = 0,
            ttl = 6,
            relayNodeId = ""
        )
        sendPacket(packet)
    }

    fun broadcastAudioChunk(pcmData: ByteArray, sequenceNumber: Int) {
        val packet = WalkiePacket.AudioData(
            senderId = myDeviceId,
            channelId = currentChannel.id,
            sequenceNumber = sequenceNumber,
            pcmData = pcmData,
            hopCount = 0,
            ttl = 6,
            relayNodeId = ""
        )
        sendPacket(packet)
    }

    fun broadcastPttEnd() {
        isTransmittingLocally = false
        val packet = WalkiePacket.PttEnd(
            senderId = myDeviceId,
            channelId = currentChannel.id,
            hopCount = 0,
            ttl = 6,
            relayNodeId = ""
        )
        sendPacket(packet)
    }

    private fun sendPacket(packet: WalkiePacket) {
        // Record own packet as seen so returning echo/relays are dropped
        val packetKey = getPacketUniqueKey(packet)
        seenMeshPacketKeys[packetKey] = System.currentTimeMillis()

        scope.launch(Dispatchers.IO) {
            dispatchPacket(packet, excludeIp = null)
        }
    }

    /**
     * Reads /proc/net/arp to discover connected clients on Android Mobile Hotspot (AP mode).
     */
    private fun getConnectedArpClients(): Set<String> {
        val clientIps = mutableSetOf<String>()
        try {
            val file = File("/proc/net/arp")
            if (file.exists() && file.canRead()) {
                file.bufferedReader().useLines { lines ->
                    lines.drop(1).forEach { line ->
                        val tokens = line.split("\\s+".toRegex())
                        if (tokens.isNotEmpty()) {
                            val ip = tokens[0]
                            if (ip.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$")) && ip != "0.0.0.0" && ip != "127.0.0.1") {
                                clientIps.add(ip)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Add standard gateway / host IPs for client nodes connected to a host hotspot
        if (allLocalIps.any { it.startsWith("192.168.43.") }) {
            clientIps.add("192.168.43.1")
        }
        if (allLocalIps.any { it.startsWith("192.168.49.") }) {
            clientIps.add("192.168.49.1")
        }
        return clientIps
    }

    /**
     * Dispatches packet across all interfaces (Wi-Fi + Mobile Hotspot + Tethering),
     * all subnet broadcasts, known peer unicasts, and hotspot clients.
     */
    private fun dispatchPacket(packet: WalkiePacket, excludeIp: String?) {
        try {
            val socket = multicastSocket ?: return
            val bytes = WalkiePacket.serialize(packet)

            // 1. Multicast across each active network interface
            multicastInetAddress?.let { mcastAddr ->
                for (intf in allActiveInterfaces) {
                    try {
                        socket.networkInterface = intf
                        val mcastDatagram = DatagramPacket(bytes, bytes.size, mcastAddr, AudioConstants.DEFAULT_PORT)
                        socket.send(mcastDatagram)
                    } catch (_: Exception) {}
                }
                try {
                    val mcastDatagram = DatagramPacket(bytes, bytes.size, mcastAddr, AudioConstants.DEFAULT_PORT)
                    socket.send(mcastDatagram)
                } catch (_: Exception) {}
            }

            // 2. Broadcast to all subnet broadcast addresses (wlan0, ap0, softap, 255.255.255.255)
            for (bcastAddr in allBroadcastAddresses) {
                if (excludeIp != null && bcastAddr.hostAddress == excludeIp) continue
                try {
                    val bcastDatagram = DatagramPacket(bytes, bytes.size, bcastAddr, AudioConstants.DEFAULT_PORT)
                    socket.send(bcastDatagram)
                } catch (_: Exception) {}
            }

            // 3. Direct unicast fallback to all discovered peers (guarantees penetration through AP isolation)
            val peerIps = peerMap.values.mapNotNull {
                if (it.ipAddress.isNotBlank() && it.ipAddress != "127.0.0.1" && it.ipAddress != excludeIp && !allLocalIps.contains(it.ipAddress)) {
                    it.ipAddress
                } else null
            }.distinct()

            for (peerIp in peerIps) {
                try {
                    val peerAddr = InetAddress.getByName(peerIp)
                    val unicastDatagram = DatagramPacket(bytes, bytes.size, peerAddr, AudioConstants.DEFAULT_PORT)
                    socket.send(unicastDatagram)
                } catch (_: Exception) {}
            }

            // 4. Direct unicast to ARP-detected connected clients (mobile hotspot clients)
            for (clientIp in getConnectedArpClients()) {
                if (clientIp == excludeIp || allLocalIps.contains(clientIp)) continue
                try {
                    val clientAddr = InetAddress.getByName(clientIp)
                    socket.send(DatagramPacket(bytes, bytes.size, clientAddr, AudioConstants.DEFAULT_PORT))
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to dispatch packet", e)
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
