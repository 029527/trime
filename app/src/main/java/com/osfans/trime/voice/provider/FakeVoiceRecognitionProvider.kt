/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.provider

import com.osfans.trime.voice.audio.VoiceAudioSource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 假识别提供方：不连网络，按固定节奏吐几段假的中间结果，松手后再补一个最终结果。
 *
 * 存在的理由是**没有真凭证也要能验整条链路**：按住 → 出字 → 松手 → 映射替换 → 上屏。
 * 故意 [requiresAudio] = false，所以在没有麦克风、没给录音权限的模拟器上也能跑。
 *
 * 它照样 collect 传进来的那条流：管理器在"松手"时会让这条流正常结束，
 * 所以这里能感知到用户松手，跟真的识别一样立刻收尾。
 *
 * 文案里带了 `Queen 3.5` 和 `克劳德`，跟词库模板里的映射词对得上，
 * 一眼就能看出映射有没有生效。
 */
class FakeVoiceRecognitionProvider : VoiceRecognitionProvider {
    override val name = "fake"

    /**
     * 给了录音权限就**真的开麦克风**（走一遍 `AudioRecord` 的采集和释放，只是把音频扔了），
     * 没给就完全不碰麦克风。
     *
     * 这样一个开关同时覆盖两种验证：没有权限、没有麦克风的模拟器上也能把
     * 「按住 → 出字 → 松手 → 映射替换 → 上屏」整条链路点出来；
     * 有权限时又能顺带验证采集和释放没写错。
     */
    override val requiresAudio = VoiceAudioSource.hasPermission()

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
        gate.cancel()

        // 松手之后还要等一下"服务端定稿"，这段时间界面上是"识别中……"
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
