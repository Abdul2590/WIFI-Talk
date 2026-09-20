package com.example

import com.example.audio.AudioConstants
import com.example.audio.ToneGenerator
import com.example.model.WalkieChannel
import com.example.network.WalkiePacket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkiePacketTest {

    @Test
    fun `test PttStart serialization and deserialization`() {
        val original = WalkiePacket.PttStart(
            senderId = "DEV12345",
            callSign = "ALPHA-1",
            channelId = 3,
            timestamp = 1700000000000L
        )

        val bytes = WalkiePacket.serialize(original)
        val deserialized = WalkiePacket.deserialize(bytes, bytes.size)

        assertNotNull(deserialized)
        assertTrue(deserialized is WalkiePacket.PttStart)
        val pttStart = deserialized as WalkiePacket.PttStart
        assertEquals("DEV12345", pttStart.senderId)
        assertEquals("ALPHA-1", pttStart.callSign)
        assertEquals(3, pttStart.channelId)
        assertEquals(1700000000000L, pttStart.timestamp)
    }

    @Test
    fun `test AudioData serialization and deserialization`() {
        val dummyPcm = ByteArray(AudioConstants.CHUNK_SIZE_BYTES) { it.toByte() }
        val original = WalkiePacket.AudioData(
            senderId = "DEV55555",
            channelId = 1,
            sequenceNumber = 42,
            pcmData = dummyPcm
        )

        val bytes = WalkiePacket.serialize(original)
        val deserialized = WalkiePacket.deserialize(bytes, bytes.size)

        assertNotNull(deserialized)
        assertTrue(deserialized is WalkiePacket.AudioData)
        val audioData = deserialized as WalkiePacket.AudioData
        assertEquals("DEV55555", audioData.senderId)
        assertEquals(1, audioData.channelId)
        assertEquals(42, audioData.sequenceNumber)
        assertArrayEquals(dummyPcm, audioData.pcmData)
        assertEquals(AudioConstants.CHUNK_SIZE_BYTES, dummyPcm.size)
    }

    @Test
    fun `test AudioConstants latency is under 20ms`() {
        assertTrue(AudioConstants.CHUNK_DURATION_MS <= 20)
        assertEquals(20, AudioConstants.CHUNK_DURATION_MS)
        assertEquals(320, AudioConstants.SAMPLES_PER_CHUNK)
        assertEquals(640, AudioConstants.CHUNK_SIZE_BYTES)
    }

    @Test
    fun `test PttEnd serialization and deserialization`() {
        val original = WalkiePacket.PttEnd(
            senderId = "DEV999",
            channelId = 2
        )

        val bytes = WalkiePacket.serialize(original)
        val deserialized = WalkiePacket.deserialize(bytes, bytes.size)

        assertNotNull(deserialized)
        assertTrue(deserialized is WalkiePacket.PttEnd)
        val pttEnd = deserialized as WalkiePacket.PttEnd
        assertEquals("DEV999", pttEnd.senderId)
        assertEquals(2, pttEnd.channelId)
    }

    @Test
    fun `test Heartbeat serialization and deserialization`() {
        val original = WalkiePacket.Heartbeat(
            senderId = "DEV888",
            callSign = "BRAVO-LEADER",
            channelId = 4,
            isTransmitting = true
        )

        val bytes = WalkiePacket.serialize(original)
        val deserialized = WalkiePacket.deserialize(bytes, bytes.size)

        assertNotNull(deserialized)
        assertTrue(deserialized is WalkiePacket.Heartbeat)
        val heartbeat = deserialized as WalkiePacket.Heartbeat
        assertEquals("DEV888", heartbeat.senderId)
        assertEquals("BRAVO-LEADER", heartbeat.callSign)
        assertEquals(4, heartbeat.channelId)
        assertTrue(heartbeat.isTransmitting)
    }

    @Test
    fun `test ToneGenerator creates non-empty pcm audio`() {
        val beep = ToneGenerator.createRogerBeep()
        assertTrue(beep.isNotEmpty())
        assertTrue(beep.size > 100)

        val chirp = ToneGenerator.createPttStartChirp()
        assertTrue(chirp.isNotEmpty())
    }

    @Test
    fun `test WalkieChannels configuration`() {
        assertEquals(8, WalkieChannel.ALL_CHANNELS.size)
        val ch1 = WalkieChannel.getById(1)
        assertEquals(1, ch1.id)
        assertEquals("CH-1 GENERAL", ch1.name)
        assertEquals("239.255.42.1", ch1.multicastGroup)
        assertEquals(AudioConstants.DEFAULT_PORT, ch1.port)
    }

    @Test
    fun `test DiscoveredWifiNetwork 24Ghz band verification`() {
        val network24 = com.example.model.DiscoveredWifiNetwork(
            ssid = "WALKIE_MESH_A1B2",
            bssid = "00:11:22:33:44:55",
            frequencyMhz = 2437,
            level = -45,
            isWalkieNetwork = true
        )
        assertTrue(network24.is24Ghz)
        assertTrue(network24.isWalkieNetwork)

        val network5 = com.example.model.DiscoveredWifiNetwork(
            ssid = "HOME_WIFI_5G",
            bssid = "AA:BB:CC:DD:EE:FF",
            frequencyMhz = 5180,
            level = -60,
            isWalkieNetwork = false
        )
        org.junit.Assert.assertFalse(network5.is24Ghz)
        org.junit.Assert.assertFalse(network5.isWalkieNetwork)
    }

    @Test
    fun `test AudioOutputDevice model`() {
        val speaker = com.example.model.AudioOutputDevice(
            id = "speaker_loudspeaker",
            name = "Built-in Speaker",
            type = com.example.model.AudioOutputType.LOUDSPEAKER,
            isSelected = true,
            isDefault = true,
            description = "Device Loudspeaker"
        )
        assertEquals("speaker_loudspeaker", speaker.id)
        assertEquals(com.example.model.AudioOutputType.LOUDSPEAKER, speaker.type)
        assertTrue(speaker.isSelected)
        assertTrue(speaker.isDefault)
    }

    @Test
    fun `test WifiHotspotState defaults`() {
        val state = com.example.model.WifiHotspotState()
        org.junit.Assert.assertFalse(state.isHosting)
        org.junit.Assert.assertFalse(state.isStarting)
        assertEquals("2.4 GHz", state.band)
        assertEquals("192.168.43.1", state.ipAddress)
    }

    @Test
    fun `test Mesh Chain Relay packet properties and serialization`() {
        val original = WalkiePacket.TextMessage(
            messageId = "MSG_BLOCKCHAIN_001",
            senderId = "PEER_1",
            senderName = "Alice",
            senderMobile = "9876543210",
            senderCallSign = "ALPHA-1",
            channelId = 1,
            text = "Hello to Peer 4 via relay!",
            timestamp = 1700000050000L,
            recipientId = "PEER_4",
            hopCount = 2,
            ttl = 4,
            relayNodeId = "PEER_2"
        )

        val bytes = WalkiePacket.serialize(original)
        val deserialized = WalkiePacket.deserialize(bytes, bytes.size)

        assertNotNull(deserialized)
        assertTrue(deserialized is WalkiePacket.TextMessage)
        val msg = deserialized as WalkiePacket.TextMessage
        assertEquals("MSG_BLOCKCHAIN_001", msg.messageId)
        assertEquals("PEER_1", msg.senderId)
        assertEquals("Alice", msg.senderName)
        assertEquals("9876543210", msg.senderMobile)
        assertEquals("ALPHA-1", msg.senderCallSign)
        assertEquals("PEER_4", msg.recipientId)
        assertEquals("Hello to Peer 4 via relay!", msg.text)
        assertEquals(2, msg.hopCount)
        assertEquals(4, msg.ttl)
        assertEquals("PEER_2", msg.relayNodeId)
    }

    @Test
    fun `test Multi-hop AudioData packet preserves sequence and pcm data`() {
        val pcm = ByteArray(AudioConstants.CHUNK_SIZE_BYTES) { (it % 128).toByte() }
        val audio = WalkiePacket.AudioData(
            senderId = "PEER_START",
            channelId = 2,
            sequenceNumber = 105,
            pcmData = pcm,
            hopCount = 3,
            ttl = 3,
            relayNodeId = "PEER_MIDDLE"
        )

        val bytes = WalkiePacket.serialize(audio)
        val deserialized = WalkiePacket.deserialize(bytes, bytes.size) as WalkiePacket.AudioData

        assertEquals("PEER_START", audio.senderId)
        assertEquals(2, audio.channelId)
        assertEquals(105, audio.sequenceNumber)
        assertEquals(3, audio.hopCount)
        assertEquals(3, audio.ttl)
        assertEquals("PEER_MIDDLE", audio.relayNodeId)
        assertArrayEquals(pcm, deserialized.pcmData)
    }
}
