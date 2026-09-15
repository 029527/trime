/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.llm

import com.osfans.trime.voice.llm.CorrectionOutputValidator.Rejection
import com.osfans.trime.voice.llm.CorrectionOutputValidator.Verdict
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CorrectionOutputValidatorTest :
    FunSpec({

        fun validate(
            input: String,
            output: String,
        ) = CorrectionOutputValidator.validate(input, output)

        test("正常的纠错通过") {
            validate("我用克劳德写代码", "我用 Claude 写代码。") shouldBe Verdict.Accepted("我用 Claude 写代码。")
            validate("没什么要改的。", "没什么要改的。") shouldBe Verdict.Accepted("没什么要改的。")
        }

        test("空的不行") {
            validate("我用克劳德写代码", "") shouldBe Verdict.Rejected(Rejection.EMPTY)
            validate("我用克劳德写代码", "  \n ") shouldBe Verdict.Rejected(Rejection.EMPTY)
            validate("我用克劳德写代码", "<think>嗯</think>") shouldBe Verdict.Rejected(Rejection.EMPTY)
        }

        test("剥掉整段引号、代码块和回显的标签") {
            validate("我用克劳德写代码", "“我用 Claude 写代码。”") shouldBe Verdict.Accepted("我用 Claude 写代码。")
            validate("我用克劳德写代码", "\"我用 Claude 写代码。\"") shouldBe Verdict.Accepted("我用 Claude 写代码。")
            validate("我用克劳德写代码", "```\n我用 Claude 写代码。\n```") shouldBe Verdict.Accepted("我用 Claude 写代码。")
            validate("我用克劳德写代码", "<transcript>\n我用 Claude 写代码。\n</transcript>") shouldBe Verdict.Accepted("我用 Claude 写代码。")
        }

        test("原文自己带引号时不剥") {
            validate("「你好」", "「你好」") shouldBe Verdict.Accepted("「你好」")
        }

        test("前面加说明的不行") {
            validate("我用克劳德写代码", "以下是纠正后的文本：我用 Claude 写代码。") shouldBe Verdict.Rejected(Rejection.PREAMBLE)
            validate("我用克劳德写代码", "纠正后：\n我用 Claude 写代码。") shouldBe Verdict.Rejected(Rejection.PREAMBLE)
            validate("i use cloud code today", "Here is the corrected text: I use Claude Code today.") shouldBe
                Verdict.Rejected(Rejection.PREAMBLE)
            // 原文本来就这么开头的，照样放行
            validate("以下是我今天的安排", "以下是我今天的安排。") shouldBe Verdict.Accepted("以下是我今天的安排。")
        }

        test("后面加解释的不行") {
            validate("我用克劳德写代码吧", "我用 Claude 写代码吧。\n说明：把克劳德改成了 Claude") shouldBe
                Verdict.Rejected(Rejection.EXPLANATION)
        }

        test("长了太多：改写、扩写或者回答了问题") {
            val input = "明天北京天气怎么样"
            validate(input, "明天北京天气晴，最高气温 25 度，最低 15 度，适合出行，记得带伞以防万一。") shouldBe
                Verdict.Rejected(Rejection.TOO_LONG)
            // 短文本有固定余量：补几个标点和空格没关系
            validate("我用克劳德写代码", "我用 Claude Code 写代码。") shouldBe Verdict.Accepted("我用 Claude Code 写代码。")
        }

        test("短了太多：删掉了内容") {
            val input = "我觉得这个方案整体上是可以的但是细节还需要再讨论一下然后下周再定"
            validate(input, "方案可以，下周再定。") shouldBe Verdict.Rejected(Rejection.TOO_SHORT)
            validate("嗯我觉得可以", "我觉得可以") shouldBe Verdict.Accepted("我觉得可以")
        }

        test("文字种类变了（被翻译了）不行") {
            validate("我今天很开心", "I am very happy today.") shouldBe Verdict.Rejected(Rejection.SCRIPT_CHANGED)
            validate("I am happy today", "我今天很开心啊") shouldBe Verdict.Rejected(Rejection.SCRIPT_CHANGED)
        }
    })
