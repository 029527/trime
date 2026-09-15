/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The fixed half of the keyboard's look: sizes, spacing, shapes, type scale and type weights.
 *
 * These are decided in code on purpose and are **not** read from the theme. The theme keeps the
 * variable half — palette (see [ImeColors]) and key layouts — so it can recolour the keyboard but
 * cannot make it clumsy. The font itself is built in, see [com.osfans.trime.data.theme.SourceHanSans].
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
    /** The highlighted candidate's pill hugs the text: this much beside it... */
    val candidateHighlightPaddingHorizontal: Dp = 8.dp,
    /** ...and this much above and below. */
    val candidateHighlightPaddingVertical: Dp = 4.dp,
    /** More than half the block's height, so the block is a pill whatever the text size. */
    val candidateHighlightCornerRadius: Dp = 50.dp,
    /** Height of the candidate row itself (a landscape bar may add a preedit line above it). */
    val candidateBarHeight: Dp = 48.dp,
    // preedit
    val preeditTextSize: TextUnit = 16.sp,
    /** Nine-key pinyin hint is smaller than a normal preedit. */
    val preeditT9TextSize: TextUnit = 13.sp,
    val preeditHorizontalPadding: Dp = 8.dp,
    val preeditVerticalPadding: Dp = 2.dp,
    val preeditCornerRadius: Dp = 12.dp,
    // keyboard frame
    /** Corners of a floating keyboard window. A docked keyboard is flat, edge to edge, like Gboard. */
    val keyboardCornerRadius: Dp = 16.dp,
    /** Left / right inset of the key grid from the window edge. */
    val keyboardHorizontalPadding: Dp = 3.dp,
    /** Row pitch, gap included: key body = keyRowHeight - keyVerticalGap. */
    val keyRowHeight: Dp = 53.dp,
    // keys
    /** Rounder than a small square, not a pill: a 34dp-wide key stays a key. */
    val keyCornerRadius: Dp = 10.dp,
    /** Gap between keys in a row; drawn as padding, so it stays part of the touch target. */
    val keyHorizontalGap: Dp = 6.dp,
    val keyVerticalGap: Dp = 8.dp,
    /** Single-character keys: letters, `，` `。`, digits. */
    val keyTextSize: TextUnit = 22.sp,
    /** Function-key words: `123`, `换行`, `ZH`, `空格`. */
    val keyLabelTextSize: TextUnit = 16.sp,
    /** A run of letters on a key that types a character: the nine-key `ABC` / `PQRS` keys are letter keys, not controls. */
    val keyLetterGroupTextSize: TextUnit = 20.sp,
    /** Glyph keys: shift, backspace, globe, mic, emoji. A Material glyph fills about 20 of its 24dp. */
    val keyIconSize: Dp = 24.dp,
    /** The swipe-up / long-press hint printed on a key. */
    val keySymbolTextSize: TextUnit = 10.sp,
    /** A glyph hint (select all, cut, copy, paste) in the top-end corner; a little larger than the text hint, for the glyph's padding. */
    val keySymbolIconSize: Dp = 13.dp,
    /** Hints sit in the top-end corner, out of the letter's way. */
    val keySymbolInsetTop: Dp = 3.dp,
    val keySymbolInsetEnd: Dp = 5.dp,
    /** Hint colour = key text colour at this alpha: findable at a glance, never louder than the letter. */
    val keySymbolAlpha: Float = 0.5f,
    // type weights
    // One variable font for everything (see SourceHanSans); a role picks a point on its weight axis.
    /** Letters, digits and other single characters on keys, the nine-key letter groups too. */
    val keyTextWeight: FontWeight = FontWeight(500),
    /** Function-key words (`123`, `换行`, `ZH`): a notch heavier, they are read at a glance and are smaller. */
    val keyLabelWeight: FontWeight = FontWeight(550),
    /** The swipe / long-press hint in the corner and the bottom hint. */
    val keySymbolWeight: FontWeight = FontWeight(400),
    val candidateWeight: FontWeight = FontWeight(450),
    /** The highlighted candidate stands out by weight as well as by its block. */
    val candidateHighlightWeight: FontWeight = FontWeight(500),
    val candidateCommentWeight: FontWeight = FontWeight(400),
    val preeditWeight: FontWeight = FontWeight(400),
    /** Key preview bubble and the cells of the long-press keyboard. */
    val popupWeight: FontWeight = FontWeight(500),
    /** Titles in the bar above a panel: symbol categories, clipboard pages, board window titles. */
    val panelTitleWeight: FontWeight = FontWeight(600),
    /** Short labels on panel keys and cells: symbol bar keys, edit panel, switches, menus. */
    val panelLabelWeight: FontWeight = FontWeight(500),
    /** Running text in panels: clipboard entries, phrases, the clipboard suggestion, empty hints. */
    val panelBodyWeight: FontWeight = FontWeight(400),
    // press feedback
    /** Character keys: a bubble above the key shows what will be typed, or what the swipe will type. */
    val keyPreviewWidth: Dp = 44.dp,
    val keyPreviewHeight: Dp = 56.dp,
    val keyPreviewTextSize: TextUnit = 30.sp,
    /**
     * Function keys get no bubble. While held they take their own pressed tone (a state layer), not
     * the letter-key shade: panels that draw function keys themselves read this.
     */
    val keyPressedFunctionSwap: Boolean = false,
    /** The enter key is a pill in the accent colour, the one key that is not a rounded rectangle. */
    val enterKeyPill: Boolean = true,
    // key preview bubble and popup keyboard
    // Material surfaces: a rounded block with a low elevation, sitting just above the key.
    /** Space between the top of the key body and the bottom of the bubble / popup keyboard. */
    val popupAnchorGap: Dp = 2.dp,
    /** Rounder than a key: the bubble is taller and floats over the app. */
    val popupPreviewCornerRadius: Dp = 14.dp,
    /** Just enough to lift the bubble off same-coloured keys. */
    val popupShadowElevation: Dp = 3.dp,
    /** Cells of the long-press keyboard are as wide as the bubble, so a held key reads the same. */
    val popupKeyboardCellWidth: Dp = 44.dp,
    val popupKeyboardCellHeight: Dp = 48.dp,
    val popupKeyboardTextSize: TextUnit = 24.sp,
    val popupKeyboardPadding: Dp = 4.dp,
    val popupKeyboardCornerRadius: Dp = 16.dp,
    /** Focused cell's block, in the accent colour; as round as a key. */
    val popupKeyboardHighlightCornerRadius: Dp = 10.dp,
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
    /** A Material quick-settings tile: rounder than a key. */
    val panelSwitchTileCornerRadius: Dp = 16.dp,
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
    /** Pressed plain icon buttons get a round block of the highlight colour. */
    val barIconButtonCornerRadius: Dp = 20.dp,
    val barClipboardIconSize: Dp = 20.dp,
    val barClipboardSpacing: Dp = 4.dp,
    val barClipboardMaxTextWidth: Dp = 220.dp,
    /** The clipboard suggestion chip keeps this much off the top and bottom of the bar. */
    val barClipboardVerticalMargin: Dp = 4.dp,
    val barClipboardCornerRadius: Dp = 20.dp,
    /** Characters of the clip shown in the suggestion; the rest is cut before measuring. */
    val barClipboardPreviewLength: Int = 42,
    val barInlinePinnedHorizontalMargin: Dp = 10.dp,
    /** Between a board window's back button, its title and the window's own bar view. */
    val barTabSpacing: Dp = 8.dp,
    /** Narrowest item of the unrolled candidate grid. */
    val barUnrolledItemMinWidth: Dp = 40.dp,
    // edit panel
    // Cells are keys: corners, gaps and shades come from the key group; rows share the window height.
    /** Share of the panel width given to the clipboard command column at the end. */
    val editPanelCommandColumnFraction: Float = 0.28f,
    /** Width of the up / select / down column against the left and right arrows at 1 each. */
    val editPanelCenterColumnWeight: Float = 1.4f,
    /** The arrows show only a chevron, which fills half of its box: larger than a key's icon. */
    val editPanelArrowIconSize: Dp = 30.dp,
    /** Glyph of a cell that also has a label. */
    val editPanelIconSize: Dp = 22.dp,
    val editPanelLabelTextSize: TextUnit = 14.sp,
    val editPanelIconLabelGap: Dp = 2.dp,
    /** Label beside the glyph instead of under it: a landscape row is too short for both stacked. */
    val editPanelLabelBesideIcon: Boolean = false,
    // t9 pinyin column
    // Drawn over the nine-key keyboard's left key column; cells are keys, so corners, gaps and shades come from the key group.
    /** One syllable: shorter than a key body, so the four key rows show five syllables and hint that the list scrolls. */
    val t9ColumnItemHeight: Dp = 36.dp,
    /** Between two syllables; less than the key gap, they belong to one list. */
    val t9ColumnItemSpacing: Dp = 4.dp,
) {
    companion object {
        val Portrait = ImeTokens()
        val Landscape = ImeTokens(
            candidateTextSize = 18.sp,
            candidateCommentTextSize = 12.sp,
            candidateHorizontalPadding = 10.dp,
            candidateHighlightPaddingHorizontal = 6.dp,
            candidateHighlightPaddingVertical = 3.dp,
            candidateBarHeight = 32.dp,
            preeditTextSize = 14.sp,
            preeditT9TextSize = 12.sp,
            preeditHorizontalPadding = 6.dp,
            preeditVerticalPadding = 1.dp,
            preeditCornerRadius = 10.dp,
            keyboardHorizontalPadding = 40.dp,
            keyRowHeight = 40.dp,
            keyCornerRadius = 8.dp,
            keyVerticalGap = 6.dp,
            keyTextSize = 20.sp,
            keyLabelTextSize = 14.sp,
            keyLetterGroupTextSize = 18.sp,
            keyIconSize = 22.dp,
            keySymbolIconSize = 12.dp,
            keySymbolTextSize = 9.sp,
            keySymbolInsetTop = 2.dp,
            keySymbolInsetEnd = 4.dp,
            keyPreviewHeight = 48.dp,
            keyPreviewTextSize = 26.sp,
            popupKeyboardCellHeight = 40.dp,
            popupKeyboardTextSize = 22.sp,
            editPanelArrowIconSize = 26.dp,
            editPanelIconSize = 20.dp,
            editPanelIconLabelGap = 6.dp,
            editPanelLabelBesideIcon = true,
            // t9 pinyin column
            t9ColumnItemHeight = 28.dp,
        )
    }
}

val LocalImeTokens = staticCompositionLocalOf { ImeTokens.Portrait }
