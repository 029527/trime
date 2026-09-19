/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PrintableKeysymTest :
    FunSpec({
        test("regular ascii characters are converted to string") {
            TrimeInputMethodService.keysymToPrintableText(0x20) shouldBe " "
            TrimeInputMethodService.keysymToPrintableText(0x30) shouldBe "0"
            TrimeInputMethodService.keysymToPrintableText(0x39) shouldBe "9"
            TrimeInputMethodService.keysymToPrintableText(0x41) shouldBe "A"
            TrimeInputMethodService.keysymToPrintableText(0x61) shouldBe "a"
            TrimeInputMethodService.keysymToPrintableText(0x7e) shouldBe "~"
        }

        test("numpad digits KP_0 to KP_9 are converted to 0 to 9") {
            for (i in 0..9) {
                val keysym = 0xffb0 + i
                TrimeInputMethodService.keysymToPrintableText(keysym) shouldBe i.toString()
            }
        }

        test("numpad arithmetic operators and symbols are converted correctly") {
            TrimeInputMethodService.keysymToPrintableText(0xffae) shouldBe "." // KP_Decimal
            TrimeInputMethodService.keysymToPrintableText(0xffaf) shouldBe "/" // KP_Divide
            TrimeInputMethodService.keysymToPrintableText(0xffaa) shouldBe "*" // KP_Multiply
            TrimeInputMethodService.keysymToPrintableText(0xffad) shouldBe "-" // KP_Subtract
            TrimeInputMethodService.keysymToPrintableText(0xffab) shouldBe "+" // KP_Add
            TrimeInputMethodService.keysymToPrintableText(0xffbd) shouldBe "=" // KP_Equal
            TrimeInputMethodService.keysymToPrintableText(0xff80) shouldBe " " // KP_Space
        }

        test("non-printable and control keys return null") {
            TrimeInputMethodService.keysymToPrintableText(0x1f) shouldBe null
            TrimeInputMethodService.keysymToPrintableText(0x7f) shouldBe null
            TrimeInputMethodService.keysymToPrintableText(0xff8d) shouldBe null // KP_Enter
            TrimeInputMethodService.keysymToPrintableText(0xff08) shouldBe null // BackSpace
            TrimeInputMethodService.keysymToPrintableText(0xff0d) shouldBe null // Return
            TrimeInputMethodService.keysymToPrintableText(0xff52) shouldBe null // Up
            TrimeInputMethodService.keysymToPrintableText(0) shouldBe null
        }
    })
