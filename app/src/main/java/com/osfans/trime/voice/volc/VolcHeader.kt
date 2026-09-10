/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.volc

/**
 * 火山引擎流式语音识别（sauc bigmodel）的二进制帧头。
 *
 * 一帧的结构是：4 字节帧头 +（可选）4 字节 sequence + 4 字节 payload 长度 + payload。
 * 帧头四个字节里塞了六个 4 bit 的字段：
 *
 * ```
 * byte0: version(4) | headerSize(4)      headerSize 的单位是 4 字节
 * byte1: messageType(4) | flags(4)
 * byte2: serialization(4) | compression(4)
 * byte3: reserved
 * ```
 *
 * 语义对齐 type4me 的 `Type4Me/Protocol/VolcHeader.swift`。
 */
data class VolcHeader(
    val messageType: VolcMessageType,
    val flags: VolcMessageFlags,
    val serialization: VolcSerialization,
    val compression: VolcCompression,
    val version: Int = 0b0001,
    val headerSize: Int = 0b0001,
    val reserved: Int = 0x00,
) {
    fun encode(): ByteArray = byteArrayOf(
        ((version shl 4) or (headerSize and 0x0F)).toByte(),
        ((messageType.raw shl 4) or (flags.raw and 0x0F)).toByte(),
        ((serialization.raw shl 4) or (compression.raw and 0x0F)).toByte(),
        reserved.toByte(),
    )

    companion object {
        const val SIZE_BYTES = 4

        fun decode(data: ByteArray, offset: Int = 0): VolcHeader {
            if (data.size - offset < SIZE_BYTES) throw VolcProtocolException.HeaderTooShort()
            val byte0 = data[offset].toInt() and 0xFF
            val byte1 = data[offset + 1].toInt() and 0xFF
            val byte2 = data[offset + 2].toInt() and 0xFF
            val byte3 = data[offset + 3].toInt() and 0xFF

            val messageTypeRaw = (byte1 shr 4) and 0x0F
            val flagsRaw = byte1 and 0x0F
            val serializationRaw = (byte2 shr 4) and 0x0F
            val compressionRaw = byte2 and 0x0F

            return VolcHeader(
                messageType = VolcMessageType.of(messageTypeRaw)
                    ?: throw VolcProtocolException.UnknownMessageType(messageTypeRaw),
                flags = VolcMessageFlags.of(flagsRaw)
                    ?: throw VolcProtocolException.UnknownFlags(flagsRaw),
                serialization = VolcSerialization.of(serializationRaw)
                    ?: throw VolcProtocolException.UnknownSerialization(serializationRaw),
                compression = VolcCompression.of(compressionRaw)
                    ?: throw VolcProtocolException.UnknownCompression(compressionRaw),
                version = (byte0 shr 4) and 0x0F,
                headerSize = byte0 and 0x0F,
                reserved = byte3,
            )
        }

        /** 只看第二个字节的高 4 位，用来在完整解码前先认出 server error 帧。 */
        fun peekMessageType(data: ByteArray): Int = if (data.size > 1) (data[1].toInt() shr 4) and 0x0F else 0
    }
}

enum class VolcMessageType(
    val raw: Int,
) {
    FULL_CLIENT_REQUEST(0b0001),
    AUDIO_ONLY_REQUEST(0b0010),
    SERVER_RESPONSE(0b1001),
    SERVER_ERROR(0b1111),
    ;

    companion object {
        fun of(raw: Int) = entries.firstOrNull { it.raw == raw }
    }
}

enum class VolcMessageFlags(
    val raw: Int,
) {
    NO_SEQUENCE(0b0000),
    POSITIVE_SEQUENCE(0b0001),
    LAST_PACKET_NO_SEQUENCE(0b0010),
    NEGATIVE_SEQUENCE_LAST(0b0011),

    /** bigmodel_async 端点用它标记「这一帧是最终结果」。 */
    ASYNC_FINAL(0b0100),
    ;

    /** 帧头后面是否跟着 4 字节 sequence。 */
    val hasSequence: Boolean
        get() = this == POSITIVE_SEQUENCE || this == NEGATIVE_SEQUENCE_LAST

    companion object {
        fun of(raw: Int) = entries.firstOrNull { it.raw == raw }
    }
}

enum class VolcSerialization(
    val raw: Int,
) {
    NONE(0b0000),
    JSON(0b0001),
    ;

    companion object {
        fun of(raw: Int) = entries.firstOrNull { it.raw == raw }
    }
}

enum class VolcCompression(
    val raw: Int,
) {
    NONE(0b0000),
    GZIP(0b0001),
    ;

    companion object {
        fun of(raw: Int) = entries.firstOrNull { it.raw == raw }
    }
}

sealed class VolcProtocolException(
    message: String,
) : Exception(message) {
    class HeaderTooShort : VolcProtocolException("帧头不足 4 字节")

    class UnknownMessageType(
        val raw: Int,
    ) : VolcProtocolException("未知的 message type: $raw")

    class UnknownFlags(
        val raw: Int,
    ) : VolcProtocolException("未知的 flags: $raw")

    class UnknownSerialization(
        val raw: Int,
    ) : VolcProtocolException("未知的 serialization: $raw")

    class UnknownCompression(
        val raw: Int,
    ) : VolcProtocolException("未知的 compression: $raw")

    class InvalidPayload : VolcProtocolException("payload 结构不合法")

    class DecompressionFailed : VolcProtocolException("解压失败")

    /**
     * 服务端自己报的错（额度、鉴权、限流……）。重试同一个账号不可能好，
     * 所以必须把服务端原话透出去，别被包成通用的网络错误。
     */
    class ServerError(
        val code: Int?,
        val serverMessage: String?,
    ) : VolcProtocolException(
        listOfNotNull(serverMessage ?: "语音识别服务返回错误", code?.let { "($it)" })
            .joinToString(" "),
    )
}
