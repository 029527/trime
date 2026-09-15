/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.dictation

import com.osfans.trime.voice.dictation.DictationEffect.BeginSession
import com.osfans.trime.voice.dictation.DictationEffect.Commit
import com.osfans.trime.voice.dictation.DictationEffect.EndSession
import com.osfans.trime.voice.dictation.DictationEffect.FinishComposing
import com.osfans.trime.voice.dictation.DictationEffect.ScheduleErrorDismiss
import com.osfans.trime.voice.dictation.DictationEffect.SetComposing
import com.osfans.trime.voice.dictation.DictationEffect.StopAudio
import com.osfans.trime.voice.dictation.DictationState.Failed
import com.osfans.trime.voice.dictation.DictationState.Finishing
import com.osfans.trime.voice.dictation.DictationState.Idle
import com.osfans.trime.voice.dictation.DictationState.Listening
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

class DictationMachineTest :
    FunSpec({
        /** Runs [events] from [initial], collecting every effect in order. */
        fun run(
            initial: DictationState,
            vararg events: DictationEvent,
        ): DictationTransition {
            var state = initial
            val effects = mutableListOf<DictationEffect>()
            for (event in events) {
                val t = DictationMachine.reduce(state, event)
                state = t.state
                effects += t.effects
            }
            return DictationTransition(state, effects)
        }

        test("点一下开始：空闲 → 听写，开会话") {
            run(Idle, DictationEvent.Start) shouldBe DictationTransition(Listening(""), listOf(BeginSession))
        }

        test("听写中再发开始不重复开会话") {
            run(Listening("你好"), DictationEvent.Start) shouldBe DictationTransition(Listening("你好"))
            run(Finishing("你好"), DictationEvent.Start) shouldBe DictationTransition(Finishing("你好"))
        }

        test("错误提示还在时可以直接重新开始") {
            run(Failed("网络断了"), DictationEvent.Start) shouldBe DictationTransition(Listening(""), listOf(BeginSession))
        }

        test("中间结果替换待定文字") {
            run(Idle, DictationEvent.Start, DictationEvent.Partial("我用"), DictationEvent.Partial("我用 Qwen")) shouldBe
                DictationTransition(
                    Listening("我用 Qwen"),
                    listOf(BeginSession, SetComposing("我用"), SetComposing("我用 Qwen")),
                )
        }

        test("再点一下：停止录音，等定稿，定稿后回到空闲") {
            run(
                Listening("写代码"),
                DictationEvent.Stop(StopReason.USER),
                DictationEvent.Partial("写代码。"),
                DictationEvent.Final("写代码。"),
                DictationEvent.Completed,
            ) shouldBe
                DictationTransition(
                    Idle,
                    listOf(StopAudio, SetComposing("写代码。"), Commit("写代码。"), EndSession),
                )
        }

        test("静音自动停和再点一下走同一条路") {
            for (reason in StopReason.entries) {
                run(Listening("好"), DictationEvent.Stop(reason)) shouldBe DictationTransition(Finishing("好"), listOf(StopAudio))
            }
        }

        test("收尾中再收到停止不重复关麦") {
            run(Finishing("好"), DictationEvent.Stop(StopReason.SILENCE)) shouldBe DictationTransition(Finishing("好"))
        }

        test("会话中途的定稿（一句说完）直接上屏，继续听") {
            run(Listening("第一句"), DictationEvent.Final("第一句。")) shouldBe
                DictationTransition(Listening(""), listOf(Commit("第一句。")))
        }

        test("没有定稿就结束：已出的字照样上屏") {
            run(Finishing("半句"), DictationEvent.Completed) shouldBe
                DictationTransition(Idle, listOf(FinishComposing, EndSession))
        }

        test("没说话就结束：什么都不上屏") {
            run(Finishing(""), DictationEvent.Completed) shouldBe DictationTransition(Idle, listOf(EndSession))
        }

        test("打断（点其他键、切输入框、收键盘）：已识别的字立刻上屏，不等定稿") {
            run(Listening("我在"), DictationEvent.Interrupt) shouldBe DictationTransition(Idle, listOf(FinishComposing, EndSession))
            run(Finishing("我在"), DictationEvent.Interrupt) shouldBe DictationTransition(Idle, listOf(FinishComposing, EndSession))
            run(Listening(""), DictationEvent.Interrupt) shouldBe DictationTransition(Idle, listOf(EndSession))
        }

        test("打断会收起错误提示，空闲时打断什么都不做") {
            run(Failed("没有凭证"), DictationEvent.Interrupt) shouldBe DictationTransition(Idle)
            run(Idle, DictationEvent.Interrupt) shouldBe DictationTransition(Idle)
        }

        test("识别失败：保留已出的字，结束会话，显示错误后自动消失") {
            run(Listening("你好"), DictationEvent.Failure("连不上火山"), DictationEvent.ErrorDismissed) shouldBe
                DictationTransition(Idle, listOf(FinishComposing, EndSession, ScheduleErrorDismiss))
        }

        test("开始前就被拒（没开启、没凭证）：只显示错误") {
            run(Idle, DictationEvent.Failure("火山凭证还没填全")) shouldBe
                DictationTransition(Failed("火山凭证还没填全"), listOf(ScheduleErrorDismiss))
        }

        test("会话结束后迟到的事件被忽略") {
            for (event in listOf(
                DictationEvent.Partial("迟到"),
                DictationEvent.Final("迟到"),
                DictationEvent.Stop(StopReason.SILENCE),
                DictationEvent.Completed,
                DictationEvent.ErrorDismissed,
            )) {
                run(Idle, event) shouldBe DictationTransition(Idle)
            }
            run(Failed("x"), DictationEvent.Partial("迟到")) shouldBe DictationTransition(Failed("x"))
        }

        test("光标离开待定文字才算打断") {
            // our own composing update: cursor at its end
            DictationMachine.cursorLeftComposition(12, 12, 10, 12).shouldBeFalse()
            // nothing composing yet
            DictationMachine.cursorLeftComposition(3, 3, -1, -1).shouldBeFalse()
            // tapped elsewhere in the text
            DictationMachine.cursorLeftComposition(4, 4, 10, 12).shouldBeTrue()
            // moved inside the composing text
            DictationMachine.cursorLeftComposition(11, 11, 10, 12).shouldBeTrue()
            // selected something
            DictationMachine.cursorLeftComposition(10, 12, 10, 12).shouldBeTrue()
        }

        test("分段定稿后，后续结果去掉已上屏的部分") {
            val segmenter = TranscriptSegmenter()
            segmenter.partial("第一句") shouldBe "第一句"
            segmenter.final("第一句。") shouldBe "第一句。"
            segmenter.partial("第一句。第二") shouldBe "第二"
            segmenter.final("第一句。第二句。") shouldBe "第二句。"
            segmenter.partial("第一句。第二句。") shouldBe ""
            segmenter.reset()
            segmenter.partial("第一句。") shouldBe "第一句。"
        }

        test("服务端只发新一句（不带前文）也不会丢字") {
            val segmenter = TranscriptSegmenter()
            segmenter.final("第一句。") shouldBe "第一句。"
            segmenter.partial("第二") shouldBe "第二"
            segmenter.final("第二句。") shouldBe "第二句。"
        }
    })
