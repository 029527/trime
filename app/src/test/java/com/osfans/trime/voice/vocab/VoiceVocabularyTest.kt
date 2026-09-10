/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.vocab

import com.osfans.trime.voice.postprocess.TranscriptPipeline
import com.osfans.trime.voice.postprocess.TranscriptProcessor
import com.osfans.trime.voice.postprocess.VocabularyMappingProcessor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class VoiceVocabularyTest :
    FunSpec({

        test("空词库原样返回") {
            VoiceVocabulary.EMPTY.replace("Queen 3.5 很强") shouldBe "Queen 3.5 很强"
            VoiceVocabulary.EMPTY.isEmpty shouldBe true
        }

        test("解析紧凑写法的 mappings") {
            val vocab = VoiceVocabulary.parse(
                """
                mappings:
                  Queen 3.5: Qwen3.5
                  克劳德: Claude
                """.trimIndent(),
            )
            vocab.rules.size shouldBe 2
            vocab.replace("我用 Queen 3.5 和克劳德") shouldBe "我用 Qwen3.5 和Claude"
        }

        test("解析展开写法的 rules，一个目标词多个来源") {
            val vocab = VoiceVocabulary.parse(
                """
                rules:
                  - to: Claude Code
                    from:
                      - 克劳德 code
                      - 克劳德扣的
                """.trimIndent(),
            )
            vocab.rules.size shouldBe 2
            vocab.replace("打开克劳德扣的") shouldBe "打开Claude Code"
            vocab.replace("打开克劳德 code") shouldBe "打开Claude Code"
        }

        test("from 写成单个标量也认") {
            val vocab = VoiceVocabulary.parse(
                """
                rules:
                  - to: Rime
                    from: 瑞姆
                """.trimIndent(),
            )
            vocab.replace("我在用瑞姆") shouldBe "我在用Rime"
        }

        test("热词只解析不参与替换") {
            val vocab = VoiceVocabulary.parse(
                """
                hotwords:
                  - Trime
                  - 小鹤双拼
                  - Trime
                mappings:
                  瑞姆: Rime
                """.trimIndent(),
            )
            vocab.hotwords shouldContainExactly listOf("Trime", "小鹤双拼")
            // 热词不是映射词，不应该动文本
            vocab.replace("Trime 和小鹤双拼") shouldBe "Trime 和小鹤双拼"
        }

        test("默认大小写不敏感") {
            val vocab = VoiceVocabulary.parse("mappings:\n  \"queen 3.5\": Qwen3.5")
            vocab.replace("QUEEN 3.5 出来了") shouldBe "Qwen3.5 出来了"
            vocab.replace("Queen 3.5 出来了") shouldBe "Qwen3.5 出来了"
        }

        test("case_sensitive 打开后只认原样") {
            val vocab = VoiceVocabulary.parse(
                """
                rules:
                  - to: iOS
                    from: [ios]
                    case_sensitive: true
                """.trimIndent(),
            )
            vocab.replace("ios 系统") shouldBe "iOS 系统"
            vocab.replace("IOS 系统") shouldBe "IOS 系统"
        }

        test("最长匹配优先") {
            val vocab = VoiceVocabulary.parse(
                """
                mappings:
                  克劳德: Claude
                  克劳德扣的: Claude Code
                """.trimIndent(),
            )
            vocab.replace("用克劳德扣的写代码") shouldBe "用Claude Code写代码"
            vocab.replace("用克劳德写代码") shouldBe "用Claude写代码"
        }

        test("替换结果不会被再扫一次，a→b b→a 不会来回换") {
            val vocab = VoiceVocabulary.parse(
                """
                mappings:
                  甲: 乙
                  乙: 甲
                """.trimIndent(),
            )
            vocab.replace("甲乙甲") shouldBe "乙甲乙"
        }

        test("替换出来的文本里含有别的词条也不会二次命中") {
            val vocab = VoiceVocabulary.parse(
                """
                mappings:
                  千问: Qwen
                  Qwen: 通义
                """.trimIndent(),
            )
            vocab.replace("千问很强") shouldBe "Qwen很强"
        }

        test("ASCII 词条默认按词边界匹配，不会切进别的单词里") {
            val vocab = VoiceVocabulary.parse("mappings:\n  in: 在")
            vocab.replace("point in time") shouldBe "point 在 time"
            vocab.replace("printing") shouldBe "printing"
        }

        test("whole_word: false 允许在词内部命中") {
            val vocab = VoiceVocabulary.parse(
                """
                rules:
                  - to: 在
                    from: [in]
                    whole_word: false
                """.trimIndent(),
            )
            vocab.replace("printing") shouldBe "pr在t在g"
        }

        test("中文词条不受词边界影响") {
            val vocab = VoiceVocabulary.parse("mappings:\n  深度求索: DeepSeek")
            vocab.replace("我觉得深度求索不错") shouldBe "我觉得DeepSeek不错"
        }

        test("中英混排一次替换多个") {
            val vocab = VoiceVocabulary.parse(
                """
                mappings:
                  Queen 3.5: Qwen3.5
                  克劳德: Claude
                  深度求索: DeepSeek
                """.trimIndent(),
            )
            vocab.replace("Queen 3.5、克劳德和深度求索都试过") shouldBe "Qwen3.5、Claude和DeepSeek都试过"
        }

        test("坏 yaml 退化成空词库而不是抛异常") {
            VoiceVocabulary.parse("mappings: [不是, 映射]").rules.size shouldBe 0
            VoiceVocabulary.parse("这不是 yaml: : :").isEmpty shouldBe true
            VoiceVocabulary.parse("").isEmpty shouldBe true
        }

        test("自带模板能被自己解析出来") {
            val vocab = VoiceVocabulary.parse(VoiceVocabulary.TEMPLATE)
            vocab.hotwords.isNotEmpty() shouldBe true
            vocab.rules.isNotEmpty() shouldBe true
            vocab.replace("Queen 3.5") shouldBe "Qwen3.5"
        }

        // MARK: - 后处理管道

        test("管道按顺序执行，映射词是第一道") {
            val vocab = VoiceVocabulary.parse("mappings:\n  Queen 3.5: Qwen3.5")
            val upper = object : TranscriptProcessor {
                override val name = "test-suffix"
                override fun process(text: String, isFinal: Boolean) = if (isFinal) "$text。" else text
            }
            val pipeline = TranscriptPipeline.of(VocabularyMappingProcessor { vocab }, upper)
            pipeline.process("Queen 3.5 很强", isFinal = false) shouldBe "Qwen3.5 很强"
            pipeline.process("Queen 3.5 很强", isFinal = true) shouldBe "Qwen3.5 很强。"
        }

        test("某一道抛异常不影响上屏") {
            val boom = object : TranscriptProcessor {
                override val name = "boom"
                override fun process(text: String, isFinal: Boolean): String = error("boom")
            }
            TranscriptPipeline.of(boom).process("原样", isFinal = true) shouldBe "原样"
        }

        test("映射词处理器每次现取词库，换了词库立刻生效") {
            var vocab = VoiceVocabulary.EMPTY
            val processor = VocabularyMappingProcessor { vocab }
            processor.process("Queen 3.5", isFinal = true) shouldBe "Queen 3.5"
            vocab = VoiceVocabulary.parse("mappings:\n  Queen 3.5: Qwen3.5")
            processor.process("Queen 3.5", isFinal = true) shouldBe "Qwen3.5"
        }
    })
