/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.postprocess

import com.osfans.trime.voice.llm.CorrectionOutputValidator
import com.osfans.trime.voice.llm.LlmCorrectionException
import com.osfans.trime.voice.llm.OpenAiCompatibleCorrector
import com.osfans.trime.voice.llm.TranscriptCorrector
import com.osfans.trime.voice.vocab.VoiceVocabulary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * 识别结果上屏前的一道同步加工（比如映射词）。中间结果和最终结果都会过一遍，所以必须快。
 *
 * 耗时的加工（LLM 纠错）不走这里，走 [TranscriptPipeline.correct]。
 */
interface TranscriptProcessor {
    val name: String

    /**
     * @param text 上一道处理器的输出
     * @param isFinal 中间结果为 false，最终结果为 true
     */
    fun process(
        text: String,
        isFinal: Boolean,
    ): String
}

/** [TranscriptPipeline.correct] 的结果。[text] 永远是能直接上屏的文字。 */
sealed interface CorrectionOutcome {
    val text: String

    /** 纠错成功，[text] 是校验通过、又过了一遍同步处理器的结果。 */
    data class Corrected(
        override val text: String,
    ) : CorrectionOutcome

    /** 模型回了，但结果没过校验；[text] 是原文。不打扰用户，只记原因。 */
    data class Rejected(
        override val text: String,
        val reason: CorrectionOutputValidator.Rejection,
    ) : CorrectionOutcome

    /** 请求失败或超时；[text] 是原文，[message] 给胶囊显示。 */
    data class Failed(
        override val text: String,
        val message: String,
    ) : CorrectionOutcome
}

/**
 * 识别结果的加工链。
 *
 * - 中间结果：[process]，只过同步处理器，字照样边说边出；
 * - 最终结果：先 [process]，再 [correct] 交给 LLM 纠错，纠错结果**再过一遍** [process]
 *   （模型可能把映射好的词又改回去）。
 *
 * 规则只有一条：**任何一步出问题都退回原文**，加工失败绝不影响上屏。
 */
class TranscriptPipeline(
    private val processors: List<TranscriptProcessor>,
) {
    /** 按顺序把文本喂给每一道处理器。任何一道抛异常都跳过它。 */
    fun process(
        text: String,
        isFinal: Boolean,
    ): String = processors.fold(text) { acc, processor ->
        runCatching { processor.process(acc, isFinal) }.getOrDefault(acc)
    }

    /**
     * 让 [corrector] 纠正 [text]（已经过了 [process] 的最终结果），最多等 [timeoutMs]。
     *
     * 失败、超时、结果没过 [CorrectionOutputValidator] 都返回原文；**只有取消会往外抛**
     * （[CancellationException]），调用方被打断时自己负责把原文上屏。
     */
    suspend fun correct(
        text: String,
        corrector: TranscriptCorrector,
        timeoutMs: Long = OpenAiCompatibleCorrector.TIMEOUT_MS,
    ): CorrectionOutcome {
        val output = try {
            withTimeout(timeoutMs) { corrector.correct(text) }
        } catch (e: TimeoutCancellationException) {
            return CorrectionOutcome.Failed(text, "纠错超时，已保留原文")
        } catch (e: CancellationException) {
            throw e
        } catch (e: LlmCorrectionException) {
            return CorrectionOutcome.Failed(text, "${e.message}，已保留原文")
        } catch (e: Exception) {
            return CorrectionOutcome.Failed(text, "纠错出错，已保留原文")
        }
        return when (val verdict = CorrectionOutputValidator.validate(text, output)) {
            is CorrectionOutputValidator.Verdict.Accepted -> CorrectionOutcome.Corrected(process(verdict.text, isFinal = true))
            is CorrectionOutputValidator.Verdict.Rejected -> CorrectionOutcome.Rejected(text, verdict.reason)
        }
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
