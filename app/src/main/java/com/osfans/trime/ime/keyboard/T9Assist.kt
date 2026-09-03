/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

/**
 * Helpers for nine-key (T9) pinyin schemas such as `t9` in rime-ice / oh-my-rime.
 *
 * Those schemas keep both letters and digits in the speller alphabet and derive a digit
 * spelling for every syllable (`derive/[abc]/2/` ...), so the raw input is a digit string
 * and every candidate carries its pinyin in the comment (`spelling_hints`). The UI side can
 * offer the distinct syllables of the candidates ("选拼音" on iOS) and, once one is chosen,
 * replace the digits it covers with letters: Rime then matches that syllable exactly and the
 * remaining digits keep their ambiguity. Letters and digits map one to one, so a syllable of
 * n letters always replaces n digits.
 */
object T9Assist {
    /** Raw input of a nine-key schema: letters (already chosen syllables) followed by digits. */
    fun isT9Input(raw: String): Boolean =
        raw.isNotEmpty() && raw.any { it.isDigit() } && raw.all { it.isLetterOrDigit() || it == '\'' }

    /** Letters at the start of the input, i.e. syllables already chosen. */
    fun fixedPrefix(raw: String): String = raw.takeWhile { !it.isDigit() }

    private const val TONED = "āáǎàōóǒòēéěèīíǐìūúǔùǖǘǚǜüńňǹḿ"
    private const val PLAIN = "aaaaooooeeeeiiiiuuuuvvvvvnnnm"

    /** Strip tone marks so a syllable can be typed back into the speller (ü becomes v). */
    fun normalize(syllable: String): String =
        buildString(syllable.length) {
            for (c in syllable.lowercase()) {
                val i = TONED.indexOf(c)
                append(if (i >= 0) PLAIN[i] else c)
            }
        }

    private fun syllables(comment: String): List<String> =
        comment
            .split(' ', '\'', ',', '/')
            .map { normalize(it.trim()) }
            .filter { s -> s.isNotEmpty() && s.all { it in 'a'..'z' } }

    /**
     * Distinct syllables the next unresolved digits could be, in candidate order.
     * [comments] are the candidate comments (pinyin) for the current input.
     */
    fun syllableChoices(
        raw: String,
        comments: List<String>,
    ): List<String> {
        val prefix = fixedPrefix(raw).filter { it.isLetter() }
        val out = LinkedHashSet<String>()
        for (comment in comments) {
            val syls = syllables(comment)
            if (syls.isEmpty()) continue
            // skip the syllables already fixed by the letter prefix
            var consumed = 0
            var i = 0
            while (i < syls.size && consumed < prefix.length) {
                consumed += syls[i].length
                i++
            }
            if (consumed != prefix.length || i >= syls.size) continue
            out += syls[i]
        }
        return out.toList()
    }

    /** New raw input after choosing [syllable] for the first unresolved digits. */
    fun applySyllable(
        raw: String,
        syllable: String,
    ): String {
        val prefix = fixedPrefix(raw)
        val rest = raw.substring(prefix.length)
        var digits = 0
        var idx = 0
        while (idx < rest.length && digits < syllable.length) {
            if (rest[idx].isDigit()) digits++
            idx++
        }
        return prefix + syllable + rest.substring(idx)
    }

    /**
     * Preedit text to show instead of the raw digits: the pinyin of the first candidate
     * ([comment]) for the digits it covers, followed by the digits it does not cover.
     */
    fun displayPreedit(
        preedit: String,
        comment: String,
    ): String {
        val syls = syllables(comment)
        if (syls.isEmpty()) return preedit
        val letters = syls.sumOf { it.length }
        val sb = StringBuilder()
        var covered = 0
        var idx = 0
        // keep any leading letters (already chosen syllables are part of the comment too)
        while (idx < preedit.length && covered < letters) {
            val c = preedit[idx]
            if (c.isLetterOrDigit()) covered++
            idx++
        }
        sb.append(syls.joinToString(" "))
        val rest = preedit.substring(idx).trimStart('\'', ' ')
        if (rest.isNotEmpty()) sb.append(' ').append(rest)
        return sb.toString()
    }
}
