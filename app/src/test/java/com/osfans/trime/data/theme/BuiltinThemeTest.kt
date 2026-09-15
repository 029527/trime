/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.builtin.BuiltinColors
import com.osfans.trime.data.theme.builtin.BuiltinTheme
import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.ime.edit.EditCommand
import com.osfans.trime.ime.edit.EditKey
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * Sanity checks on the hand-edited [BuiltinTheme]. `KeyActionManager` itself needs the
 * Android runtime, so references are checked the way it resolves them: a plain token longer
 * than one character names a preset key (or is a keypad keysym), `select:` names a keyboard.
 */
class BuiltinThemeTest :
    FunSpec({
        val theme = BuiltinTheme.theme

        test("ships the keyboards schemas and keys switch to") {
            theme.presetKeyboards.keys shouldContainAll
                listOf("default", "english", "symbols", "symbols2", "symbols_en", "symbols2_en", "number", "t9", "t9_land")
        }

        test("one light and one dark colour scheme") {
            theme.colorSchemes.map { it.id } shouldContainExactlyInAnyOrder listOf(BuiltinColors.LIGHT_SCHEME, BuiltinColors.DARK_SCHEME)
            DayNightMigration.isBuiltinDarkScheme(BuiltinColors.LIGHT_SCHEME) shouldBe false
            DayNightMigration.isBuiltinDarkScheme(BuiltinColors.DARK_SCHEME) shouldBe true
            theme.colorSchemes[0].colors.keys shouldBe theme.colorSchemes[1].colors.keys
        }

        test("key actions refer to preset keys that exist") {
            val keypad = Regex("KP_\\d")
            val missing =
                theme.presetKeyboards.flatMap { (id, keyboard) ->
                    keyboard.keys.flatMap { key ->
                        key.behaviors.values
                            .filterIsInstance<KeyActionToken.Plain>()
                            .map { it.token }
                            .filter { it.length > 1 && !keypad.matches(it) && it !in theme.presetKeys }
                            .map { "$id: $it" }
                    }
                }
            missing shouldBe emptyList()
            theme.liquidKeyboard.fixedKeyBar.keys.filterNot { it in theme.presetKeys } shouldBe emptyList()
        }

        test("the iOS preset keys switch to keyboards and panels that exist") {
            val keyboards = theme.presetKeyboards.keys
            theme.presetKeys
                .filterKeys { it.startsWith("ios_") }
                .values
                .map { it.select }
                .filter { it.isNotEmpty() && !it.startsWith(".") }
                .filterNot { it in keyboards } shouldBe emptyList()
            theme.presetKeyboards.values
                .map { it.asciiKeyboard }
                .filter { it.isNotEmpty() }
                .filterNot { it in keyboards } shouldBe emptyList()
            val panels = theme.liquidKeyboard.keyboards.map { it.id }
            theme.presetKeys.values
                .filter { it.command == "liquid_keyboard" && it.option in listOf("emoji", "yanwenzi") }
                .map { it.option }
                .filterNot { it in panels } shouldBe emptyList()
        }

        test("toolbar buttons and edit panel keys refer to preset keys that exist") {
            val toolBar = theme.toolBar
            (listOfNotNull(toolBar.primaryButton) + toolBar.buttons)
                .map { it.action }
                .filterNot { it in theme.presetKeys } shouldBe emptyList()
            (EditKey.entries.map { it.keysym } + EditCommand.entries.map { it.presetKey })
                .filterNot { it in theme.presetKeys } shouldBe emptyList()
            theme.presetKeys.getValue("edit_panel").command shouldBe "edit_panel"
        }

        test("the style names no font files: the font is built in") {
            with(theme.generalStyle) {
                listOf(candidateFont, commentFont, keyFont, labelFont, popupFont, symbolFont, textFont, hanbFont, latinFont)
                    .flatten() shouldBe emptyList()
            }
        }
    })
