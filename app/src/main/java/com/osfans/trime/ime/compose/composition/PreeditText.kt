/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.composition

import androidx.compose.runtime.Immutable
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.ime.keyboard.T9Assist

/**
 * What the preedit bar shows for one composition.
 *
 * All offsets are UTF-16 indices into [text], ready for Compose text APIs. librime reports
 * them in code points; they differ only when the preedit carries characters outside the BMP.
 */
@Immutable
data class PreeditText(
    val text: String,
    /** Highlighted range, `selectionStart until selectionEnd`; empty when nothing is highlighted. */
    val selectionStart: Int,
    val selectionEnd: Int,
    /** Where to draw a caret, or -1 for none (caret at the end, or the preedit draws its own). */
    val caret: Int,
    /** Nine-key hint: the pinyin of the first candidate, shown smaller and without highlight. */
    val isT9: Boolean,
) {
    val hasSelection: Boolean get() = selectionStart < selectionEnd

    companion object {
        /** The glyph librime's `soft_cursor` option puts into the preedit. */
        const val SOFT_CURSOR = '‸'

        /**
         * Builds the preedit to show, or `null` when there is nothing being composed.
         *
         * For nine-key schemas the raw preedit is a digit string; show the pinyin of the
         * first candidate that carries one instead, like iOS does (hot words and English
         * words have no comment, so they are skipped).
         */
        fun of(
            composition: CompositionProto,
            candidates: Array<CandidateProto>,
        ): PreeditText? {
            val preedit = composition.preedit
            if (composition.length <= 0 || preedit.isNullOrEmpty()) return null
            if (T9Assist.isT9Input(T9Assist.stripPreedit(preedit))) {
                val comment = candidates.firstOrNull { it.comment.isNotEmpty() }?.comment
                if (comment != null) {
                    val text = T9Assist.displayPreedit(preedit, comment)
                    return PreeditText(text, text.length, text.length, caret = -1, isT9 = true)
                }
            }
            val selStart = preedit.utf16Index(composition.selStart)
            val selEnd = preedit.utf16Index(composition.selEnd).coerceAtLeast(selStart)
            val cursor = preedit.utf16Index(composition.cursorPos)
            val caret = if (cursor >= preedit.length || SOFT_CURSOR in preedit) -1 else cursor
            return PreeditText(preedit, selStart, selEnd, caret, isT9 = false)
        }

        /**
         * The caret position to hand to librime for a tap before [offset] (UTF-16) of [text].
         *
         * `set_caret_pos` counts bytes of the raw input, while the preedit adds syllable
         * delimiters and possibly a soft cursor; those are not part of the input and are
         * skipped. Letters and digits map one to one, including the nine-key pinyin hint.
         * Already converted segments (hanzi) cannot be mapped back exactly; they count by
         * their UTF-8 size, as before.
         */
        fun caretPositionFor(
            text: String,
            offset: Int,
        ): Int {
            val end = offset.coerceIn(0, text.length)
            var bytes = 0
            var i = 0
            while (i < end) {
                val cp = text.codePointAt(i)
                i += Character.charCount(cp)
                if (cp == ' '.code || cp == SOFT_CURSOR.code) continue
                bytes += utf8Size(cp)
            }
            return bytes
        }

        private fun utf8Size(cp: Int) = when {
            cp < 0x80 -> 1
            cp < 0x800 -> 2
            cp < 0x10000 -> 3
            else -> 4
        }

        private fun String.utf16Index(codePoints: Int): Int {
            if (codePoints <= 0) return 0
            return runCatching { offsetByCodePoints(0, codePoints) }.getOrDefault(length)
        }
    }
}
