/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.bar

import androidx.compose.ui.unit.dp

/**
 * Sizes of the bar above the keyboard and of the unrolled candidate grid that
 * [com.osfans.trime.ime.compose.theme.ImeTokens] does not cover.
 *
 * Kept apart from `ImeTokens` while several panels move to Compose at once; the values are the
 * ones the View bar used, so the bar does not change size in the switch.
 */
object BarTokens {
    /** The unroll button at the end of the candidate row. */
    val unrollButtonWidth = 40.dp

    /** Plain icon buttons (hide keyboard, unroll, back) leave this much around the glyph. */
    val iconButtonPadding = 4.dp

    /** Pressed plain icon buttons get a rounded block of the highlight colour. */
    val iconButtonCornerRadius = 8.dp

    // clipboard suggestion
    val clipboardIconSize = 20.dp
    val clipboardSpacing = 4.dp
    val clipboardMaxTextWidth = 220.dp

    /** The suggestion chip keeps this much off the top and bottom of the bar. */
    val clipboardVerticalMargin = 4.dp
    val clipboardCornerRadius = 8.dp

    /** Characters of the clip shown in the suggestion; the rest is cut before measuring. */
    const val CLIPBOARD_PREVIEW_LENGTH = 42

    // inline (autofill) suggestions
    val inlinePinnedHorizontalMargin = 10.dp

    // tab bar

    /** Between the back button, the title and the window's own bar view. */
    val tabSpacing = 8.dp

    // unrolled grid
    val unrolledItemMinWidth = 40.dp
}
