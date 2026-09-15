/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.symbol

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the symbol panel is showing, shared by the grid and the category tabs.
 *
 * Owned by the window rather than the composition: the panel's views are detached (and their
 * compositions disposed) every time the keyboard comes back, and the selected category must
 * survive that. Main thread only.
 */
@Stable
class SymbolPanelState(
    private val source: SymbolPanelSource,
) {
    val spec: SymbolPanelSpec = source.spec()

    var selected by mutableIntStateOf(-1)
        private set

    var items by mutableStateOf<List<SymbolItem>>(emptyList())
        private set

    val gridState = LazyGridState()

    val tabsState = LazyListState()

    /** Show the category at [index] from the top, reloading its items. Out-of-range indices are ignored. */
    fun select(index: Int) {
        if (index !in spec.categories.indices) return
        selected = index
        items = source.items(index)
        gridState.requestScrollToItem(0)
    }
}
