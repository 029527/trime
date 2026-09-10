/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice

import com.osfans.trime.voice.provider.VoiceRecognitionEvent
import com.osfans.trime.voice.provider.VolcVoiceRecognitionProvider
import com.osfans.trime.voice.volc.VolcAuthentication
import com.osfans.trime.voice.volc.VolcConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * 打真实火山服务端的联调测试，**只在环境变量齐全时才跑**，所以 CI 和平时的
 * `testDebugUnitTest` 都会静默跳过它。
 *
 * 单元测试只能证明「我们发出去的字节符合 type4me 的实现」，证明不了服务端认这套；
 * 这条用例补的就是那一段：真实握手、连续音频包、空的 last packet、中间结果与定稿的回流、
 * 以及响应的解压路径（火山标志位写 gzip，type4me 用的却是 raw DEFLATE，谁对只有真连才知道）。
 *
 * 跑法（音频要 16kHz 单声道 16bit WAV）：
 * ```
 * VOLC_APP_KEY=... VOLC_ACCESS_KEY=... VOLC_TEST_WAV=/path/to/speech.wav \
 *   ./gradlew :app:testDebugUnitTest --tests '*VolcLiveIntegrationTest*'
 * ```
 */
class VolcLiveIntegrationTest :
    FunSpec({

        val appKey = System.getenv("VOLC_APP_KEY")
        val accessKey = System.getenv("VOLC_ACCESS_KEY")
        val wavPath = System.getenv("VOLC_TEST_WAV")
        val resourceId = System.getenv("VOLC_RESOURCE_ID") ?: VolcConfig.RESOURCE_ID_SEED_ASR

        val ready = !appKey.isNullOrBlank() && !accessKey.isNullOrBlank() && !wavPath.isNullOrBlank()

        test("真实火山链路：推 WAV 进去，能拿到中间结果和定稿").config(enabled = ready) {
            val pcm = File(wavPath!!).readBytes().drop(44).toByteArray() // 跳过 WAV 头
            println("音频 ${pcm.size} 字节 / ${"%.1f".format(pcm.size / 32000.0)}s，resourceId=$resourceId")

            val provider = VolcVoiceRecognitionProvider(configProvider = {
                VolcConfig(
                    authentication = VolcAuthentication.Legacy(appKey!!, accessKey!!),
                    resourceId = resourceId,
                    uid = "trime-live-test",
                )
            })

            val audio: Flow<ByteArray> = flow {
                var offset = 0
                while (offset < pcm.size) {
                    val end = minOf(offset + 3200, pcm.size) // 100ms @16k/16bit
                    emit(pcm.copyOfRange(offset, end))
                    offset = end
                    delay(50)
                }
            }

            val partials = mutableListOf<String>()
            var final: String? = null
            var failure: VoiceRecognitionEvent.Failure? = null

            provider.recognize(audio).collect { event ->
                when (event) {
                    is VoiceRecognitionEvent.Partial -> {
                        partials += event.text
                        println("  中间: ${event.text}")
                    }
                    is VoiceRecognitionEvent.Final -> {
                        final = event.text
                        println("  定稿: ${event.text}")
                    }
                    is VoiceRecognitionEvent.Failure -> {
                        failure = event
                        println("  失败: ${event.message} (可重试=${event.recoverable})")
                    }
                    VoiceRecognitionEvent.Completed -> println("  会话结束")
                }
            }

            failure shouldBe null
            partials.size shouldNotBe 0
            final shouldNotBe null
            final!! shouldContain "输入法"
        }
    })
