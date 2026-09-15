/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.builtin.BuiltinTheme
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.junit.Assert.assertEquals
import java.io.File

/**
 * `assets/ios.trime.yaml` is the theme Rime deployed on the device (`build/ios.trime.yaml`).
 * Decoding it the way the app used to must give exactly [BuiltinTheme.theme].
 */
class BuiltinThemeEquivalenceTest :
    FunSpec({
        val decoded by lazy {
            val node = Yaml.parseToYamlNode(File("src/test/assets/ios.trime.yaml").readText())
            Theme.decode(node.mapping!!)
        }
        val builtin = BuiltinTheme.theme

        test("style / preedit / window") {
            builtin.name shouldBe decoded.name
            builtin.generalStyle shouldBe decoded.generalStyle
            builtin.preedit shouldBe decoded.preedit
            builtin.window shouldBe decoded.window
            builtin.toolBar shouldBe decoded.toolBar
        }

        test("colour schemes") {
            builtin.colorSchemes shouldBe decoded.colorSchemes
            builtin.fallbackColors shouldBe decoded.fallbackColors
        }

        test("preset keys") {
            builtin.presetKeys.keys shouldBe decoded.presetKeys.keys
            decoded.presetKeys.forEach { (id, key) -> (id to builtin.presetKeys[id]) shouldBe (id to key) }
        }

        test("keyboards") {
            builtin.presetKeyboards.keys shouldBe decoded.presetKeyboards.keys
            decoded.presetKeyboards.forEach { (id, keyboard) ->
                val mine = builtin.presetKeyboards.getValue(id)
                (id to mine.copy(keys = emptyList())) shouldBe (id to keyboard.copy(keys = emptyList()))
                mine.keys.size shouldBe keyboard.keys.size
                keyboard.keys.forEachIndexed { i, key -> ("$id[$i]" to mine.keys[i]) shouldBe ("$id[$i]" to key) }
            }
        }

        test("liquid keyboard") {
            builtin.liquidKeyboard.copy(keyboards = emptyList()) shouldBe decoded.liquidKeyboard.copy(keyboards = emptyList())
            builtin.liquidKeyboard.keyboards.map { it.id } shouldBe decoded.liquidKeyboard.keyboards.map { it.id }
            decoded.liquidKeyboard.keyboards.zip(builtin.liquidKeyboard.keyboards).forEach { (d, b) -> b shouldBe d }
        }

        test("the whole theme is equal") {
            assertEquals(decoded, builtin)
        }
    })
