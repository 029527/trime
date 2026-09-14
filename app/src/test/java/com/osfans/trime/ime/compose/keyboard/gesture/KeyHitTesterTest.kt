/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.keyboard.gesture

import com.osfans.trime.ime.compose.keyboard.gesture.KeyTouchBox.Companion.EDGE_BOTTOM
import com.osfans.trime.ime.compose.keyboard.gesture.KeyTouchBox.Companion.EDGE_LEFT
import com.osfans.trime.ime.compose.keyboard.gesture.KeyTouchBox.Companion.EDGE_RIGHT
import com.osfans.trime.ime.compose.keyboard.gesture.KeyTouchBox.Companion.EDGE_TOP
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class KeyHitTesterTest :
    StringSpec({

        // Row 0: [0,100) [100,200) [200,300)   Row 1: a 40px spacer, then [40,140) [140,240) and 60px of nothing.
        val boxes =
            listOf(
                KeyTouchBox(0, 0, 100, 100, EDGE_LEFT or EDGE_TOP),
                KeyTouchBox(100, 0, 200, 100, EDGE_TOP),
                KeyTouchBox(200, 0, 300, 100, EDGE_TOP),
                KeyTouchBox(40, 100, 140, 200, EDGE_LEFT or EDGE_BOTTOM),
                KeyTouchBox(140, 100, 240, 200, EDGE_RIGHT or EDGE_BOTTOM),
            )
        val all: (Int, Int) -> IntArray = { _, _ -> boxes.indices.toList().toIntArray() }
        val none: (Int, Int) -> IntArray = { _, _ -> IntArray(0) }

        "inside a box" {
            KeyHitTester.keyAt(boxes, 150, 50, none) shouldBe 1
            KeyHitTester.keyAt(boxes, 100, 99, none) shouldBe 1
            KeyHitTester.keyAt(boxes, 239, 199, none) shouldBe 4
        }

        "edge keys own what lies beyond their edge" {
            KeyHitTester.keyAt(boxes, 20, 150, none) shouldBe 3
            KeyHitTester.keyAt(boxes, 280, 150, none) shouldBe 4
            KeyHitTester.keyAt(boxes, 150, -5, none) shouldBe 1
            KeyHitTester.keyAt(boxes, 60, 230, none) shouldBe 3
        }

        "extra touch width counts as the key" {
            val expanded = boxes.toMutableList().apply { this[3] = KeyTouchBox(0, 100, 140, 200) }
            KeyHitTester.keyAt(expanded, 20, 150, none) shouldBe 3
        }

        "a gap falls to the closest nearby key" {
            val gapped =
                listOf(
                    KeyTouchBox(0, 0, 90, 100),
                    KeyTouchBox(110, 0, 200, 100),
                    KeyTouchBox(220, 0, 300, 100),
                )
            KeyHitTester.keyAt(gapped, 95, 50, all) shouldBe 0
            KeyHitTester.keyAt(gapped, 106, 50, all) shouldBe 1
            KeyHitTester.keyAt(gapped, 212, 50, all) shouldBe 2
            KeyHitTester.keyAt(gapped, 95, 50, none) shouldBe -1
        }

        "only candidates are considered" {
            val gapped = listOf(KeyTouchBox(0, 0, 90, 100), KeyTouchBox(110, 0, 200, 100))
            KeyHitTester.keyAt(gapped, 95, 50) { _, _ -> intArrayOf(1, 7) } shouldBe 1
        }
    })
