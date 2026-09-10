/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.volc

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer

/**
 * 对拍 type4me 的 `Type4MeTests/VolcServerErrorTests.swift`。
 *
 * 那边的背景是 issue #290：火山额度用完时，录音自己停了，界面上什么都没有。
 * 真实的错误帧从来没被抓到过，所以这些用例**故意不锁死某一种字节排布**，
 * 只钉住真正要保证的性质：不管帧长什么样，服务端的原话必须能出来；
 * 带错误的帧绝不能被当成「正常结束」。
 */
class VolcServerErrorTest :
    FunSpec({

        val quotaBody = """{"error":"quota exceeded for types: audio_duration_lifetime"}"""
        val quotaMessage = "quota exceeded for types: audio_duration_lifetime"
        val quotaCode = 45_000_292

        fun header(compression: Int = 0) = // version 1, headerSize 1, serverError(0b1111), no sequence
            byteArrayOf(0x11, 0xF0.toByte(), (0x10 or compression).toByte(), 0x00)

        fun bigEndian(value: Int) = ByteBuffer.allocate(4).putInt(value).array()

        /** 文档描述的排布：错误码、长度、正文。 */
        fun codeThenSizeFrame(body: ByteArray) = header() + bigEndian(quotaCode) + bigEndian(body.size) + body

        fun codeThenSizeFrame(body: String) = codeThenSizeFrame(body.toByteArray())

        /** 万一没有错误码那个字。 */
        fun sizeOnlyFrame(body: String) = header() + bigEndian(body.toByteArray().size) + body.toByteArray()

        /** 完全没有分帧字。 */
        fun bareBodyFrame(body: String) = header() + body.toByteArray()

        test("quotaMessageSurvivesEveryPlausibleFraming") {
            val frames = mapOf(
                "code+size" to codeThenSizeFrame(quotaBody),
                "size only" to sizeOnlyFrame(quotaBody),
                "bare body" to bareBodyFrame(quotaBody),
            )
            frames.forEach { (label, frame) ->
                withClue("framing $label lost the server message") {
                    VolcProtocol.extractServerError(frame).second shouldBe quotaMessage
                }
            }
        }

        test("errorCodeIsReportedWhenTheFrameCarriesOne") {
            VolcProtocol.extractServerError(codeThenSizeFrame(quotaBody)).first shouldBe quotaCode
        }

        test("shortLeadingWordIsNotReportedAsAnErrorCode") {
            val (code, message) = VolcProtocol.extractServerError(sizeOnlyFrame(quotaBody))
            code shouldBe null
            (message != null) shouldBe true
        }

        test("gzippedBodyIsStillReadable") {
            val compressed = VolcProtocol.gzipCompress(quotaBody.toByteArray())
            val frame = header(0x01) + bigEndian(quotaCode) + bigEndian(compressed.size) + compressed
            VolcProtocol.extractServerError(frame).second shouldBe quotaMessage
        }

        test("alternateJSONKeysAreAccepted") {
            listOf("error", "message", "msg", "error_msg").forEach { key ->
                val body = """{"$key":"boom"}"""
                VolcProtocol.extractServerError(codeThenSizeFrame(body)).second shouldBe "boom"
            }
        }

        test("codeCarriedInsideTheBodyIsRead") {
            val (code, message) = VolcProtocol.extractServerError(
                sizeOnlyFrame("""{"code":1001,"message":"auth failed"}"""),
            )
            code shouldBe 1001
            message shouldBe "auth failed"
        }

        test("nonJSONBodyFallsBackToRawText") {
            VolcProtocol.extractServerError(bareBodyFrame("quota exceeded")).second shouldBe "quota exceeded"
        }

        test("frameWithoutReadableContentYieldsNothing") {
            // 什么都读不出来的帧，正是「会话正常结束」和「出错了」的分界，
            // 所以绝不能拿分帧字节编出一条消息来。
            val frame = header() + byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04)
            val (code, message) = VolcProtocol.extractServerError(frame)
            code shouldBe null
            message shouldBe null
        }

        test("decodeServerResponseThrowsTheServerMessageNotInvalidPayload") {
            val error = shouldThrow<VolcProtocolException.ServerError> {
                VolcProtocol.decodeServerResponse(codeThenSizeFrame(quotaBody))
            }
            error.code shouldBe quotaCode
            error.serverMessage shouldBe quotaMessage
        }
    })
