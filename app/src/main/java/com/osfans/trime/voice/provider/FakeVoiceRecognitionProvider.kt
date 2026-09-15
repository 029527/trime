/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.provider

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 假识别提供方：不连网络，按固定节奏吐几段假的中间结果，音频流结束后再补一个最终结果。
 *
 * 存在的理由是**没有真凭证也要能验整条链路**：点麦克风 → 出字 → 自动停 / 再点一下 → 映射替换 → 上屏。
 * 只在 debug 包的「开发者 → 模拟识别」打开时使用；配「模拟麦克风」时连录音权限都不要。
 *
 * 它照样 collect 传进来的那条流：管理器在结束听写时会让这条流正常结束，
 * 所以这里能感知到说完了，跟真的识别一样立刻收尾。
 *
 * 文案里带了 `Queen 3.5` 和 `克劳德`，跟词库模板里的映射词对得上，
 * 一眼就能看出映射有没有生效。
 */
class FakeVoiceRecognitionProvider : VoiceRecognitionProvider {
    override val name = "fake"

    override fun recognize(audio: Flow<ByteArray>): Flow<VoiceRecognitionEvent> = channelFlow {
        val speechEnded = AtomicBoolean(false)
        val gate = launch {
            audio.collect { }
            speechEnded.set(true)
        }

        var lastText = ""
        for ((delayMs, text) in SCRIPT) {
            if (speechEnded.get()) break
            delay(delayMs)
            if (speechEnded.get()) break
            lastText = text
            send(VoiceRecognitionEvent.Partial(text))
        }
        // 脚本念完了也不自己收尾：跟真服务端一样一直听，等管理器结束音频流（再点一下、静音自动停）
        gate.join()

        // 说完之后还要等一下「服务端定稿」，这段时间胶囊和麦克风键还亮着
        delay(400)
        send(VoiceRecognitionEvent.Final(lastText.ifEmpty { SCRIPT.first().second }))
        send(VoiceRecognitionEvent.Completed)
    }.buffer(Channel.UNLIMITED, BufferOverflow.SUSPEND)

    companion object {
        /** (等多久, 这一刻的完整假设)。中间故意插了一次"修订"，验证是替换而不是追加。 */
        private val SCRIPT = listOf(
            500L to "我用",
            400L to "我用亲",
            400L to "我用 Queen",
            400L to "我用 Queen 3.5",
            500L to "我用 Queen 3.5 和克劳德",
            500L to "我用 Queen 3.5 和克劳德写带马",
            400L to "我用 Queen 3.5 和克劳德写代码",
        )
    }
}
