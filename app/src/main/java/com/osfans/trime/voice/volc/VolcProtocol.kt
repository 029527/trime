/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.volc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.Inflater

/** 一段识别文本；`definite` 表示服务端已经确认、不会再改。 */
data class VolcUtterance(
    val text: String,
    val definite: Boolean,
)

data class VolcASRResult(
    val text: String,
    val utterances: List<VolcUtterance>,
)

data class VolcServerResponse(
    val header: VolcHeader,
    val result: VolcASRResult,
)

/** 发起识别请求时可以带的参数。热词这期只存不用，先把字段留着。 */
data class VolcRequestOptions(
    val enablePunc: Boolean = true,
    val hotwords: List<String> = emptyList(),
    val boostingTableId: String? = null,
    val contextHistoryLength: Int = 0,
)

/**
 * 火山流式识别的编解码。纯函数，没有 Android 依赖，可以直接在 JVM 单元测试里跑。
 *
 * 语义对齐 type4me 的 `Type4Me/Protocol/VolcProtocol.swift`，
 * 单元测试 `VolcProtocolTest` / `VolcServerErrorTest` 是照它的 XCTest 用例对拍的。
 */
object VolcProtocol {

    const val ENDPOINT = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // MARK: - 鉴权 header

    /**
     * WebSocket 握手要带的 header。新版控制台用 `X-Api-Key`，
     * 旧版控制台是 App ID + Access Token 一对。
     */
    fun authHeaders(
        authentication: VolcAuthentication,
        resourceId: String,
        connectId: String,
    ): Map<String, String> {
        val headers = mutableMapOf(
            "X-Api-Resource-Id" to resourceId,
            "X-Api-Connect-Id" to connectId,
        )
        when (authentication) {
            is VolcAuthentication.ApiKey -> headers["X-Api-Key"] = authentication.apiKey
            is VolcAuthentication.Legacy -> {
                headers["X-Api-App-Key"] = authentication.appKey
                headers["X-Api-Access-Key"] = authentication.accessKey
            }
        }
        return headers
    }

    // MARK: - full client request

    fun buildClientRequest(
        uid: String,
        format: String = "pcm",
        codec: String = "raw",
        rate: Int = 16000,
        bits: Int = 16,
        channel: Int = 1,
        showUtterances: Boolean = true,
        resultType: String = "full",
        options: VolcRequestOptions = VolcRequestOptions(),
    ): ByteArray {
        val payload = buildJsonObject {
            putJsonObject("user") { put("uid", uid) }
            putJsonObject("audio") {
                put("format", format)
                put("codec", codec)
                put("rate", rate)
                put("bits", bits)
                put("channel", channel)
            }
            putJsonObject("request") {
                put("model_name", "bigmodel")
                put("enable_punc", options.enablePunc)
                put("enable_ddc", true)
                put("enable_nonstream", true)
                put("show_utterances", showUtterances)
                put("result_type", resultType)
                put("end_window_size", 3000)
                put("force_to_speech_time", 0)

                val boostingTableId = options.boostingTableId?.trim()?.takeIf { it.isNotEmpty() }
                if (boostingTableId != null) {
                    // 云端热词表优先：给了表 ID 就不再内联热词
                    putJsonObject("corpus") { put("boosting_table_id", boostingTableId) }
                } else {
                    buildContextString(options.hotwords)?.let { put("context", it) }
                }
                if (options.contextHistoryLength > 0) {
                    put("context_history_length", options.contextHistoryLength)
                }
            }
        }
        return json.encodeToString(JsonObject.serializer(), payload).toByteArray(StandardCharsets.UTF_8)
    }

    private fun buildContextString(hotwords: List<String>): String? {
        val cleaned = hotwords.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return null
        val context = buildJsonObject {
            put(
                "hotwords",
                buildJsonArray {
                    cleaned.forEach { word ->
                        add(
                            buildJsonObject {
                                put("word", word)
                                put("scale", 5.0)
                            },
                        )
                    }
                },
            )
        }
        return json.encodeToString(JsonObject.serializer(), context)
    }

    // MARK: - 整帧编码

