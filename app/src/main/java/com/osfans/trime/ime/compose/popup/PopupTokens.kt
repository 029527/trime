/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.popup

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osfans.trime.ime.compose.theme.ImeTokens

/**
 * Popup sizes that [ImeTokens] does not have yet. The bubble's own size and text size
 * ([ImeTokens.keyPreviewWidth], [ImeTokens.keyPreviewHeight], [ImeTokens.keyPreviewTextSize])
 * stay in [ImeTokens]; these are the rest, to be folded into it later.
 *
 * Looks follow iOS: a light rounded block with a soft shadow, sitting just above the key.
 */
@Immutable
data class PopupTokens(
    /** Space between the top of the key body and the bottom of the bubble / popup keyboard. */
    val anchorGap: Dp = 2.dp,
    /** Rounder than a key (6dp): the bubble is taller and floats over the app. */
    val previewCornerRadius: Dp = 8.dp,
    /** Just enough to lift the bubble off same-coloured keys. */
    val shadowElevation: Dp = 3.dp,
    /** Cells of the long-press keyboard are as wide as the bubble, so a held key reads the same. */
    val keyboardCellWidth: Dp = 44.dp,
    val keyboardCellHeight: Dp = 48.dp,
    val keyboardTextSize: TextUnit = 24.sp,
    val keyboardPadding: Dp = 4.dp,
    val keyboardCornerRadius: Dp = 10.dp,
    /** Focused cell's block; same radius as a key. */
    val keyboardHighlightCornerRadius: Dp = 6.dp,
) {
    companion object {
        val Portrait = PopupTokens()
        val Landscape = PopupTokens(
            keyboardCellHeight = 40.dp,
            keyboardTextSize = 22.sp,
        )
    }
}
