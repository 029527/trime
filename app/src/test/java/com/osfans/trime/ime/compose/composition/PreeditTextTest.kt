/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.composition

import com.osfans.trime.core.CandidateProto
import com.osfans.trime.core.CompositionProto
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class PreeditTextTest :
    StringSpec({

        fun composition(
            preedit: String,
            selStart: Int = 0,
            selEnd: Int = preedit.codePointCount(0, preedit.length),
            cursorPos: Int = preedit.codePointCount(0, preedit.length),
        ) = CompositionProto(
            length = preedit.codePointCount(0, preedit.length),
            cursorPos = cursorPos,
            selStart = selStart,
            selEnd = selEnd,
            preedit = preedit,
        )

        fun candidate(
            text: String,
            comment: String = "",
        ) = CandidateProto(text, comment, "")

        "nothing composed gives null" {
            PreeditText.of(CompositionProto(), emptyArray()).shouldBeNull()
            PreeditText.of(CompositionProto(length = 0, preedit = ""), emptyArray()).shouldBeNull()
        }

        "plain pinyin keeps rime's text and highlight" {
            val shown = PreeditText.of(composition("ni hao", selStart = 3, selEnd = 6), arrayOf(candidate("你好")))!!
            shown.text shouldBe "ni hao"
            shown.selectionStart shouldBe 3
            shown.selectionEnd shouldBe 6
            shown.hasSelection shouldBe true
            shown.isT9 shouldBe false
        }

        "caret at the end is not drawn" {
            PreeditText.of(composition("ni hao"), emptyArray())!!.caret shouldBe -1
        }

        "caret inside the preedit is drawn" {
            PreeditText.of(composition("ni hao", cursorPos = 2), emptyArray())!!.caret shouldBe 2
        }

        "no extra caret when rime draws a soft cursor" {
            PreeditText.of(composition("ni‸ hao", cursorPos = 2), emptyArray())!!.caret shouldBe -1
        }

        "offsets are converted from code points to utf-16" {
            // 𠀀 is one code point but two UTF-16 units
            val shown = PreeditText.of(composition("𠀀hao", selStart = 1, selEnd = 4, cursorPos = 1), emptyArray())!!
            shown.selectionStart shouldBe 2
            shown.selectionEnd shouldBe 5
            shown.caret shouldBe 2
        }

        "out of range selection is clamped" {
            val shown = PreeditText.of(composition("nihao", selStart = 3, selEnd = 99), emptyArray())!!
            shown.selectionStart shouldBe 3
            shown.selectionEnd shouldBe 5
        }

        "nine-key shows the pinyin of the first commented candidate" {
            val shown =
                PreeditText.of(
                    composition("64 426"),
                    arrayOf(candidate("hello"), candidate("你好", "nǐ hǎo"), candidate("密码", "mi ma")),
                )!!
            shown.text shouldBe "ni hao"
            shown.isT9 shouldBe true
            shown.hasSelection shouldBe false
            shown.caret shouldBe -1
        }

        "nine-key keeps the digits the pinyin does not cover" {
            val shown = PreeditText.of(composition("64426"), arrayOf(candidate("你", "ni")))!!
            shown.text shouldBe "ni 426"
        }

        "nine-key without any pinyin falls back to the raw preedit" {
            val shown = PreeditText.of(composition("64426"), arrayOf(candidate("hello")))!!
            shown.text shouldBe "64426"
            shown.isT9 shouldBe false
        }

        "letters only is not nine-key" {
            PreeditText.of(composition("nihao"), arrayOf(candidate("你好", "ni hao")))!!.text shouldBe "nihao"
        }

        "caret position skips syllable delimiters" {
            PreeditText.caretPositionFor("ni hao", 0) shouldBe 0
            PreeditText.caretPositionFor("ni hao", 2) shouldBe 2
            PreeditText.caretPositionFor("ni hao", 3) shouldBe 2
            PreeditText.caretPositionFor("ni hao", 4) shouldBe 3
            PreeditText.caretPositionFor("ni hao", 6) shouldBe 5
        }

        "caret position skips the soft cursor" {
            PreeditText.caretPositionFor("ni‸hao", 4) shouldBe 3
        }

        "caret position counts converted hanzi by utf-8 size" {
            PreeditText.caretPositionFor("你hao", 1) shouldBe 3
            PreeditText.caretPositionFor("你hao", 2) shouldBe 4
        }

        "caret position clamps the offset" {
            PreeditText.caretPositionFor("nihao", -3) shouldBe 0
            PreeditText.caretPositionFor("nihao", 42) shouldBe 5
        }
    })
