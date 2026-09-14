/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.candidates

import androidx.compose.runtime.Immutable
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.core.Candidates

/**
 * The part of the current candidate list the bar has fetched so far.
 *
 * librime pushes the first [Candidates.Bulk] (16 items, see `getBulkCandidates` in
 * `rime_jni.cc`) with every composition; the rest is pulled in pages while the user
 * scrolls. [total] is -1 when librime does not know the size, which is always the case
 * once the list is at least one bulk long.
 */
@Immutable
data class LoadedCandidates(
    val items: List<CandidateProto> = emptyList(),
    val total: Int = 0,
    val endReached: Boolean = true,
) {
    /** Global index of the first candidate not fetched yet. */
    val nextStart: Int get() = items.size

    fun append(
        page: List<CandidateProto>,
        limit: Int,
    ): LoadedCandidates {
        val merged = items + page
        return copy(
            items = merged,
            endReached = page.size < limit || (total >= 0 && merged.size >= total),
        )
    }

    companion object {
        fun from(
            bulk: Candidates.Bulk,
            transform: (CandidateProto) -> CandidateProto = { it },
        ): LoadedCandidates {
            val items = bulk.candidates.map(transform)
            return LoadedCandidates(
                items = items,
                total = bulk.total,
                endReached = bulk.total >= 0 && items.size >= bulk.total,
            )
        }
    }
}
