/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.candidates

import com.osfans.trime.core.CandidateProto
import com.osfans.trime.core.Candidates
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LoadedCandidatesTest :
    StringSpec({
        fun cands(range: IntRange) = range.map { CandidateProto("c$it", "", "") }

        "a short bulk is the whole list" {
            val loaded = LoadedCandidates.from(Candidates.Bulk(3, 0, cands(0..2).toTypedArray()))
            loaded.endReached shouldBe true
            loaded.nextStart shouldBe 3
        }

        "a full bulk with unknown total may have more" {
            val loaded = LoadedCandidates.from(Candidates.Bulk(-1, 0, cands(0..15).toTypedArray()))
            loaded.endReached shouldBe false
            loaded.nextStart shouldBe 16
        }

        "an empty bulk has nothing to load" {
            LoadedCandidates.from(Candidates.Bulk(0, 0, arrayOf())).endReached shouldBe true
        }

        "a full page keeps loading, a short page ends the list" {
            val first = LoadedCandidates.from(Candidates.Bulk(-1, 0, cands(0..15).toTypedArray()))
            val second = first.append(cands(16..47), limit = 32)
            second.endReached shouldBe false
            second.nextStart shouldBe 48
            val third = second.append(cands(48..50), limit = 32)
            third.endReached shouldBe true
            third.items.size shouldBe 51
        }

        "an empty page ends the list" {
            val first = LoadedCandidates.from(Candidates.Bulk(-1, 0, cands(0..15).toTypedArray()))
            first.append(emptyList(), limit = 32).endReached shouldBe true
        }

        "transform applies to the bulk" {
            val loaded =
                LoadedCandidates.from(Candidates.Bulk(1, 0, arrayOf(CandidateProto("你", "ni", "")))) {
                    it.copy(comment = "")
                }
            loaded.items.single().comment shouldBe ""
        }
    })
