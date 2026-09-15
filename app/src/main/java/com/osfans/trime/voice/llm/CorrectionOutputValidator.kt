/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.llm

import kotlin.math.max

/**
 * 检查模型的纠错结果能不能用。不能用就上屏原文 —— **宁可不改，也不能改坏**。
 *
 * 先 [sanitize] 掉常见的包装（推理块、代码块、整段引号、回显的标签），再按下面几条拒绝：
 *
 * - 空的；
 * - 长度变化太大：纠错只改错字和标点，长度应该差不多。多出 `max(8, 50%)` 或少了 `max(3, 30%)`
 *   个字（不算空白）都算改写或者答非所问。短文本给固定余量，免得补个标点就被拒；
 * - 开头是「以下是……」「纠正后：」「Here is」这类说明，或者多出了「说明：」「注：」段落；
 * - 文字种类变了：原文有汉字而结果没有（被翻译了），或者反过来。
 */
object CorrectionOutputValidator {

    enum class Rejection { EMPTY, TOO_LONG, TOO_SHORT, PREAMBLE, EXPLANATION, SCRIPT_CHANGED }

    sealed interface Verdict {
        data class Accepted(
            val text: String,
        ) : Verdict

        data class Rejected(
            val reason: Rejection,
        ) : Verdict
    }

    const val GROWTH_RATIO = 0.5
    const val GROWTH_SLACK = 8
    const val SHRINK_RATIO = 0.3
    const val SHRINK_SLACK = 3

    private val QUOTE_PAIRS = listOf("\"" to "\"", "'" to "'", "“" to "”", "‘" to "’", "「" to "」", "『" to "』", "`" to "`")

    private val PREAMBLE = Regex(
        "^(以下是|下面是|这是|这里是|修正后|纠正后|校对后|校正后|更正后|修改后|纠错后|改正后|输出[:：]|结果[:：]|" +
            "(?i:here is|here's|corrected|the corrected|sure[,!.]|okay[,!.]))",
    )

    private val EXPLANATION = Regex("(^|\\n)\\s*[（(]?(说明|注|备注|解释|修改说明|Note|Explanation)[:：]", RegexOption.IGNORE_CASE)

    private val CODE_FENCE = Regex("^```[^\\n]*\\n([\\s\\S]*?)\\n?```$")

    fun validate(
        input: String,
        output: String,
    ): Verdict {
        val text = sanitize(input, output)
        if (text.isBlank()) return Verdict.Rejected(Rejection.EMPTY)

        val source = input.trim()
        if (PREAMBLE.containsMatchIn(text) && !PREAMBLE.containsMatchIn(source)) {
            return Verdict.Rejected(Rejection.PREAMBLE)
        }
        if (EXPLANATION.containsMatchIn(text) && !EXPLANATION.containsMatchIn(source)) {
            return Verdict.Rejected(Rejection.EXPLANATION)
        }

        // 先查文字种类：被翻译的结果通常长短也变了，但「被翻译」才是真正的原因
        val sourceHan = source.any(::isHan)
        val textHan = text.any(::isHan)
        if (sourceHan != textHan) return Verdict.Rejected(Rejection.SCRIPT_CHANGED)

        val n = visibleLength(source)
        val m = visibleLength(text)
        if (m - n > max(GROWTH_SLACK, (n * GROWTH_RATIO).toInt())) return Verdict.Rejected(Rejection.TOO_LONG)
        if (n - m > max(SHRINK_SLACK, (n * SHRINK_RATIO).toInt())) return Verdict.Rejected(Rejection.TOO_SHORT)

        return Verdict.Accepted(text)
    }

    /** 剥掉模型常加的包装。每一层都只在原文自己没有这层包装时才剥。 */
    fun sanitize(
        input: String,
        output: String,
    ): String {
        val source = input.trim()
        var text = ChatCompletionProtocol.stripThinking(output)
        CODE_FENCE.matchEntire(text)?.let { text = it.groupValues[1].trim() }
        if (!source.contains("<transcript>")) {
            text = text.removePrefix("<transcript>").removeSuffix("</transcript>").trim()
        }
        for ((open, close) in QUOTE_PAIRS) {
            val wrapped = text.length >= open.length + close.length + 1 && text.startsWith(open) && text.endsWith(close)
            if (wrapped && !(source.startsWith(open) && source.endsWith(close))) {
                text = text.substring(open.length, text.length - close.length).trim()
                break
            }
        }
        return text
    }

    private fun visibleLength(text: String): Int = text.codePoints().filter { !Character.isWhitespace(it) }.count().toInt()

    private fun isHan(c: Char): Boolean = Character.UnicodeScript.of(c.code) == Character.UnicodeScript.HAN
}