    fun encodeMessage(
        header: VolcHeader,
        payload: ByteArray,
        sequenceNumber: Int? = null,
    ): ByteArray {
        val size = VolcHeader.SIZE_BYTES + (if (sequenceNumber != null) 4 else 0) + 4 + payload.size
        val buffer = ByteBuffer.allocate(size)
        buffer.put(header.encode())
        if (sequenceNumber != null) buffer.putInt(sequenceNumber)
        buffer.putInt(payload.size)
        buffer.put(payload)
        return buffer.array()
    }

    fun encodeAudioPacket(
        audioData: ByteArray,
        isLast: Boolean,
    ): ByteArray = encodeMessage(
        header = VolcHeader(
            messageType = VolcMessageType.AUDIO_ONLY_REQUEST,
            flags = if (isLast) VolcMessageFlags.LAST_PACKET_NO_SEQUENCE else VolcMessageFlags.NO_SEQUENCE,
            serialization = VolcSerialization.NONE,
            compression = VolcCompression.NONE,
        ),
        payload = audioData,
    )

    // MARK: - server error

    /**
     * 尽最大努力从 server error 帧里抠出 code 和人话。
     *
     * 文档说的排布是「帧头 + 4 字节错误码 + 4 字节长度 + 正文」，但这个 client 从来
     * 没抓到过真实的错误帧。所以这里不赌某一种偏移：几种可能的分帧都试一遍，
     * 能读出什么算什么。把服务端原话透出去，比精确解析重要得多；
     * 因为解析不动就抛 `InvalidPayload`、把原话吞掉，才是真正的缺陷。
     */
    fun extractServerError(data: ByteArray): Pair<Int?, String?> {
        val headerBytes = (runCatching { VolcHeader.decode(data).headerSize }.getOrNull() ?: 1) * 4
        if (data.size <= headerBytes) return null to null
        val tail = data.copyOfRange(headerBytes, data.size)

        var code: Int? = null
        if (tail.size >= 4) {
            val value = ByteBuffer.wrap(tail, 0, 4).int.toLong() and 0xFFFFFFFFL
            // 火山的错误码是 8 位数。别的值多半是长度前缀或者正文本身，
            // 与其报一个假的错误码，不如丢掉。
            if (value in 10_000_000L..99_999_999L) code = value.toInt()
        }

        // 候选正文：原样的 tail，以及剥掉 code / size 前缀之后的，每种再试一次解压。
        val candidates = mutableListOf<ByteArray>()
        for (skip in intArrayOf(0, 4, 8)) {
            if (tail.size <= skip) continue
            val slice = tail.copyOfRange(skip, tail.size)
            candidates += slice
            runCatching { gzipDecompress(slice) }.getOrNull()?.let { candidates += it }
        }

        for (candidate in candidates) {
            val found = readableError(candidate)
            if (found != null) {
                // 正文里自带的 code 是确定的；开头那个字是猜的分帧，只在正文没有时补位。
                return (found.first ?: code) to found.second
            }
        }
        return code to null
    }

    private val errorKeys = listOf("error", "message", "msg", "error_msg")

