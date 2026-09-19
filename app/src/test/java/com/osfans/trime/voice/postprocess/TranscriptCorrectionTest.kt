/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.postprocess

import com.osfans.trime.voice.llm.CorrectionOutputValidator
import com.osfans.trime.voice.llm.FakeTranscriptCorrector
import com.osfans.trime.voice.llm.LlmCorrectionException
import com.osfans.trime.voice.llm.TranscriptCorrector
import com.osfans.trime.voice.vocab.VoiceVocabulary
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

class TranscriptCorrectionTest :
    FunSpec({

        val vocab = VoiceVocabulary.parse("mappings:\n  克劳德: Claude")
        val pipeline = TranscriptPipeline.of(VocabularyMappingProcessor { vocab })

        fun corrector(block: suspend (String) -> String) = object : TranscriptCorrector {
            override val name = "test"

            override suspend fun correct(text: String) = block(text)
        }

        test("中间结果只过映射，不纠错") {
            pipeline.process("我用克劳德", isFinal = false) shouldBe "我用Claude"
        }

        test("纠错结果再过一遍映射：模型改回去的词会被映射回来") {
            val outcome = pipeline.correct("我用Claude写带马", corrector { "我用克劳德写代码。" })
            outcome shouldBe CorrectionOutcome.Corrected("我用Claude写代码。")
        }

        test("结果没过校验：原文，带原因") {
            val outcome = pipeline.correct("我用Claude写代码", corrector { "以下是纠正后的文本：我用 Claude 写代码。" })
            outcome shouldBe CorrectionOutcome.Rejected("我用Claude写代码", CorrectionOutputValidator.Rejection.PREAMBLE)
        }

        test("请求失败：原文，错误说明给胶囊") {
            val outcome = pipeline.correct("我用Claude写代码", corrector { throw LlmCorrectionException("纠错服务鉴权失败（HTTP 401）") })
            outcome shouldBe CorrectionOutcome.Failed("我用Claude写代码", "纠错服务鉴权失败（HTTP 401），已保留原文")
        }

        test("意外异常也退回原文，异常信息不外露") {
            val outcome = pipeline.correct("我用Claude写代码", corrector { error("secret-ish internals") })
            outcome shouldBe CorrectionOutcome.Failed("我用Claude写代码", "纠错出错，已保留原文")
        }

        test("超时：原文") {
            val outcome = pipeline.correct(
                "我用Claude写代码",
                FakeTranscriptCorrector(FakeTranscriptCorrector.Outcome.TIMEOUT),
                timeoutMs = 50,
            )
            outcome shouldBe CorrectionOutcome.Failed("我用Claude写代码", "纠错超时，已保留原文")
        }

        test("被打断（取消）时往外抛，不当成失败吞掉") {
            coroutineScope {
                val job = async {
                    pipeline.correct(
                        "我用Claude写代码",
                        corrector {
                            delay(10_000)
                            "不该出现"
                        },
                        timeoutMs = 60_000,
                    )
                }
                delay(20)
                job.cancel()
                shouldThrow<CancellationException> { job.await() }
            }
        }

        test("假纠错：固定的改动，能过校验") {
            FakeTranscriptCorrector.polish("我用 Qwen3.5 和Claude写代码") shouldBe "我用 Qwen3.5 和 Claude 写代码。"
            FakeTranscriptCorrector.polish("好了。") shouldBe "好了。"
            val outcome = pipeline.correct(
                "我用 Qwen3.5 和Claude写代码",
                FakeTranscriptCorrector(FakeTranscriptCorrector.Outcome.SUCCESS, delayMs = 1),
            )
            outcome shouldBe CorrectionOutcome.Corrected("我用 Qwen3.5 和 Claude 写代码。")
        }

        test("假纠错的失败模式") {
            val outcome = pipeline.correct("我用Claude写代码", FakeTranscriptCorrector(FakeTranscriptCorrector.Outcome.FAILURE, delayMs = 1))
            outcome shouldBe CorrectionOutcome.Failed("我用Claude写代码", "模拟纠错失败，已保留原文")
        }

        test("纯数字判定：阿拉伯数字、中文数字、包含标点") {
            com.osfans.trime.voice.llm.LlmCorrectionConfig.isNumericOnly("123456") shouldBe true
            com.osfans.trime.voice.llm.LlmCorrectionConfig.isNumericOnly("138 0000 0000") shouldBe true
            com.osfans.trime.voice.llm.LlmCorrectionConfig.isNumericOnly("10086。") shouldBe true
            com.osfans.trime.voice.llm.LlmCorrectionConfig.isNumericOnly("3.14159") shouldBe true
            com.osfans.trime.voice.llm.LlmCorrectionConfig.isNumericOnly("1,000,000") shouldBe true
            com.osfans.trime.voice.llm.LlmCorrectionConfig.isNumericOnly("一二三四五六") shouldBe true
            com.osfans.trime.voice.llm.LlmCorrectionConfig.isNumericOnly("一百二十万零五百") shouldBe true
            com.osfans.trime.voice.llm.LlmCorrectionConfig.isNumericOnly("零点五") shouldBe true
            com.osfans.trime.voice.llm.LlmCorrectionConfig.isNumericOnly("98%") shouldBe true

            com.osfans.trime.voice.llm.LlmCorrectionConfig.isNumericOnly("我有3个苹果") shouldBe false
            com.osfans.trime.voice.llm.LlmCorrectionConfig.isNumericOnly("第12条") shouldBe false
            com.osfans.trime.voice.llm.LlmCorrectionConfig.isNumericOnly("iphone16") shouldBe false
            com.osfans.trime.voice.llm.LlmCorrectionConfig.isNumericOnly("") shouldBe false
            com.osfans.trime.voice.llm.LlmCorrectionConfig.isNumericOnly("...") shouldBe false
        }

        test("纯数字跳过纠错：作为输入法默认行为") {
            // 纯数字即使超过字数阈值也不走纠错
            com.osfans.trime.voice.llm.LlmCorrectionConfig.shouldCorrect("13800000000", threshold = 4) shouldBe false
            com.osfans.trime.voice.llm.LlmCorrectionConfig.shouldCorrect("123456。", threshold = 4) shouldBe false
            com.osfans.trime.voice.llm.LlmCorrectionConfig.shouldCorrect("一二三四五六", threshold = 4) shouldBe false

            // 非纯数字超过字数阈值正常纠错
            com.osfans.trime.voice.llm.LlmCorrectionConfig.shouldCorrect("我有3个苹果", threshold = 4) shouldBe true
            // 非纯数字低于字数阈值不纠错
            com.osfans.trime.voice.llm.LlmCorrectionConfig.shouldCorrect("好的", threshold = 4) shouldBe false
        }
    })
