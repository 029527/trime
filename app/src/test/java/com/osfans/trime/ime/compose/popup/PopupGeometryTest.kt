/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.popup

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe

class PopupGeometryTest :
    StringSpec({
        "bubble is centred on the key" {
            PopupGeometry.centeredLeft(centerX = 500, width = 100, containerWidth = 1080) shouldBe 450
        }

        "bubble on an edge key stays on screen" {
            PopupGeometry.centeredLeft(centerX = 20, width = 100, containerWidth = 1080) shouldBe 0
            PopupGeometry.centeredLeft(centerX = 1070, width = 100, containerWidth = 1080) shouldBe 980
        }

        "bubble never goes above the layer" {
            PopupGeometry.topAbove(bottom = 1600, height = 150) shouldBe 1450
            PopupGeometry.topAbove(bottom = 100, height = 150) shouldBe 0
        }

        "columns count outwards from the focus, right first" {
            PopupGeometry.columnOrder(5, 2).toList() shouldBe listOf(4, 2, 0, 1, 3)
            PopupGeometry.columnOrder(4, 0).toList() shouldBe listOf(0, 1, 2, 3)
            PopupGeometry.columnOrder(4, 3).toList() shouldBe listOf(3, 2, 1, 0)
        }

        "initial column moves to the side with room" {
            PopupGeometry.initialFocusColumn(5, 100, leftSpace = 1000, rightSpace = 1000) shouldBe 2
            PopupGeometry.initialFocusColumn(5, 100, leftSpace = 0, rightSpace = 1000) shouldBe 0
            PopupGeometry.initialFocusColumn(5, 100, leftSpace = 1000, rightSpace = 0) shouldBe 4
            PopupGeometry.initialFocusColumn(5, 100, leftSpace = 150, rightSpace = 1000) shouldBe 1
        }

        "every key gets a cell" {
            for (count in 1..15) {
                val layout = PopupKeyboardLayout(count, 1080, 480, 580, 1500, 100, 120, 10)
                (layout.rowCount * layout.columnCount) shouldBeGreaterThanOrEqual count
                val cells = (0 until count).map { layout.cellLeft(it) to layout.cellTop(it) }
                cells.toSet().size shouldBe count
            }
        }

        "first key starts under the finger" {
            val layout = PopupKeyboardLayout(3, 1080, 480, 580, 1500, 100, 120, 10)
            layout.initialFocus shouldBe 0
            // focused cell centred on the key
            (layout.left + layout.cellLeft(0) + 50) shouldBe 530
            (layout.top + layout.height) shouldBe 1500
        }

        "keyboard of an edge key stays on screen" {
            val left = PopupKeyboardLayout(5, 1080, 0, 100, 1500, 100, 120, 10)
            left.left shouldBe 0
            val right = PopupKeyboardLayout(5, 1080, 980, 1080, 1500, 100, 120, 10)
            (right.left + right.width) shouldBeLessThanOrEqual 1080
        }

        "sliding picks the key under the finger" {
            val layout = PopupKeyboardLayout(3, 1080, 480, 580, 1500, 100, 120, 10)
            // finger resting on the held key, below the keyboard
            layout.focusAt(530f, 1560f) shouldBe 0
            // one cell to the right, one to the left: centre, right, left
            layout.focusAt(630f, 1560f) shouldBe 1
            layout.focusAt(430f, 1560f) shouldBe 2
            // far away closes it
            layout.focusAt(530f, 2400f) shouldBe PopupKeyboardLayout.OUTSIDE
            layout.focusAt(-400f, 1450f) shouldBe PopupKeyboardLayout.OUTSIDE
        }

        "rows are entered above their bottom third" {
            // 7 keys: 2 rows x 4 columns, bottom row 0..3, top row 4..6 and a hole
            val layout = PopupKeyboardLayout(7, 1080, 480, 580, 1500, 100, 120, 10)
            layout.rowCount shouldBe 2
            val x = (layout.left + layout.cellLeft(0) + 50).toFloat()
            val bottomInner = (layout.top + layout.height - 10).toFloat()
            layout.focusAt(x, bottomInner - 20f) shouldBe 0
            layout.focusAt(x, bottomInner - 40f) shouldBe 4
        }

        "a cell without a key keeps the focus" {
            val layout = PopupKeyboardLayout(7, 1080, 480, 580, 1500, 100, 120, 10)
            val hole = (0 until 4).map { col -> layout.left + 10 + col * 100 + 50 }
                .first { x -> (0 until 7).none { layout.left + layout.cellLeft(it) + 50 == x && layout.cellTop(it) == 10 } }
            layout.focusAt(hole.toFloat(), (layout.top + 10 + 60).toFloat()) shouldBe PopupKeyboardLayout.EMPTY
        }
    })
