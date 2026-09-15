/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.dictation

import com.osfans.trime.voice.audio.FakeSpeechSignal
import com.osfans.trime.voice.dictation.VoiceActivityDetector.Decision
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.shouldBe

class VoiceActivityDetectorTest :
    FunSpec({
        val quiet = -65f
        val voice = -20f

        /** Feeds one chunk every 100 ms from [from] to [to] inclusive; returns the first stop and when it happened. */
        fun VoiceActivityDetector.feed(
            from: Long,
            to: Long,
            level: (Long) -> Float,
        ): Pair<Decision, Long>? {
            var t = from
            while (t <= to) {
                val decision = onAudio(level(t), t)
                if (decision != Decision.CONTINUE) return decision to t
                t += 100
            }
            return null
        }

        test("没人说话：开始后 3 秒停") {
            val vad = VoiceActivityDetector().apply { start(0) }
            vad.feed(100, 2900) { quiet } shouldBe null
            vad.onAudio(quiet, 3000) shouldBe Decision.STOP_NO_SPEECH
            vad.hasSpoken.shouldBeFalse()
        }

        test("说完之后静音 3 秒停，从最后一次人声算起") {
            val vad = VoiceActivityDetector().apply { start(0) }
            vad.feed(100, 1900) { if (it >= 400) voice else quiet } shouldBe null
            vad.hasSpoken.shouldBeTrue()
            vad.feed(2000, 4800) { quiet } shouldBe null
            vad.onAudio(quiet, 4900) shouldBe Decision.STOP_SILENCE
        }

        test("说话中间停顿不到 3 秒不会停") {
            val vad = VoiceActivityDetector().apply { start(0) }
            val result = vad.feed(100, 9000) { t ->
                when (t) {
                    in 400..1500 -> voice
                    in 1600..4000 -> quiet // 2.5 s pause
                    in 4100..6000 -> voice
                    else -> quiet
                }
            }
            result shouldBe (Decision.STOP_SILENCE to 9000L)
        }

        test("按键的一下咔哒声不算开口") {
            val vad = VoiceActivityDetector().apply { start(0) }
            val result = vad.feed(100, 5000) { if (it == 500L) -10f else quiet }
            vad.hasSpoken.shouldBeFalse()
            result shouldBe (Decision.STOP_NO_SPEECH to 3000L)
        }

        test("校准期不判人声，但一上来就说话也能在校准后认出来") {
            val vad = VoiceActivityDetector().apply { start(0) }
            vad.onAudio(voice, 100)
            vad.onAudio(voice, 200)
            vad.hasSpoken.shouldBeFalse()
            vad.onAudio(voice, 300)
            vad.onAudio(voice, 400)
            vad.hasSpoken.shouldBeTrue()
        }

        test("嘈杂环境：底噪抬高阈值，噪声本身不算人声") {
            val vad = VoiceActivityDetector().apply { start(0) }
            val noise = { t: Long -> if (t / 100 % 2 == 0L) -42f else -38f }
            vad.feed(100, 1000, noise) shouldBe null
            vad.noiseFloorDb shouldBe -42f
            vad.thresholdDb shouldBe -32f
            vad.hasSpoken.shouldBeFalse()
            vad.onAudio(-15f, 1100)
            vad.onAudio(-15f, 1200)
            vad.hasSpoken.shouldBeTrue()
        }

        test("环境突然一直很吵：底噪跟上后照样按静音停下，不会一直录") {
            val vad = VoiceActivityDetector().apply { start(0) }
            val result = vad.feed(100, 20_000) { if (it <= 500) quiet else -35f }
            result?.first shouldBe Decision.STOP_SILENCE
            result!!.second shouldBeInRange 5_000L..9_000L
        }

        test("阈值上下限：数字静音压到下限，很吵压到上限") {
            val silent = VoiceActivityDetector().apply { start(0) }
            silent.onAudio(VoiceActivityDetector.SILENCE_DBFS, 100)
            silent.thresholdDb shouldBe -55f
            val loud = VoiceActivityDetector().apply { start(0) }
            loud.onAudio(-25f, 100)
            loud.thresholdDb shouldBe -30f
        }

        test("音量：静音为 0，大声逐步涨到接近 1，停下后回落") {
            val vad = VoiceActivityDetector().apply { start(0) }
            vad.feed(100, 300) { quiet }
            vad.level shouldBe 0f
            vad.feed(400, 700) { -10f }
            vad.level shouldBeGreaterThan 0.9f
            vad.feed(800, 1200) { quiet }
            vad.level shouldBeLessThan 0.2f
        }

        test("配合假音频：说 2 秒后静音，约 3 秒后自动停") {
            val signal = FakeSpeechSignal(speechMs = 2000, leadMs = 400)
            val chunk = ByteArray(3200)
            val vad = VoiceActivityDetector().apply { start(0) }
            val result = vad.feed(0, 10_000) { t ->
                signal.fill(chunk, t)
                pcm16RmsDbfs(chunk)
            }
            result?.first shouldBe Decision.STOP_SILENCE
            // last speaking chunk starts at 2300 ms
            result!!.second shouldBe 5300L
        }

        test("配合假音频：一直不说话，3 秒停") {
            val signal = FakeSpeechSignal(speechMs = 0)
            val chunk = ByteArray(3200)
            val vad = VoiceActivityDetector().apply { start(0) }
            val result = vad.feed(0, 10_000) { t ->
                signal.fill(chunk, t)
                pcm16RmsDbfs(chunk)
            }
            result shouldBe (Decision.STOP_NO_SPEECH to 3000L)
        }

        test("RMS 分贝") {
            pcm16RmsDbfs(ByteArray(3200)) shouldBe VoiceActivityDetector.SILENCE_DBFS
            pcm16RmsDbfs(ByteArray(0)) shouldBe VoiceActivityDetector.SILENCE_DBFS
            val half = ByteArray(3200)
            for (i in half.indices step 2) {
                val s = if (i % 4 == 0) 16384 else -16384
                half[i] = (s and 0xFF).toByte()
                half[i + 1] = (s shr 8).toByte()
            }
            pcm16RmsDbfs(half) shouldBe (-6.02f plusOrMinus 0.01f)
        }
    })
