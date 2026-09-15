/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.builtin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class KeyboardColorRolesTest :
    FunSpec({
        val neutral = mapOf("light" to KeyboardColorRoles.NeutralLight, "dark" to KeyboardColorRoles.NeutralDark)

        test("text is readable on what it is drawn on (WCAG AA for keys, 3:1 for secondary text)") {
            neutral.forEach { (id, r) ->
                withClue(id, "key text") { contrast(r.onSurface, r.key) shouldBeGreaterThanOrEqual 4.5 }
                withClue(id, "function key text") { contrast(r.onFunctionKey, r.functionKey) shouldBeGreaterThanOrEqual 4.5 }
                withClue(id, "enter key text") { contrast(r.onAccent, r.accent) shouldBeGreaterThanOrEqual 4.5 }
                withClue(id, "candidate on bar") { contrast(r.onSurface, r.surface) shouldBeGreaterThanOrEqual 4.5 }
                withClue(id, "comment on bar") { contrast(r.onSurfaceVariant, r.surface) shouldBeGreaterThanOrEqual 3.0 }
                withClue(id, "pressed key text") {
                    contrast(r.onSurface, KeyboardColorRoles.pressed(r.key, r.onSurface)) shouldBeGreaterThanOrEqual 4.5
                }
            }
        }

        test("letter keys, function keys and the surface are three different tones") {
            neutral.forEach { (id, r) ->
                withClue(id, "key vs surface") { r.key shouldNotBe r.surface }
                withClue(id, "function key vs key") { r.functionKey shouldNotBe r.key }
                withClue(id, "function key vs surface") { r.functionKey shouldNotBe r.surface }
            }
        }

        test("pressed is a 12% state layer and keeps the base opaque") {
            KeyboardColorRoles.pressed(0xFFFFFFFF.toInt(), 0xFF000000.toInt()) shouldBe 0xFFE0E0E0.toInt()
            KeyboardColorRoles.pressed(0xFF000000.toInt(), 0xFFFFFFFF.toInt()) shouldBe 0xFF1F1F1F.toInt()
        }

        test("opaque colours are written without alpha, so the opacity slider applies") {
            KeyboardColorRoles.hex(0xFF18181B.toInt()) shouldBe "0x18181B"
            KeyboardColorRoles.hex(0x8018181B.toInt()) shouldBe "0x8018181B"
        }

        test("the two built-in schemes have the same keys") {
            KeyboardColorRoles.NeutralLight.colors("a").keys shouldBe KeyboardColorRoles.NeutralDark.colors("b").keys
        }
    })

private inline fun withClue(
    scheme: String,
    what: String,
    block: () -> Unit,
) = io.kotest.assertions.withClue("$scheme: $what") { block() }

private fun luminance(color: Int): Double {
    fun channel(shift: Int): Double {
        val c = ((color ushr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

private fun contrast(
    a: Int,
    b: Int,
): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}
