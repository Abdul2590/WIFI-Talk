package com.example.network

import java.nio.ByteBuffer
import java.nio.ByteOrder

sealed class WalkiePacket {
    abstract val senderId: String
    abstract val channelId: Int

    data class PttStart(
        override val senderId: String,
        val callSign: String,
        override val channelId: Int,
        val timestamp: Long = System.currentTimeMillis()
    ) : WalkiePacket()

    data class AudioData(
        override val senderId: String,
        override val channelId: Int,
        val sequenceNumber: Int,
        val pcmData: ByteArray
    ) : WalkiePacket() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as AudioData
            if (senderId != other.senderId) return false
            if (channelId != other.channelId) return false
            if (sequenceNumber != other.sequenceNumber) return false
            if (!pcmData.contentEquals(other.pcmData)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = senderId.hashCode()
            result = 31 * result + channelId
            result = 31 * result + sequenceNumber
            result = 31 * result + pcmData.contentHashCode()
            return result
        }
    }

    data class PttEnd(
        override val senderId: String,
        override val channelId: Int
    ) : WalkiePacket()

    data class Heartbeat(
        override val senderId: String,
        val callSign: String,
        override val channelId: Int,
        val isTransmitting: Boolean,
        val userName: String = "",
        val mobileNumber: String = "",
        val isMessagingEnabled: Boolean = false
    ) : WalkiePacket()

    data class TextMessage(
        val messageId: String,
        override val senderId: String,
        val senderName: String,
        val senderMobile: String,
        val senderCallSign: String,
        override val channelId: Int,
        val text: String,
        val timestamp: Long = System.currentTimeMillis(),
        val recipientId: String? = null // null means broadcast to channel
    ) : WalkiePacket()

    companion object {
        private val MAGIC = byteArrayOf('W'.code.toByte(), 'T'.code.toByte(), 'K'.code.toByte(), '1'.code.toByte())
        const val TYPE_PTT_START: Byte = 0x01
        const val TYPE_AUDIO_DATA: Byte = 0x02
        const val TYPE_PTT_END: Byte = 0x03
        const val TYPE_HEARTBEAT: Byte = 0x04
        const val TYPE_TEXT_MESSAGE: Byte = 0x05
        const val TYPE_HEARTBEAT_PROFILE: Byte = 0x06

        fun serialize(packet: WalkiePacket): ByteArray {
            val senderBytes = packet.senderId.toByteArray(Charsets.UTF_8).take(32).toByteArray()

            return when (packet) {
                is PttStart -> {
                    val callSignBytes = packet.callSign.toByteArray(Charsets.UTF_8).take(32).toByteArray()
                    val totalSize = 4 + 1 + 1 + senderBytes.size + 1 + callSignBytes.size + 1 + 8
                    val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
                    buffer.put(MAGIC)
                    buffer.put(TYPE_PTT_START)
                    buffer.put(senderBytes.size.toByte())
                    buffer.put(senderBytes)
                    buffer.put(callSignBytes.size.toByte())
                    buffer.put(callSignBytes)
                    buffer.put(packet.channelId.toByte())
                    buffer.putLong(packet.timestamp)
                    buffer.array()
                }

                is AudioData -> {
                    val totalSize = 4 + 1 + 1 + senderBytes.size + 1 + 4 + 2 + packet.pcmData.size
                    val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
                    buffer.put(MAGIC)
                    buffer.put(TYPE_AUDIO_DATA)
                    buffer.put(senderBytes.size.toByte())
                    buffer.put(senderBytes)
                    buffer.put(packet.channelId.toByte())
                    buffer.putInt(packet.sequenceNumber)
                    buffer.putShort(packet.pcmData.size.toShort())
                    buffer.put(packet.pcmData)
                    buffer.array()
                }

                is PttEnd -> {
                    val totalSize = 4 + 1 + 1 + senderBytes.size + 1
                    val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
                    buffer.put(MAGIC)
                    buffer.put(TYPE_PTT_END)
                    buffer.put(senderBytes.size.toByte())
                    buffer.put(senderBytes)
                    buffer.put(packet.channelId.toByte())
                    buffer.array()
                }

                is Heartbeat -> {
                    val callSignBytes = packet.callSign.toByteArray(Charsets.UTF_8).take(32).toByteArray()
                    val userNameBytes = packet.userName.toByteArray(Charsets.UTF_8).take(48).toByteArray()
                    val mobileBytes = packet.mobileNumber.toByteArray(Charsets.UTF_8).take(24).toByteArray()

                    val totalSize = 4 + 1 + 1 + senderBytes.size + 1 + callSignBytes.size + 1 + 1 +
                            1 + userNameBytes.size + 1 + mobileBytes.size + 1
                    val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
                    buffer.put(MAGIC)
                    buffer.put(TYPE_HEARTBEAT_PROFILE)
                    buffer.put(senderBytes.size.toByte())
                    buffer.put(senderBytes)
                    buffer.put(callSignBytes.size.toByte())
                    buffer.put(callSignBytes)
                    buffer.put(packet.channelId.toByte())
                    buffer.put(if (packet.isTransmitting) 1.toByte() else 0.toByte())
                    buffer.put(userNameBytes.size.toByte())
                    buffer.put(userNameBytes)
                    buffer.put(mobileBytes.size.toByte())
                    buffer.put(mobileBytes)
                    buffer.put(if (packet.isMessagingEnabled) 1.toByte() else 0.toByte())
                    buffer.array()
                }

                is TextMessage -> {
                    val msgIdBytes = packet.messageId.toByteArray(Charsets.UTF_8).take(36).toByteArray()
                    val nameBytes = packet.senderName.toByteArray(Charsets.UTF_8).take(48).toByteArray()
                    val mobileBytes = packet.senderMobile.toByteArray(Charsets.UTF_8).take(24).toByteArray()
                    val callSignBytes = packet.senderCallSign.toByteArray(Charsets.UTF_8).take(32).toByteArray()
                    val textBytes = packet.text.toByteArray(Charsets.UTF_8).take(1000).toByteArray()
                    val recipientStr = packet.recipientId ?: ""
                    val recipBytes = recipientStr.toByteArray(Charsets.UTF_8).take(32).toByteArray()

                    val totalSize = 4 + 1 +
                            1 + msgIdBytes.size +
                            1 + senderBytes.size +
                            1 + nameBytes.size +
                            1 + mobileBytes.size +
                            1 + callSignBytes.size +
                            1 + // channelId
                            8 + // timestamp
                            1 + recipBytes.size +
                            2 + textBytes.size

                    val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
                    buffer.put(MAGIC)
                    buffer.put(TYPE_TEXT_MESSAGE)
                    buffer.put(msgIdBytes.size.toByte())
                    buffer.put(msgIdBytes)
                    buffer.put(senderBytes.size.toByte())
                    buffer.put(senderBytes)
                    buffer.put(nameBytes.size.toByte())
                    buffer.put(nameBytes)
                    buffer.put(mobileBytes.size.toByte())
                    buffer.put(mobileBytes)
                    buffer.put(callSignBytes.size.toByte())
                    buffer.put(callSignBytes)
                    buffer.put(packet.channelId.toByte())
                    buffer.putLong(packet.timestamp)
                    buffer.put(recipBytes.size.toByte())
                    buffer.put(recipBytes)
                    buffer.putShort(textBytes.size.toShort())
                    buffer.put(textBytes)
                    buffer.array()
                }
            }
        }

        fun deserialize(bytes: ByteArray, length: Int): WalkiePacket? {
            if (length < 6) return null
            val buffer = ByteBuffer.wrap(bytes, 0, length).order(ByteOrder.BIG_ENDIAN)

            // Check magic
            val m0 = buffer.get()
            val m1 = buffer.get()
            val m2 = buffer.get()
            val m3 = buffer.get()
            if (m0 != MAGIC[0] || m1 != MAGIC[1] || m2 != MAGIC[2] || m3 != MAGIC[3]) {
                return null
            }

            return try {
                when (buffer.get()) {
                    TYPE_PTT_START -> {
                        val senderLen = buffer.get().toInt() and 0xFF
                        if (buffer.remaining() < senderLen) return null
                        val senderBytes = ByteArray(senderLen)
                        buffer.get(senderBytes)
                        val senderId = String(senderBytes, Charsets.UTF_8)

                        val callSignLen = buffer.get().toInt() and 0xFF
                        if (buffer.remaining() < callSignLen) return null
                        val callSignBytes = ByteArray(callSignLen)
                        buffer.get(callSignBytes)
                        val callSign = String(callSignBytes, Charsets.UTF_8)

                        val channelId = buffer.get().toInt() and 0xFF
                        val timestamp = buffer.getLong()
                        PttStart(senderId, callSign, channelId, timestamp)
                    }

                    TYPE_AUDIO_DATA -> {
                        val senderLen = buffer.get().toInt() and 0xFF
                        if (buffer.remaining() < senderLen) return null
                        val senderBytes = ByteArray(senderLen)
                        buffer.get(senderBytes)
                        val senderId = String(senderBytes, Charsets.UTF_8)

                        val channelId = buffer.get().toInt() and 0xFF
                        val seq = buffer.getInt()
                        val pcmLen = buffer.getShort().toInt() and 0xFFFF
                        if (buffer.remaining() < pcmLen) return null
                        val pcmData = ByteArray(pcmLen)
                        buffer.get(pcmData)

                        AudioData(senderId, channelId, seq, pcmData)
                    }

                    TYPE_PTT_END -> {
                        val senderLen = buffer.get().toInt() and 0xFF
                        if (buffer.remaining() < senderLen) return null
                        val senderBytes = ByteArray(senderLen)
                        buffer.get(senderBytes)
                        val senderId = String(senderBytes, Charsets.UTF_8)

                        val channelId = buffer.get().toInt() and 0xFF
                        PttEnd(senderId, channelId)
                    }

                    TYPE_HEARTBEAT -> {
                        val senderLen = buffer.get().toInt() and 0xFF
                        if (buffer.remaining() < senderLen) return null
                        val senderBytes = ByteArray(senderLen)
                        buffer.get(senderBytes)
                        val senderId = String(senderBytes, Charsets.UTF_8)

                        val callSignLen = buffer.get().toInt() and 0xFF
                        if (buffer.remaining() < callSignLen) return null
                        val callSignBytes = ByteArray(callSignLen)
                        buffer.get(callSignBytes)
                        val callSign = String(callSignBytes, Charsets.UTF_8)

                        val channelId = buffer.get().toInt() and 0xFF
                        val isTransmitting = buffer.get().toInt() == 1
                        Heartbeat(senderId, callSign, channelId, isTransmitting)
                    }

                    TYPE_HEARTBEAT_PROFILE -> {
                        val senderLen = buffer.get().toInt() and 0xFF
                        if (buffer.remaining() < senderLen) return null
                        val senderBytes = ByteArray(senderLen)
                        buffer.get(senderBytes)
                        val senderId = String(senderBytes, Charsets.UTF_8)

                        val callSignLen = buffer.get().toInt() and 0xFF
                        if (buffer.remaining() < callSignLen) return null
                        val callSignBytes = ByteArray(callSignLen)
                        buffer.get(callSignBytes)
                        val callSign = String(callSignBytes, Charsets.UTF_8)

                        val channelId = buffer.get().toInt() and 0xFF
                        val isTransmitting = buffer.get().toInt() == 1

                        val userLen = buffer.get().toInt() and 0xFF
                        if (buffer.remaining() < userLen) return null
                        val userBytes = ByteArray(userLen)
                        buffer.get(userBytes)
                        val userName = String(userBytes, Charsets.UTF_8)

                        val mobLen = buffer.get().toInt() and 0xFF
                        if (buffer.remaining() < mobLen) return null
                        val mobBytes = ByteArray(mobLen)
                        buffer.get(mobBytes)
                        val mobileNumber = String(mobBytes, Charsets.UTF_8)

                        val isMessagingEnabled = buffer.get().toInt() == 1

                        Heartbeat(
                            senderId = senderId,
                            callSign = callSign,
                            channelId = channelId,
                            isTransmitting = isTransmitting,
                            userName = userName,
                            mobileNumber = mobileNumber,
                            isMessagingEnabled = isMessagingEnabled
                        )
                    }

                    TYPE_TEXT_MESSAGE -> {
                        val msgIdLen = buffer.get().toInt() and 0xFF
                        if (buffer.remaining() < msgIdLen) return null
                        val msgIdBytes = ByteArray(msgIdLen)
                        buffer.get(msgIdBytes)
                        val messageId = String(msgIdBytes, Charsets.UTF_8)

                        val senderLen = buffer.get().toInt() and 0xFF
                        if (buffer.remaining() < senderLen) return null
                        val senderBytes = ByteArray(senderLen)
                        buffer.get(senderBytes)
                        val senderId = String(senderBytes, Charsets.UTF_8)

                        val nameLen = buffer.get().toInt() and 0xFF
                        if (buffer.remaining() < nameLen) return null
                        val nameBytes = ByteArray(nameLen)
                        buffer.get(nameBytes)
                        val senderName = String(nameBytes, Charsets.UTF_8)

                        val mobLen = buffer.get().toInt() and 0xFF
                        if (buffer.remaining() < mobLen) return null
                        val mobBytes = ByteArray(mobLen)
                        buffer.get(mobBytes)
                        val senderMobile = String(mobBytes, Charsets.UTF_8)

                        val csLen = buffer.get().toInt() and 0xFF
                        if (buffer.remaining() < csLen) return null
                        val csBytes = ByteArray(csLen)
                        buffer.get(csBytes)
                        val senderCallSign = String(csBytes, Charsets.UTF_8)

                        val channelId = buffer.get().toInt() and 0xFF
                        val timestamp = buffer.getLong()

                        val recipLen = buffer.get().toInt() and 0xFF
                        val recipientId = if (recipLen > 0) {
                            if (buffer.remaining() < recipLen) return null
                            val recipBytes = ByteArray(recipLen)
                            buffer.get(recipBytes)
                            String(recipBytes, Charsets.UTF_8)
                        } else {
                            null
                        }

                        val textLen = buffer.getShort().toInt() and 0xFFFF
                        if (buffer.remaining() < textLen) return null
                        val textBytes = ByteArray(textLen)
                        buffer.get(textBytes)
                        val text = String(textBytes, Charsets.UTF_8)

                        TextMessage(
                            messageId = messageId,
                            senderId = senderId,
                            senderName = senderName,
                            senderMobile = senderMobile,
                            senderCallSign = senderCallSign,
                            channelId = channelId,
                            text = text,
                            timestamp = timestamp,
                            recipientId = recipientId
                        )
                    }

                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
