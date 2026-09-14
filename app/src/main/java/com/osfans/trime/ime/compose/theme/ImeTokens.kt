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
 * Every value and the reason for it is in docs/ime-design-system.md. The defaults are the
 * portrait values; [Landscape] only overrides what a 400dp-tall screen cannot afford.
 */
@Immutable
data class ImeTokens(
    // candidate bar
    /** The candidate is what you read most; same visual weight as the key letters. */
    val candidateTextSize: TextUnit = 20.sp,
    /** ~70% of the candidate, still readable: the comment carries typo hints and toned pinyin. */
    val candidateCommentTextSize: TextUnit = 14.sp,
    val candidateHorizontalPadding: Dp = 12.dp,
    /** Gap between a candidate's text and its comment. */
    val candidateCommentGap: Dp = 2.dp,
    /** The highlighted candidate's block hugs the text, this much on every side. */
    val candidateHighlightPadding: Dp = 4.dp,
    val candidateHighlightCornerRadius: Dp = 6.dp,
    /** Height of the candidate row itself (a landscape bar may add a preedit line above it). */
    val candidateBarHeight: Dp = 48.dp,
    // preedit
    val preeditTextSize: TextUnit = 16.sp,
    /** Nine-key pinyin hint is smaller than a normal preedit. */
    val preeditT9TextSize: TextUnit = 13.sp,
    val preeditHorizontalPadding: Dp = 8.dp,
    val preeditVerticalPadding: Dp = 2.dp,
    val preeditCornerRadius: Dp = 6.dp,
    // keyboard frame
    /** Top corners of the whole keyboard window (candidate bar included). */
    val keyboardCornerRadius: Dp = 26.dp,
    /** Left / right inset of the key grid from the window edge. */
    val keyboardHorizontalPadding: Dp = 3.dp,
    /** Row pitch, gap included: key body = keyRowHeight - keyVerticalGap. */
    val keyRowHeight: Dp = 53.dp,
    // keys
    val keyCornerRadius: Dp = 6.dp,
    /** Gap between keys in a row; drawn as padding, so it stays part of the touch target. */
    val keyHorizontalGap: Dp = 6.dp,
    val keyVerticalGap: Dp = 8.dp,
    /** Single-character keys: letters, `，` `。`, digits. */
    val keyTextSize: TextUnit = 22.sp,
    /** Function-key words: `123`, `换行`, `ZH`, `空格`. */
    val keyLabelTextSize: TextUnit = 16.sp,
    /** A run of letters on a key that types a character: the nine-key `ABC` / `PQRS` keys are letter keys, not controls. */
    val keyLetterGroupTextSize: TextUnit = 20.sp,
    /** Glyph keys: shift, backspace, globe, mic, emoji. */
    val keyIconSize: Dp = 22.dp,
    /** The swipe-up / long-press hint printed on a key. */
    val keySymbolTextSize: TextUnit = 10.sp,
    /** Hints sit in the top-end corner, out of the letter's way. */
    val keySymbolInsetTop: Dp = 3.dp,
    val keySymbolInsetEnd: Dp = 5.dp,
    /** Hint colour = key text colour at this alpha: findable at a glance, never louder than the letter. */
    val keySymbolAlpha: Float = 0.5f,
    // press feedback
    /** Character keys: a bubble above the key shows what will be typed, or what the swipe will type. */
    val keyPreviewWidth: Dp = 44.dp,
    val keyPreviewHeight: Dp = 56.dp,
    val keyPreviewTextSize: TextUnit = 30.sp,
    /** Function keys get no bubble; while held they swap to the letter-key shade instead. */
    val keyPressedFunctionSwap: Boolean = true,
) {
    companion object {
        val Portrait = ImeTokens()
        val Landscape = ImeTokens(
            candidateTextSize = 18.sp,
            candidateCommentTextSize = 12.sp,
            candidateHorizontalPadding = 10.dp,
            candidateHighlightPadding = 3.dp,
            candidateHighlightCornerRadius = 5.dp,
            candidateBarHeight = 32.dp,
            preeditTextSize = 14.sp,
            preeditT9TextSize = 12.sp,
            preeditHorizontalPadding = 6.dp,
            preeditVerticalPadding = 1.dp,
            preeditCornerRadius = 5.dp,
            keyboardHorizontalPadding = 40.dp,
            keyRowHeight = 40.dp,
            keyVerticalGap = 6.dp,
            keyTextSize = 20.sp,
            keyLabelTextSize = 14.sp,
            keyLetterGroupTextSize = 18.sp,
            keyIconSize = 20.dp,
            keySymbolTextSize = 9.sp,
            keySymbolInsetTop = 2.dp,
            keySymbolInsetEnd = 4.dp,
            keyPreviewHeight = 48.dp,
            keyPreviewTextSize = 26.sp,
        )
    }
}

val LocalImeTokens = staticCompositionLocalOf { ImeTokens.Portrait }
