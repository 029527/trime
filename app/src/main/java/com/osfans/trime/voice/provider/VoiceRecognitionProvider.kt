/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.provider

import kotlinx.coroutines.flow.Flow

/** 识别过程中吐出来的事件。 */
sealed interface VoiceRecognitionEvent {
    /** 中间结果：完整的当前假设，不是增量。上屏走 `setComposingText`。 */
    data class Partial(
        val text: String,
    ) : VoiceRecognitionEvent

    /** 最终结果：这一整段说完了的定稿。 */
    data class Final(
        val text: String,
    ) : VoiceRecognitionEvent

    /**
     * 出错了。[message] 是给用户看的中文说明，
     * [recoverable] 为 false 表示重试也没用（鉴权失败、额度用完）。
     */
    data class Failure(
        val message: String,
        val recoverable: Boolean = true,
        val cause: Throwable? = null,
    ) : VoiceRecognitionEvent

    /** 会话正常结束，不会再有事件了。 */
    data object Completed : VoiceRecognitionEvent
}

/**
 * 一个识别提供方。
 *
 * [recognize] 收一条 PCM 流、吐一条事件流；两条都是冷流，
 * 调用方取消协程就等于结束会话。
 */
interface VoiceRecognitionProvider {
    val name: String

    /** 为 false 时管理器不去开麦克风，也就不需要 `RECORD_AUDIO`（假识别提供方就是这样）。 */
    val requiresAudio: Boolean get() = true

    /** 配置不全时返回一句给用户看的原因；返回 null 表示可用。 */
    fun unavailableReason(): String? = null

    fun recognize(audio: Flow<ByteArray>): Flow<VoiceRecognitionEvent>
}