    private fun fromJson(text: String): Pair<Int?, String?>? {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val code = runCatching { obj["code"]?.jsonPrimitive?.intOrNull }.getOrNull()
        for (key in errorKeys) {
            val value = runCatching { obj[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
            if (!value.isNullOrEmpty()) return code to value
        }
        return code?.let { it to null }
    }

    private fun readableError(data: ByteArray): Pair<Int?, String?>? {
        val text = decodeUtf8OrNull(data) ?: return null

        fromJson(text)?.let { return it }

        // 猜错分帧的时候，正文可能被裹在更大的一段里
        val open = text.indexOf('{')
        val close = text.lastIndexOf('}')
        if (open >= 0 && close > open) {
            fromJson(text.substring(open, close + 1))?.let { return it }
        }

        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        // 大部分是控制字符的片段是分帧，不是消息 —— 它能解码成 UTF-8 纯属巧合。
        val printable = trimmed.count { it.code >= 0x20 }
        if (printable * 4 < trimmed.length * 3) return null
        return null to trimmed.take(200)
    }

    /** 严格解码：不合法的 UTF-8 直接判定不可读，而不是替换成 U+FFFD。 */
    private fun decodeUtf8OrNull(data: ByteArray): String? = runCatching {
        val decoder = StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val out: CharBuffer = decoder.decode(ByteBuffer.wrap(data))
        out.toString()
    }.getOrNull()

    /**
     * 握手被 HTTP 拒掉时，从响应体里捞服务端的原话。
     *
     * 鉴权失败 / 额度用完这类问题只在这个 body 里说得清楚，
     * 只报一句"连接失败"等于把唯一有用的信息扔了。
     */
    fun readableHttpError(body: String): String? {
        if (body.isBlank()) return null
        fromJson(body)?.let { (code, message) ->
            if (message != null) return code?.let { "$message ($it)" } ?: message
            if (code != null) return "错误码 $code"
        }
        return body.trim().take(200).ifEmpty { null }
    }

    // MARK: - server response

    fun decodeServerResponse(data: ByteArray): VolcServerResponse {
        val header = VolcHeader.decode(data)

        // 放在任何长度解析之前：错误帧的分帧恰恰是我们没法确认的部分，
        // 严格解析只会把服务端原话换成 InvalidPayload。
        if (header.messageType == VolcMessageType.SERVER_ERROR) {
            val (code, message) = extractServerError(data)
            throw VolcProtocolException.ServerError(code, message)
        }

        var offset = header.headerSize * 4
        if (header.flags.hasSequence) offset += 4

        if (data.size < offset + 4) throw VolcProtocolException.InvalidPayload()
        val payloadSize = ByteBuffer.wrap(data, offset, 4).int
        offset += 4

        if (payloadSize < 0 || data.size < offset + payloadSize) throw VolcProtocolException.InvalidPayload()
        var payload = data.copyOfRange(offset, offset + payloadSize)

        if (header.compression == VolcCompression.GZIP) payload = gzipDecompress(payload)
        if (header.serialization != VolcSerialization.JSON) throw VolcProtocolException.InvalidPayload()

        val text = decodeUtf8OrNull(payload) ?: throw VolcProtocolException.InvalidPayload()
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw VolcProtocolException.InvalidPayload()

        val resultObj = runCatching { root["result"]?.jsonObject }.getOrNull()
        val resultText = runCatching { resultObj?.get("text")?.jsonPrimitive?.contentOrNull }.getOrNull()
            ?: runCatching { root["text"]?.jsonPrimitive?.contentOrNull }.getOrNull()
            ?: ""

        val utteranceArray = runCatching { resultObj?.get("utterances")?.jsonArray }.getOrNull()
            ?: runCatching { root["utterances"]?.jsonArray }.getOrNull()
        val utterances = utteranceArray?.mapNotNull { element ->
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            VolcUtterance(
                text = runCatching { obj["text"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: "",
                definite = runCatching { obj["definite"]?.jsonPrimitive?.booleanOrNull }.getOrNull() ?: false,
            )
        } ?: emptyList()

        return VolcServerResponse(header, VolcASRResult(resultText, utterances))
    }

    fun decodeServerMessage(data: ByteArray): VolcASRResult = decodeServerResponse(data).result

    // MARK: - gzip

    fun gzipCompress(data: ByteArray): ByteArray {
        if (data.isEmpty()) return ByteArray(0)
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    /**
     * 先按 gzip（RFC 1952）解，不行再按 zlib（RFC 1950）解。
     *
     * 火山那一位标志位写的就是 gzip，所以 gzip 是主路径。留一条 zlib 回退是因为
     * type4me 用的 Apple `COMPRESSION_ZLIB` 其实是 raw DEFLATE，两边到底谁跟服务端
     * 对得上还没有真实抓包能证明。**故意不再回退到 raw DEFLATE**：raw 流没有任何
     * 校验头，随便一段二进制都可能"解"出一堆垃圾字节，反而会让
     * [extractServerError] 编出一条根本不存在的错误消息。
     */
    fun gzipDecompress(data: ByteArray): ByteArray {
        if (data.isEmpty()) return ByteArray(0)
        runCatching {
            GZIPInputStream(data.inputStream()).use { it.readBytes() }
        }.getOrNull()?.let { return it }
        inflate(data)?.let { return it }
        throw VolcProtocolException.DecompressionFailed()
    }

    private fun inflate(data: ByteArray): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream(data.size * 2)
            val buffer = ByteArray(16384)
            while (!inflater.finished()) {
                val produced = inflater.inflate(buffer)
                if (produced == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) return null
                } else {
                    out.write(buffer, 0, produced)
                }
            }
            out.toByteArray().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        } finally {
            inflater.end()
        }
    }
}
