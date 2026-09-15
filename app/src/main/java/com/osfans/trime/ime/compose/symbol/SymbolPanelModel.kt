/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.symbol

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

/**
 * Everything the symbol panel shows, independent of where it was defined.
 *
 * The panel only renders this; a [SymbolPanelSource] builds it. Today the source reads the
 * theme's `liquid_keyboard` section, later it can be a table written in code.
 */
@Immutable
data class SymbolPanelSpec(
    val categories: List<SymbolCategory>,
    /** Fixed keys next to the grid (back, space, backspace...), in display order. */
    val barKeys: List<SymbolBarKey> = emptyList(),
    val barPosition: SymbolBarPosition = SymbolBarPosition.Bottom,
    /** Narrowest a one-column cell may be; the grid fits as many columns as this allows. Null: [com.osfans.trime.ime.compose.theme.ImeTokens.symbolCellMinWidth]. */
    val cellWidth: Dp? = null,
    /** Height of a cell body, gap excluded. Null: the key body height of the keyboard. */
    val cellHeight: Dp? = null,
)

@Immutable
data class SymbolCategory(
    val label: String,
)

/**
 * One grid cell. An empty [label] is not a key: it ends the current row, so a list can
 * start a new line at a chosen place.
 */
@Immutable
data class SymbolItem(
    val label: String,
    val action: SymbolAction,
)

sealed interface SymbolAction {
    /** Commit [text]; [remember] adds it to the recently used symbols. */
    data class Commit(
        val text: String,
        val remember: Boolean,
    ) : SymbolAction

    /** Type [keys] through Rime, e.g. `/fh` to open a symbol list in the candidates, then return to the keyboard. */
    data class TypeKeys(
        val keys: String,
    ) : SymbolAction

    /** Switch the panel to the category at [index]. */
    data class OpenCategory(
        val index: Int,
    ) : SymbolAction
}

@Immutable
data class SymbolBarKey(
    val label: String,
    /** Key action name, resolved by the window. */
    val action: String,
    /** Fires again and again while held, like backspace. */
    val repeatable: Boolean = false,
    /** Drawn in the function-key shade; false for keys that type, like space. */
    val functional: Boolean = true,
)

enum class SymbolBarPosition { Top, Bottom, Left, Right }

/** Supplies the panel's content. Main thread only. */
interface SymbolPanelSource {
    fun spec(): SymbolPanelSpec

    /** Items of the category at [index]; may change between calls (recently used). */
    fun items(index: Int): List<SymbolItem>

    /** Record [text] as recently used, for [SymbolAction.Commit.remember]. */
    fun remember(text: String)
}
