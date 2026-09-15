/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.candidates.unrolled

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ChunkRowsTest :
    StringSpec({
        "an empty list has no rows" {
            chunkRows(emptyList(), 0, 100) shouldBe emptyList()
        }

        "items wrap when the next one does not fit" {
            chunkRows(listOf(40, 40, 40, 40, 40), 5, 100) shouldBe listOf(0..1, 2..3, 4..4)
        }

        "an item that fills the row exactly stays on it" {
            chunkRows(listOf(50, 50, 50), 3, 100) shouldBe listOf(0..1, 2..2)
        }

        "an item wider than the row gets a row of its own" {
            chunkRows(listOf(30, 250, 30), 3, 100) shouldBe listOf(0..0, 1..1, 2..2)
        }

        "only the first count items are laid out" {
            // widths may already hold measurements for items the caller does not show yet
            chunkRows(listOf(40, 40, 40, 40), 3, 100) shouldBe listOf(0..1, 2..2)
        }
    })
