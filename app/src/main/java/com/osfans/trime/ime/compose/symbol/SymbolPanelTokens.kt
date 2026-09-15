/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.symbol

import androidx.compose.ui.unit.dp

/**
 * Sizes the symbol panel needs on top of [com.osfans.trime.ime.compose.theme.ImeTokens].
 *
 * Corners, gaps, and type sizes come from the key group of `ImeTokens`, so a cell looks like a key:
 * one character is [com.osfans.trime.ime.compose.theme.ImeTokens.keyTextSize], anything longer
 * is [com.osfans.trime.ime.compose.theme.ImeTokens.keyLabelTextSize] on the long-text colours.
 */
object SymbolPanelTokens {
    /** Narrowest single cell when the source gives none; wide enough for one 22sp emoji plus gaps. */
    val defaultCellWidth = 48.dp

    /** Text inside a long-text cell keeps this far from the key body edges; small enough that a three-character label still fits one cell. */
    val longTextHorizontalPadding = 4.dp

    /** Width of the fixed key column when the bar sits left or right of the grid. */
    val sideBarWidth = 64.dp
}
