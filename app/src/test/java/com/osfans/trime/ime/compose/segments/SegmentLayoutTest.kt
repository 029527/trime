// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.compose.segments

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SegmentLayoutTest :
    StringSpec({
        "joined segments keep whitespace only between selected neighbours" {
            val raw = "hello world\n你好"
            val segments = listOf("hello", "world", "你好")
            val separators = segmentSeparators(segments, raw)
            separators shouldBe listOf(" ", "\n", "")
            joinSelectedSegments(segments, separators) { it == 0 || it == 1 } shouldBe "hello world"
            joinSelectedSegments(segments, separators) { it == 1 || it == 2 } shouldBe "world\n你好"
            joinSelectedSegments(segments, separators) { it == 0 || it == 2 } shouldBe "hello你好"
            joinSelectedSegments(segments, separators) { false } shouldBe ""
        }

        "rows wrap and a too-wide cell gets its own row" {
            val rows = SegmentRows.layout(intArrayOf(50, 50, 50, 200, 10), maxWidth = 120)
            rows.rows shouldBe listOf(0..1, 2..2, 3..3, 4..4)
            rows.cellX.toList() shouldBe listOf(0, 50, 0, 0, 0)
            rows.cellWidth[3] shouldBe 120
            rows.indexAt(0, 75f) shouldBe 1
            rows.indexAt(1, 75f) shouldBe -1
            rows.indexAt(9, 0f) shouldBe -1
        }

        "drag selects a run and restores what it leaves" {
            val selected = booleanArrayOf(false, false, false, false, true)
            val drag = DragSelectRange(selected.size, { selected[it] }, { i, v -> selected[i] = v })
            drag.begin(1)
            drag.extendTo(4)
            selected.toList() shouldBe listOf(false, true, true, true, true)
            drag.extendTo(2)
            selected.toList() shouldBe listOf(false, true, true, false, true)
            drag.extendTo(0)
            selected.toList() shouldBe listOf(true, true, false, false, true)
            drag.end()
        }

        "drag starting on a selected segment clears" {
            val selected = booleanArrayOf(true, true, true)
            val drag = DragSelectRange(selected.size, { selected[it] }, { i, v -> selected[i] = v })
            drag.begin(2)
            drag.extendTo(1)
            selected.toList() shouldBe listOf(true, false, false)
        }
    })
