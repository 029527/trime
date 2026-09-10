/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.volc

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 对拍 type4me 的 `Type4MeTests/VolcProtocolTests.swift`：
 * 每个 `test...` 用例都在那边有同名的 XCTest 版本，断言的字节值一模一样。
 */
class VolcProtocolTest :
    FunSpec({

        fun ByteArray.u(index: Int) = this[index].toInt() and 0xFF

        // MARK: - 帧头编码

        test("headerEncoding_fullClientRequest") {
            val data = VolcHeader(
                messageType = VolcMessageType.FULL_CLIENT_REQUEST,
                flags = VolcMessageFlags.NO_SEQUENCE,
                serialization = VolcSerialization.JSON,
                compression = VolcCompression.GZIP,
            ).encode()
            data.size shouldBe 4
            // byte0: version=0001 | headerSize=0001 => 0x11
            data.u(0) shouldBe 0x11
            // byte1: msgType=0001 | flags=0000 => 0x10
            data.u(1) shouldBe 0x10
            // byte2: serialization=0001 | compression=0001 => 0x11
            data.u(2) shouldBe 0x11
            data.u(3) shouldBe 0x00
        }

        test("headerEncoding_audioData") {
            val data = VolcHeader(
                messageType = VolcMessageType.AUDIO_ONLY_REQUEST,
                flags = VolcMessageFlags.POSITIVE_SEQUENCE,
                serialization = VolcSerialization.NONE,
                compression = VolcCompression.NONE,
            ).encode()
            data.size shouldBe 4
            data.u(0) shouldBe 0x11
            // byte1: msgType=0010 | flags=0001 => 0x21
            data.u(1) shouldBe 0x21
            data.u(2) shouldBe 0x00
            data.u(3) shouldBe 0x00
        }

        test("headerEncoding_lastAudioPacket") {
            val data = VolcHeader(
                messageType = VolcMessageType.AUDIO_ONLY_REQUEST,
                flags = VolcMessageFlags.NEGATIVE_SEQUENCE_LAST,
                serialization = VolcSerialization.NONE,
                compression = VolcCompression.NONE,
            ).encode()
            // byte1: msgType=0010 | flags=0011 => 0x23
            data.u(1) shouldBe 0x23
        }

        // MARK: - 帧头解码

        test("headerDecoding_serverResponse") {
            val header = VolcHeader.decode(byteArrayOf(0x11, 0x90.toByte(), 0x11, 0x00))
            header.version shouldBe 1
            header.headerSize shouldBe 1
            header.messageType shouldBe VolcMessageType.SERVER_RESPONSE
            header.flags shouldBe VolcMessageFlags.NO_SEQUENCE
            header.serialization shouldBe VolcSerialization.JSON
            header.compression shouldBe VolcCompression.GZIP
        }

        test("headerDecoding_serverError") {
            val header = VolcHeader.decode(byteArrayOf(0x11, 0xF0.toByte(), 0x10, 0x00))
            header.messageType shouldBe VolcMessageType.SERVER_ERROR
            header.serialization shouldBe VolcSerialization.JSON
            header.compression shouldBe VolcCompression.NONE
        }

        test("headerDecoding_asyncFinal") {
            val header = VolcHeader.decode(byteArrayOf(0x11, 0x94.toByte(), 0x10, 0x00))
            header.messageType shouldBe VolcMessageType.SERVER_RESPONSE
            header.flags shouldBe VolcMessageFlags.ASYNC_FINAL
            header.flags.hasSequence.shouldBeFalse()
        }

        test("headerDecoding_tooShort") {
            shouldThrow<VolcProtocolException.HeaderTooShort> {
                VolcHeader.decode(byteArrayOf(0x11, 0x90.toByte()))
            }
        }

        // MARK: - full client request 的 JSON

        test("clientRequestJSON") {
            val payload = VolcProtocol.buildClientRequest(uid = "test-user-123")
            val root = Json.parseToJsonElement(payload.decodeToString()).jsonObject

            root["user"]!!.jsonObject["uid"]!!.jsonPrimitive.content shouldBe "test-user-123"

            val audio = root["audio"]!!.jsonObject
            audio["format"]!!.jsonPrimitive.content shouldBe "pcm"
            audio["codec"]!!.jsonPrimitive.content shouldBe "raw"
            audio["rate"]!!.jsonPrimitive.int shouldBe 16000
            audio["bits"]!!.jsonPrimitive.int shouldBe 16
            audio["channel"]!!.jsonPrimitive.int shouldBe 1

            val request = root["request"]!!.jsonObject
            request["show_utterances"]!!.jsonPrimitive.boolean shouldBe true
            request["result_type"]!!.jsonPrimitive.content shouldBe "full"
            request["enable_nonstream"]!!.jsonPrimitive.boolean shouldBe true
            request["enable_ddc"]!!.jsonPrimitive.boolean shouldBe true
            request["context"] shouldBe null
        }

        test("clientRequestJSON_usesHotwordsAndBoostingCorpusFields") {
            // 给了云端热词表 ID 就不再内联热词（表优先）
            val payload = VolcProtocol.buildClientRequest(
                uid = "test-user-123",
                options = VolcRequestOptions(
                    enablePunc = true,
                    hotwords = listOf("Type4Me", "DeepSeek"),
                    boostingTableId = "boost-123",
                    contextHistoryLength = 6,
                ),
            )
            val request = Json.parseToJsonElement(payload.decodeToString()).jsonObject["request"]!!.jsonObject
            request["context_history_length"]!!.jsonPrimitive.int shouldBe 6
            request["context"] shouldBe null
            request["corpus"]!!.jsonObject["boosting_table_id"]!!.jsonPrimitive.content shouldBe "boost-123"
        }

        test("clientRequestJSON_usesInlineHotwordsWhenNoBoostingTable") {
            val payload = VolcProtocol.buildClientRequest(
                uid = "test-user-123",
                options = VolcRequestOptions(
                    enablePunc = true,
                    hotwords = listOf("Type4Me", "DeepSeek"),
                    boostingTableId = null,
                    contextHistoryLength = 6,
                ),
            )
            val request = Json.parseToJsonElement(payload.decodeToString()).jsonObject["request"]!!.jsonObject
            val context = Json.parseToJsonElement(request["context"]!!.jsonPrimitive.content).jsonObject
            val hotwords = context["hotwords"]!!.jsonArray
            hotwords.size shouldBe 2
            hotwords[0].jsonObject["word"]!!.jsonPrimitive.content shouldBe "Type4Me"
            request["corpus"] shouldBe null
        }

        // MARK: - 整帧编码

        test("encodeMessage_withSequenceNumber") {
            val message = VolcProtocol.encodeMessage(
                header = VolcHeader(
                    messageType = VolcMessageType.AUDIO_ONLY_REQUEST,
                    flags = VolcMessageFlags.POSITIVE_SEQUENCE,
                    serialization = VolcSerialization.NONE,
                    compression = VolcCompression.NONE,
                ),
                payload = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte()),
                sequenceNumber = 1,
            )
            // 4（帧头）+ 4（seq）+ 4（长度）+ 3（payload）= 15
            message.size shouldBe 15
            listOf(message.u(4), message.u(5), message.u(6), message.u(7)) shouldBe listOf(0, 0, 0, 1)
            listOf(message.u(8), message.u(9), message.u(10), message.u(11)) shouldBe listOf(0, 0, 0, 3)
            listOf(message.u(12), message.u(13), message.u(14)) shouldBe listOf(0xAA, 0xBB, 0xCC)
        }

        test("encodeMessage_noSequenceNumber") {
            val message = VolcProtocol.encodeMessage(
                header = VolcHeader(
                    messageType = VolcMessageType.FULL_CLIENT_REQUEST,
                    flags = VolcMessageFlags.NO_SEQUENCE,
                    serialization = VolcSerialization.JSON,
                    compression = VolcCompression.NONE,
                ),
                payload = byteArrayOf(0x01, 0x02),
            )
            // 4 + 4 + 2 = 10
            message.size shouldBe 10
            listOf(message.u(4), message.u(5), message.u(6), message.u(7)) shouldBe listOf(0, 0, 0, 2)
        }

        // MARK: - 音频包

        test("encodeAudioPacket_normal") {
            val packet = VolcProtocol.encodeAudioPacket(ByteArray(10) { 0x55 }, isLast = false)
            // byte1: audioOnly=0010 | noSequence=0000 => 0x20
            packet.u(1) shouldBe 0x20
            packet.size shouldBe 18
        }

        test("encodeAudioPacket_last") {
            val packet = VolcProtocol.encodeAudioPacket(ByteArray(10) { 0x55 }, isLast = true)
            // byte1: audioOnly=0010 | lastPacketNoSequence=0010 => 0x22
            packet.u(1) shouldBe 0x22
            packet.size shouldBe 18
        }

        // MARK: - 响应解码

        test("decodeServerMessage_withGzip") {
            val body = """{"text":"hello world","utterances":[{"text":"hello","definite":true},{"text":"world","definite":false}]}"""
            val message = VolcProtocol.encodeMessage(
                header = VolcHeader(
                    messageType = VolcMessageType.SERVER_RESPONSE,
                    flags = VolcMessageFlags.NO_SEQUENCE,
                    serialization = VolcSerialization.JSON,
                    compression = VolcCompression.GZIP,
                ),
                payload = VolcProtocol.gzipCompress(body.toByteArray()),
            )
            val result = VolcProtocol.decodeServerMessage(message)
            result.text shouldBe "hello world"
            result.utterances shouldBe listOf(
                VolcUtterance("hello", true),
                VolcUtterance("world", false),
            )
        }

        test("decodeServerResponse_preservesAsyncFinalFlag") {
            val body = """{"result":{"text":"修正后的整句","utterances":[{"text":"修正后的整句","definite":true}]}}"""
            val message = VolcProtocol.encodeMessage(
                header = VolcHeader(
                    messageType = VolcMessageType.SERVER_RESPONSE,
                    flags = VolcMessageFlags.ASYNC_FINAL,
                    serialization = VolcSerialization.JSON,
                    compression = VolcCompression.NONE,
                ),
                payload = body.toByteArray(),
            )
            val response = VolcProtocol.decodeServerResponse(message)
            response.header.flags shouldBe VolcMessageFlags.ASYNC_FINAL
            response.result.text shouldBe "修正后的整句"
            response.result.utterances.first().text shouldBe "修正后的整句"
        }

        test("decodeServerResponsePreservesCanonicalRevisionAndUtteranceMetadata") {
            val body =
                """{"result":{"text":"最新修订后的完整文本","utterances":[{"text":"已确认前缀。","definite":true},{"text":"新的临时文本","definite":false}]}}"""
            val message = VolcProtocol.encodeMessage(
                header = VolcHeader(
                    messageType = VolcMessageType.SERVER_RESPONSE,
                    flags = VolcMessageFlags.NO_SEQUENCE,
                    serialization = VolcSerialization.JSON,
                    compression = VolcCompression.NONE,
                ),
                payload = body.toByteArray(),
            )
            val response = VolcProtocol.decodeServerResponse(message)
            response.result.text shouldBe "最新修订后的完整文本"
            response.result.utterances shouldBe listOf(
                VolcUtterance("已确认前缀。", true),
                VolcUtterance("新的临时文本", false),
            )
        }

        test("decodeServerMessage_uncompressed") {
            val message = VolcProtocol.encodeMessage(
                header = VolcHeader(
                    messageType = VolcMessageType.SERVER_RESPONSE,
                    flags = VolcMessageFlags.NO_SEQUENCE,
                    serialization = VolcSerialization.JSON,
                    compression = VolcCompression.NONE,
                ),
                payload = """{"text":"test","utterances":[]}""".toByteArray(),
            )
            val result = VolcProtocol.decodeServerMessage(message)
            result.text shouldBe "test"
            result.utterances.size shouldBe 0
        }

        test("decodeServerMessage_serverError") {
            val message = VolcProtocol.encodeMessage(
                header = VolcHeader(
                    messageType = VolcMessageType.SERVER_ERROR,
                    flags = VolcMessageFlags.NO_SEQUENCE,
                    serialization = VolcSerialization.JSON,
                    compression = VolcCompression.NONE,
                ),
                payload = """{"code":1001,"message":"auth failed"}""".toByteArray(),
            )
            val error = shouldThrow<VolcProtocolException.ServerError> {
                VolcProtocol.decodeServerMessage(message)
            }
            error.code shouldBe 1001
            error.serverMessage shouldBe "auth failed"
        }

        // MARK: - gzip 往返

        test("gzipRoundTrip") {
            val original = "The quick brown fox jumps over the lazy dog. 重复数据重复数据重复数据".toByteArray()
            VolcProtocol.gzipDecompress(VolcProtocol.gzipCompress(original)).decodeToString() shouldBe original.decodeToString()
        }

        test("gzipRoundTrip_emptyData") {
            VolcProtocol.gzipCompress(ByteArray(0)).size shouldBe 0
            VolcProtocol.gzipDecompress(ByteArray(0)).size shouldBe 0
        }

        test("gzipCompress_producesSmaller") {
            val original = ByteArray(10000) { 0x41 }
            VolcProtocol.gzipCompress(original).size shouldBeLessThan original.size
        }

        // MARK: - 鉴权 header

        test("authHeaders_apiKeyMode") {
            val headers = VolcProtocol.authHeaders(
                VolcAuthentication.ApiKey("k-123"),
                resourceId = VolcConfig.RESOURCE_ID_SEED_ASR,
                connectId = "conn-1",
            )
            headers["X-Api-Key"] shouldBe "k-123"
            headers["X-Api-Resource-Id"] shouldBe VolcConfig.RESOURCE_ID_SEED_ASR
            headers["X-Api-Connect-Id"] shouldBe "conn-1"
            headers["X-Api-App-Key"] shouldBe null
        }

        test("authHeaders_legacyMode") {
            val headers = VolcProtocol.authHeaders(
                VolcAuthentication.Legacy("app-1", "access-1"),
                resourceId = VolcConfig.RESOURCE_ID_BIG_ASR,
                connectId = "conn-2",
            )
            headers["X-Api-App-Key"] shouldBe "app-1"
            headers["X-Api-Access-Key"] shouldBe "access-1"
            headers["X-Api-Key"] shouldBe null
        }

        // MARK: - 文本累积

        test("accumulator_keepsLastTextOnBlankInterimFrame") {
            val accumulator = VolcTranscriptAccumulator()
            accumulator.apply(VolcASRResult("你好", emptyList()), isFinal = false) shouldBe "你好"
            // 空的中间帧不许把已有结果抹掉
            accumulator.apply(VolcASRResult("", emptyList()), isFinal = false) shouldBe "你好"
            // 服务端修订是替换，不是追加
            accumulator.apply(VolcASRResult("你好世界", emptyList()), isFinal = true) shouldBe "你好世界"
        }

        test("accumulator_fallsBackToUtterances") {
            val accumulator = VolcTranscriptAccumulator()
            val result = VolcASRResult("", listOf(VolcUtterance("今天", true), VolcUtterance("天气", false)))
            accumulator.apply(result, isFinal = false) shouldBe "今天天气"
        }

        test("clientRequest_isValidUtf8Json") {
            val payload = VolcProtocol.buildClientRequest(uid = "用户-中文")
            Json.parseToJsonElement(payload.decodeToString()).jsonObject["user"]
                .shouldNotBeNull()
        }
    })
