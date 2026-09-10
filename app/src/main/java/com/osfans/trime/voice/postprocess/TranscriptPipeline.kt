/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.postprocess

import com.osfans.trime.voice.vocab.VoiceVocabulary

/**
 * 识别结果上屏前的一道加工。
 *
 * 做成可插拔的一串是为了下一期：接 LLM 纠错/润色时只是再加一个实现，
 * 不用动上屏那条链路。
 */
interface TranscriptProcessor {
    val name: String

    /**
     * @param text 上一道处理器的输出
     * @param isFinal 中间结果为 false，最终结果为 true。
     *   耗时的处理器（比如以后的 LLM）应该只在 `isFinal` 时干活，中间结果原样返回。
     */
    fun process(
        text: String,
        isFinal: Boolean,
    ): String
}

/** 按顺序把文本喂给每一道处理器。任何一道抛异常都跳过它，不影响上屏。 */
class TranscriptPipeline(
    private val processors: List<TranscriptProcessor>,
) {
    fun process(
        text: String,
        isFinal: Boolean,
    ): String = processors.fold(text) { acc, processor ->
        runCatching { processor.process(acc, isFinal) }.getOrDefault(acc)
    }

    companion object {
        fun of(vararg processors: TranscriptProcessor) = TranscriptPipeline(processors.toList())
    }
}

/**
 * 第一道处理器：本地映射词兜底。
 *
 * 中间结果也替换，这样边说边出的字就已经是对的，不会在松手那一刻突然跳变。
 * 词库由 [vocabularyProvider] 每次现取，所以「重新加载词库」立刻生效。
 */
class VocabularyMappingProcessor(
    private val vocabularyProvider: () -> VoiceVocabulary,
) : TranscriptProcessor {
    override val name = "vocabulary-mapping"

    override fun process(
        text: String,
        isFinal: Boolean,
    ): String = vocabularyProvider().replace(text)
}
