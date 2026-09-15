/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.edit

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EditKeysTest :
    FunSpec({
        test("plain keys move the cursor") {
            EditKey.entries.map { it.token(selecting = false, composing = false) } shouldBe
                listOf("Up", "Down", "Left", "Right", "Home", "End", "Tab")
        }

        test("select mode adds Shift so the editor extends the selection") {
            EditKey.entries.map { it.token(selecting = true, composing = false) } shouldBe
                listOf("Shift+Up", "Shift+Down", "Shift+Left", "Shift+Right", "Shift+Home", "Shift+End", "Tab")
        }

        test("a composition keeps plain keys, which move the caret inside the preedit") {
            EditKey.entries.forEach {
                it.token(selecting = true, composing = true) shouldBe it.keysym
                it.token(selecting = false, composing = true) shouldBe it.keysym
            }
        }

        test("only the arrows repeat while held") {
            EditKey.entries.filter { it.repeatable } shouldBe listOf(EditKey.Up, EditKey.Down, EditKey.Left, EditKey.Right)
        }

        test("select all turns select mode on, moving text turns it off") {
            EditCommand.SelectAll.selectingAfter shouldBe true
            EditCommand.Cut.selectingAfter shouldBe false
            EditCommand.Copy.selectingAfter shouldBe false
            EditCommand.Paste.selectingAfter shouldBe false
        }
    })
