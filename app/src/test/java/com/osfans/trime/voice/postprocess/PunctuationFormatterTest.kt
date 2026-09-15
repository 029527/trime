/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.postprocess

import com.osfans.trime.voice.vocab.VoiceVocabulary
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import com.osfans.trime.voice.postprocess.PeriodStyle.CHINESE as ZH
import com.osfans.trime.voice.postprocess.PeriodStyle.ENGLISH as EN
import com.osfans.trime.voice.postprocess.PeriodStyle.NONE as NO
import com.osfans.trime.voice.postprocess.PeriodStyle.SPACE as SP
import com.osfans.trime.voice.postprocess.PunctuationRule.PRESERVE as KEEP
import com.osfans.trime.voice.postprocess.PunctuationRule.QUESTIONS_AND_EXCLAMATIONS as QE
import com.osfans.trime.voice.postprocess.PunctuationRule.REMOVE_ALL as ALL
import com.osfans.trime.voice.postprocess.PunctuationRule.STRIP_PERIODS as END_PERIOD
import com.osfans.trime.voice.postprocess.PunctuationRule.STRIP_TRAILING as END_ANY

class PunctuationFormatterTest :
    FunSpec({

        class Case(
            val style: PeriodStyle,
            val rule: PunctuationRule,
            val input: String,
            val expected: String,
        )

        val cases = listOf(
            // 默认：一个字不改
            Case(ZH, KEEP, "今天天气不错。我们出去走走吧。", "今天天气不错。我们出去走走吧。"),
            Case(ZH, KEEP, "Hello world. 3.5 e.g. ", "Hello world. 3.5 e.g. "),

            // 句号
            Case(EN, KEEP, "今天天气不错。我们出去走走吧。", "今天天气不错. 我们出去走走吧."),
            Case(SP, KEEP, "今天天气不错。我们出去走走吧。", "今天天气不错 我们出去走走吧"),
            Case(NO, KEEP, "今天天气不错。我们出去走走吧。", "今天天气不错我们出去走走吧"),
            Case(ZH, END_PERIOD, "今天天气不错。我们出去走走吧。", "今天天气不错。我们出去走走吧"),
            Case(EN, END_PERIOD, "今天天气不错。我们出去走走吧。", "今天天气不错. 我们出去走走吧"),
            Case(NO, END_PERIOD, "今天天气不错。我们出去走走吧。", "今天天气不错我们出去走走吧"),

            // 中英混排：句号挨着英文时不粘在一起
            Case(NO, KEEP, "我用Claude。它写得不错。", "我用Claude 它写得不错"),
            Case(SP, KEEP, "Hello world. How are you?", "Hello world How are you?"),
            Case(NO, KEEP, "Hello world. How are you?", "Hello world How are you?"),
            Case(EN, KEEP, "Hello world. How are you?", "Hello world. How are you?"),
            Case(SP, KEEP, "句号后面有空格。 下一句。", "句号后面有空格 下一句"),
            // 火山把英文分句直接拼起来
            Case(SP, KEEP, "Hello world.How are you?", "Hello world How are you?"),
            Case(SP, KEEP, "用 node.js 写。", "用 node.js 写"),

            // 不是句号的点
            Case(SP, KEEP, "我用 Qwen3.5 写代码。", "我用 Qwen3.5 写代码"),
            Case(SP, KEEP, "版本 v1.2.3 发布了。", "版本 v1.2.3 发布了"),
            Case(SP, KEEP, "打开 https://example.com/a.html 看看。", "打开 https://example.com/a.html 看看"),
            Case(SP, KEEP, "访问 www.example.com.", "访问 www.example.com"),
            Case(SP, KEEP, "发到 me@mail.example.com 吧。", "发到 me@mail.example.com 吧"),
            Case(SP, KEEP, "比如 e.g. this one. OK", "比如 e.g. this one OK"),
            Case(SP, KEEP, "Mr. Smith is here. Dr. Lee too.", "Mr. Smith is here Dr. Lee too"),
            Case(SP, KEEP, "等一下... 好了。", "等一下... 好了"),
            Case(EN, KEEP, "价格是 3.5。", "价格是 3.5."),
            Case(SP, KEEP, "I have 3. You have 2.", "I have 3 You have 2"),

            // 问号感叹号不归「句号」管
            Case(EN, KEEP, "真的吗？太好了！走吧。", "真的吗？太好了！走吧."),
            Case(SP, KEEP, "Really? Great! Go.", "Really? Great! Go"),

            // 引号括号
            Case(SP, KEEP, "他说“好的。”然后走了。", "他说“好的” 然后走了"),
            Case(EN, KEEP, "他说“好的。”然后走了。", "他说“好的.” 然后走了."),
            Case(ZH, END_PERIOD, "他说“好的。”", "他说“好的”"),

            // 换行前的句号不补空格
            Case(SP, KEEP, "第一行。\n第二行。", "第一行\n第二行"),
            Case(EN, KEEP, "第一行。\n第二行。", "第一行.\n第二行."),

            // 句末规则
            Case(ZH, END_PERIOD, "真的吗？", "真的吗？"),
            Case(ZH, END_ANY, "真的吗？", "真的吗"),
            Case(ZH, END_ANY, "好的，然后呢……", "好的，然后呢"),
            Case(EN, END_ANY, "Really?!", "Really"),

            // 只留问号感叹号 / 全部去掉
            Case(ZH, QE, "今天天气不错，要不要出去走走？好啊！", "今天天气不错要不要出去走走？好啊！"),
            Case(ZH, ALL, "今天天气不错，要不要出去走走？好啊！", "今天天气不错要不要出去走走好啊"),
            Case(SP, ALL, "今天天气不错。要不要出去走走？好啊。", "今天天气不错 要不要出去走走好啊"),
            Case(EN, ALL, "今天天气不错。我们走吧。", "今天天气不错 我们走吧"),
            Case(ZH, ALL, "我用 Qwen3.5，价格 1,000 元，10:30 见，don't worry。", "我用 Qwen3.5 价格 1,000 元 10:30 见 don't worry"),
            Case(ZH, ALL, "打开 https://example.com/?q=1，看看。", "打开 https://example.com/?q=1 看看"),
            Case(ZH, ALL, "我用Claude，它很好。", "我用Claude 它很好"),
            Case(ZH, QE, "Hello, world. Really?", "Hello world Really?"),
        )

        cases.forEach { case ->
            test("${case.style}+${case.rule}: ${case.input.replace("\n", "⏎")}") {
                PunctuationFormatter.format(case.input, PunctuationOptions(case.style, case.rule)) shouldBe case.expected
            }
        }

        test("格式化是幂等的") {
            cases.forEach { case ->
                val options = PunctuationOptions(case.style, case.rule)
                val once = PunctuationFormatter.format(case.input, options)
                withClue(case.input) { PunctuationFormatter.format(once, options) shouldBe once }
            }
        }

        test("当末尾处理的结果是当中间处理的前缀：分段上屏靠这一点") {
            val first = "今天天气不错。"
            val whole = "今天天气不错。我们出去走走吧？好啊，走吧。"
            PeriodStyle.entries.forEach { style ->
                PunctuationRule.entries.forEach { rule ->
                    val options = PunctuationOptions(style, rule)
                    withClue("$style+$rule") {
                        PunctuationFormatter.format(whole, options) shouldStartWith PunctuationFormatter.format(first, options)
                    }
                }
            }
        }

        test("中间结果和最终结果一样：定稿时不跳") {
            val processor = PunctuationProcessor { PunctuationOptions(SP, END_PERIOD) }
            processor.process("今天天气不错。我们", isFinal = false) shouldBe processor.process("今天天气不错。我们", isFinal = true)
        }

        context("分段上屏") {
            fun join(
                options: PunctuationOptions,
                vararg segments: String,
            ): List<String> {
                val joiner = TranscriptJoiner { PunctuationFormatter.format(it, options) }
                return segments.map { joiner.commit(it) }
            }

            test("空格：分隔符出现在下一段开头，不重复、开头不空") {
                join(PunctuationOptions(SP, KEEP), "今天天气不错。", "我们出去走走吧。", "好的。") shouldBe
                    listOf("今天天气不错", " 我们出去走走吧", " 好的")
            }

            test("英文句点：第一段留点，下一段开头补空格") {
                join(PunctuationOptions(EN, KEEP), "今天天气不错。", "我们出去走走吧。") shouldBe
                    listOf("今天天气不错.", " 我们出去走走吧.")
            }

            test("英文句点 + 去掉句末句号：中间的句点在下一段补上") {
                join(PunctuationOptions(EN, END_PERIOD), "今天天气不错。", "我们出去走走吧。") shouldBe
                    listOf("今天天气不错", ". 我们出去走走吧")
            }

            test("中文句号 + 去掉句末句号") {
                join(PunctuationOptions(ZH, END_PERIOD), "今天天气不错。", "我们出去走走吧。") shouldBe
                    listOf("今天天气不错", "。我们出去走走吧")
            }

            test("不加 + 去掉句末句号：句子连起来，挨着英文时补空格") {
                join(PunctuationOptions(NO, END_PERIOD), "今天天气不错。", "我们走吧。") shouldBe listOf("今天天气不错", "我们走吧")
                join(PunctuationOptions(NO, END_PERIOD), "我用Claude。", "它很好。") shouldBe listOf("我用Claude", " 它很好")
            }

            test("去掉句末标点：问号在下一段补上") {
                join(PunctuationOptions(ZH, END_ANY), "真的吗？", "太好了！") shouldBe listOf("真的吗", "？太好了")
            }

            test("默认组合原样上屏") {
                join(PunctuationOptions(), "今天天气不错。", "我们走吧。") shouldBe listOf("今天天气不错。", "我们走吧。")
            }

            test("拼起来跟一次性格式化一模一样") {
                val segments = listOf("今天天气不错。", "要不要出去走走？", "好啊，我查一下 Qwen3.5。", "Hello world. ", "OK.")
                PeriodStyle.entries.forEach { style ->
                    PunctuationRule.entries.forEach { rule ->
                        val options = PunctuationOptions(style, rule)
                        withClue("$style+$rule") {
                            join(options, *segments.toTypedArray()).joinToString("") shouldBe
                                PunctuationFormatter.format(segments.joinToString(""), options)
                        }
                    }
                }
            }

            test("中间结果的显示和随后的上屏一致") {
                val options = PunctuationOptions(SP, END_PERIOD)
                val joiner = TranscriptJoiner { PunctuationFormatter.format(it, options) }
                joiner.commit("今天天气不错。")
                val shown = joiner.display("我们出去走走吧。")
                joiner.commit("我们出去走走吧。") shouldBe shown
            }

            test("两次听写之间补的分隔符") {
                PunctuationFormatter.separatorAfter("今天天气不错", PunctuationOptions(ZH, KEEP)) shouldBe ""
                PunctuationFormatter.separatorAfter("今天天气不错", PunctuationOptions(EN, KEEP)) shouldBe ". "
                PunctuationFormatter.separatorAfter("今天天气不错", PunctuationOptions(SP, KEEP)) shouldBe " "
                PunctuationFormatter.separatorAfter("今天天气不错", PunctuationOptions(NO, KEEP)) shouldBe ""
                PunctuationFormatter.separatorAfter("我用 Claude", PunctuationOptions(NO, KEEP)) shouldBe " "
                PunctuationFormatter.separatorAfter("今天天气不错", PunctuationOptions(ZH, END_PERIOD)) shouldBe "。"
                PunctuationFormatter.separatorAfter("今天天气不错.", PunctuationOptions(EN, KEEP)) shouldBe " "
                PunctuationFormatter.separatorAfter("要不要出去走走？", PunctuationOptions(SP, KEEP)) shouldBe ""
                PunctuationFormatter.separatorAfter("今天天气不错 ", PunctuationOptions(SP, KEEP)) shouldBe ""
                PunctuationFormatter.separatorAfter("", PunctuationOptions(SP, KEEP)) shouldBe ""
            }

            test("分隔符只加在这次听写的第一段前面") {
                val joiner = TranscriptJoiner { PunctuationFormatter.format(it, PunctuationOptions(SP, KEEP)) }
                joiner.reset(leading = " ")
                joiner.display("我们出去走走吧。") shouldBe " 我们出去走走吧"
                joiner.commit("我们出去走走吧。") shouldBe " 我们出去走走吧"
                joiner.commit("好啊。") shouldBe " 好啊"
            }

            test("对不上时退回单独格式化，开头不留空白") {
                // 前一段末尾的 "." 在后一段接上数字后就不是句号了
                val joiner = TranscriptJoiner { PunctuationFormatter.format(it, PunctuationOptions(SP, KEEP)) }
                joiner.commit("Hello.") shouldBe "Hello"
                joiner.commit("5 apples.") shouldBe "5 apples"
            }
        }

        test("管道：映射先做，标点最后做；纠错拿到的是没整理过的原文") {
            val vocab = VoiceVocabulary.parse("mappings:\n  克劳德: Claude")
            val pipeline = TranscriptPipeline(
                processors = listOf(VocabularyMappingProcessor { vocab }),
                formatters = listOf(PunctuationProcessor { PunctuationOptions(NO, END_PERIOD) }),
            )
            val raw = pipeline.process("我用克劳德。它很好。", isFinal = true)
            raw shouldBe "我用Claude。它很好。"
            pipeline.format(raw, isFinal = true) shouldBe "我用Claude 它很好"
        }
    })
