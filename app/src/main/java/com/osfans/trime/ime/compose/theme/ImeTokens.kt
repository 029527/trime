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
    // key preview bubble and popup keyboard
    // Looks follow iOS: a light rounded block with a soft shadow, sitting just above the key.
    /** Space between the top of the key body and the bottom of the bubble / popup keyboard. */
    val popupAnchorGap: Dp = 2.dp,
    /** Rounder than a key (6dp): the bubble is taller and floats over the app. */
    val popupPreviewCornerRadius: Dp = 8.dp,
    /** Just enough to lift the bubble off same-coloured keys. */
    val popupShadowElevation: Dp = 3.dp,
    /** Cells of the long-press keyboard are as wide as the bubble, so a held key reads the same. */
    val popupKeyboardCellWidth: Dp = 44.dp,
    val popupKeyboardCellHeight: Dp = 48.dp,
    val popupKeyboardTextSize: TextUnit = 24.sp,
    val popupKeyboardPadding: Dp = 4.dp,
    val popupKeyboardCornerRadius: Dp = 10.dp,
    /** Focused cell's block; same radius as a key. */
    val popupKeyboardHighlightCornerRadius: Dp = 6.dp,
    // symbol panel
    // Corners, gaps, and type sizes come from the key group, so a cell looks like a key.
    /** Narrowest single cell when the source gives none; wide enough for one 22sp emoji plus gaps. */
    val symbolCellMinWidth: Dp = 48.dp,
    /** Text inside a long-text cell keeps this far from the key body edges; small enough that a three-character label still fits one cell. */
    val symbolLongTextHorizontalPadding: Dp = 4.dp,
    /** Width of the fixed key column when the bar sits left or right of the grid. */
    val symbolSideBarWidth: Dp = 64.dp,
    // panel lists: clipboard, switches, word segments
    /** Outer padding of a list, and the gap between its cells. */
    val panelListPadding: Dp = 4.dp,
    val panelCellSpacing: Dp = 6.dp,
    val panelEntryTextSize: TextUnit = 15.sp,
    val panelEntryPaddingHorizontal: Dp = 10.dp,
    val panelEntryPaddingVertical: Dp = 8.dp,
    val panelEntryMaxLines: Int = 4,
    val panelEntryPinSize: Dp = 12.dp,
    val panelEntryPinAlpha: Float = 0.3f,
    /** Narrowest grid column: four columns on a portrait phone. */
    val panelSwitchCellMinWidth: Dp = 88.dp,
    val panelSwitchCellHeight: Dp = 96.dp,
    val panelSwitchTileSize: Dp = 48.dp,
    val panelSwitchIconSize: Dp = 24.dp,
    val panelSwitchGlyphTextSize: TextUnit = 20.sp,
    val panelSwitchLabelTextSize: TextUnit = 12.sp,
    val panelSegmentPaddingHorizontal: Dp = 8.dp,
    val panelSegmentPaddingVertical: Dp = 4.dp,
    /** Margin on every side of a segment chip. */
    val panelSegmentMargin: Dp = 4.dp,
    /** Dragging this close to the top / bottom edge scrolls the list. */
    val panelSegmentEdgeScrollZone: Dp = 10.dp,
    val panelSegmentEdgeScrollStep: Dp = 12.dp,
    val panelBarIconSize: Dp = 24.dp,
    val panelDisabledAlpha: Float = 0.38f,
    val panelMenuTextSize: TextUnit = 16.sp,
    val panelEmptyHintTextSize: TextUnit = 14.sp,
    val panelEmptyHintAlpha: Float = 0.6f,
    // input bar
    // The values are the ones the View bar used, so the bar did not change size in the switch.
    /** The unroll button at the end of the candidate row. */
    val barUnrollButtonWidth: Dp = 40.dp,
    /** Plain icon buttons (hide keyboard, unroll, back) leave this much around the glyph. */
    val barIconButtonPadding: Dp = 4.dp,
    /** Pressed plain icon buttons get a rounded block of the highlight colour. */
    val barIconButtonCornerRadius: Dp = 8.dp,
    val barClipboardIconSize: Dp = 20.dp,
    val barClipboardSpacing: Dp = 4.dp,
    val barClipboardMaxTextWidth: Dp = 220.dp,
    /** The clipboard suggestion chip keeps this much off the top and bottom of the bar. */
    val barClipboardVerticalMargin: Dp = 4.dp,
    val barClipboardCornerRadius: Dp = 8.dp,
    /** Characters of the clip shown in the suggestion; the rest is cut before measuring. */
    val barClipboardPreviewLength: Int = 42,
    val barInlinePinnedHorizontalMargin: Dp = 10.dp,
    /** Between a board window's back button, its title and the window's own bar view. */
    val barTabSpacing: Dp = 8.dp,
    /** Narrowest item of the unrolled candidate grid. */
    val barUnrolledItemMinWidth: Dp = 40.dp,
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
            popupKeyboardCellHeight = 40.dp,
            popupKeyboardTextSize = 22.sp,
        )
    }
}

val LocalImeTokens = staticCompositionLocalOf { ImeTokens.Portrait }
