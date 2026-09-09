/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import android.icu.text.BreakIterator

object NativeTokenizer {
    /**
     * Utilize Android native ICU engine ([android.icu.text.BreakIterator], API 24+) to tokenize text
     *
     * @param text original text
     * @param filterBlank filter the whitespace characters
     */
    fun tokenize(text: String, filterBlank: Boolean = true): List<String> {
        if (text.isEmpty()) return emptyList()
        val words = mutableListOf<String>()

        val iterator = BreakIterator.getWordInstance().apply { setText(text) }
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val word = text.substring(start, end)
            if (!filterBlank || word.isNotBlank()) words.add(word)
            start = end
            end = iterator.next()
        }
        return words
    }
}
