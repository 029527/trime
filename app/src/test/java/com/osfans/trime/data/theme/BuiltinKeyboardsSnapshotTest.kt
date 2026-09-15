/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.builtin.BuiltinTheme
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Pins what every key of every built-in keyboard does and says: rows, widths, heights, labels,
 * hints and all action slots. Visual work (colours, shapes, fonts, icons drawn for an `ic@` name)
 * must leave this untouched.
 *
 * The expected text lives in `src/test/resources/builtin-keyboards.snapshot.txt`. After an
 * intended layout change, regenerate it with `UPDATE_SNAPSHOT=1 ./gradlew :app:testDebugUnitTest`.
 */
class BuiltinKeyboardsSnapshotTest :
    FunSpec({
        test("key layouts match the snapshot") {
            val actual = render()
            val file = File("src/test/resources/builtin-keyboards.snapshot.txt")
            if (System.getenv("UPDATE_SNAPSHOT") == "1") {
                file.parentFile?.mkdirs()
                file.writeText(actual)
            }
            actual shouldBe file.readText()
        }
    })

private fun render(): String = buildString {
    BuiltinTheme.theme.presetKeyboards.toSortedMap().forEach { (id, keyboard) ->
        appendLine("# $id width=${keyboard.width} height=${keyboard.height} ascii=${keyboard.asciiKeyboard} keys=${keyboard.keys.size}")
        keyboard.keys.forEachIndexed { i, key ->
            append("$i w=${key.width} h=${key.height} label=${key.label.quoted()} symbol=${key.labelSymbol.quoted()} hint=${key.hint.quoted()}")
            if (key.hideInLandscape) append(" hideInLandscape")
            if (key.popup.isNotEmpty()) append(" popup=${key.popup}")
            key.behaviors.forEach { (behavior, token) -> append(" ${behavior.name}=$token") }
            appendLine()
        }
    }
    BuiltinTheme.theme.presetKeys.toSortedMap().forEach { (id, preset) ->
        appendLine("preset $id label=${preset.label.quoted()} send=${preset.send.quoted()} command=${preset.command.quoted()} option=${preset.option.quoted()} select=${preset.select.quoted()}")
    }
}

private fun String.quoted() = "\"" + replace("\n", "\\n") + "\""
