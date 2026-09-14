/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The fixed half of the keyboard's look: sizes, spacing, shapes, type scale.
 *
 * These are decided in code on purpose and are **not** read from the theme yaml. The yaml
 * keeps the variable half — palette (see [ImeColors]), fonts, key layouts — so a theme
 * can recolour the keyboard but cannot make it clumsy.
 *
 * Values are placeholders until the design pass lands (docs/ime-design-system.md).
 */
@Immutable
data class ImeTokens(
    // candidate bar
    val candidateTextSize: TextUnit = 20.sp,
    val candidateCommentTextSize: TextUnit = 11.sp,
    val candidateHorizontalPadding: Dp = 14.dp,
    /** Gap between a candidate's text and its comment. */
    val candidateCommentGap: Dp = 2.dp,
    /** The highlighted candidate's block hugs the text, this much on every side. */
    val candidateHighlightPadding: Dp = 2.dp,
    val candidateHighlightCornerRadius: Dp = 6.dp,
    // preedit
    val preeditTextSize: TextUnit = 16.sp,
    /** Nine-key pinyin hint is smaller than a normal preedit. */
    val preeditT9TextSize: TextUnit = 12.sp,
    val preeditHorizontalPadding: Dp = 8.dp,
    val preeditVerticalPadding: Dp = 2.dp,
    val preeditCornerRadius: Dp = 6.dp,
) {
    companion object {
        val Portrait = ImeTokens()
        val Landscape = ImeTokens()
    }
}

val LocalImeTokens = staticCompositionLocalOf { ImeTokens.Portrait }
