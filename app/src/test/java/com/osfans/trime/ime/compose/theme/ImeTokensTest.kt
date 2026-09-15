/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Pins the panel sizes merged into [ImeTokens] (popup, symbol panel, panel lists, input bar) to the
 * values of the objects they came from, so the merge changes no pixel.
 */
class ImeTokensTest :
    StringSpec({
        fun table(t: ImeTokens) = mapOf<String, Any>(
            "popupAnchorGap" to t.popupAnchorGap,
            "popupPreviewCornerRadius" to t.popupPreviewCornerRadius,
            "popupShadowElevation" to t.popupShadowElevation,
            "popupKeyboardCellWidth" to t.popupKeyboardCellWidth,
            "popupKeyboardCellHeight" to t.popupKeyboardCellHeight,
            "popupKeyboardTextSize" to t.popupKeyboardTextSize,
            "popupKeyboardPadding" to t.popupKeyboardPadding,
            "popupKeyboardCornerRadius" to t.popupKeyboardCornerRadius,
            "popupKeyboardHighlightCornerRadius" to t.popupKeyboardHighlightCornerRadius,
            "symbolCellMinWidth" to t.symbolCellMinWidth,
            "symbolLongTextHorizontalPadding" to t.symbolLongTextHorizontalPadding,
            "symbolSideBarWidth" to t.symbolSideBarWidth,
            "panelListPadding" to t.panelListPadding,
            "panelCellSpacing" to t.panelCellSpacing,
            "panelEntryTextSize" to t.panelEntryTextSize,
            "panelEntryPaddingHorizontal" to t.panelEntryPaddingHorizontal,
            "panelEntryPaddingVertical" to t.panelEntryPaddingVertical,
            "panelEntryMaxLines" to t.panelEntryMaxLines,
            "panelEntryPinSize" to t.panelEntryPinSize,
            "panelEntryPinAlpha" to t.panelEntryPinAlpha,
            "panelSwitchCellMinWidth" to t.panelSwitchCellMinWidth,
            "panelSwitchCellHeight" to t.panelSwitchCellHeight,
            "panelSwitchTileSize" to t.panelSwitchTileSize,
            "panelSwitchIconSize" to t.panelSwitchIconSize,
            "panelSwitchGlyphTextSize" to t.panelSwitchGlyphTextSize,
            "panelSwitchLabelTextSize" to t.panelSwitchLabelTextSize,
            "panelSegmentPaddingHorizontal" to t.panelSegmentPaddingHorizontal,
            "panelSegmentPaddingVertical" to t.panelSegmentPaddingVertical,
            "panelSegmentMargin" to t.panelSegmentMargin,
            "panelSegmentEdgeScrollZone" to t.panelSegmentEdgeScrollZone,
            "panelSegmentEdgeScrollStep" to t.panelSegmentEdgeScrollStep,
            "panelBarIconSize" to t.panelBarIconSize,
            "panelDisabledAlpha" to t.panelDisabledAlpha,
            "panelMenuTextSize" to t.panelMenuTextSize,
            "panelEmptyHintTextSize" to t.panelEmptyHintTextSize,
            "panelEmptyHintAlpha" to t.panelEmptyHintAlpha,
            "barUnrollButtonWidth" to t.barUnrollButtonWidth,
            "barIconButtonPadding" to t.barIconButtonPadding,
            "barIconButtonCornerRadius" to t.barIconButtonCornerRadius,
            "barClipboardIconSize" to t.barClipboardIconSize,
            "barClipboardSpacing" to t.barClipboardSpacing,
            "barClipboardMaxTextWidth" to t.barClipboardMaxTextWidth,
            "barClipboardVerticalMargin" to t.barClipboardVerticalMargin,
            "barClipboardCornerRadius" to t.barClipboardCornerRadius,
            "barClipboardPreviewLength" to t.barClipboardPreviewLength,
            "barInlinePinnedHorizontalMargin" to t.barInlinePinnedHorizontalMargin,
            "barTabSpacing" to t.barTabSpacing,
            "barUnrolledItemMinWidth" to t.barUnrolledItemMinWidth,
        )

        "portrait values" {
            table(ImeTokens.Portrait) shouldBe expectedPortrait
        }

        "landscape values" {
            table(ImeTokens.Landscape) shouldBe expectedLandscape
        }
    })

// field → original value; the same tables passed against the old token objects before the merge
private val expectedPortrait: Map<String, Any> = mapOf(
    // key preview bubble and popup keyboard
    "popupAnchorGap" to 2.dp,
    "popupPreviewCornerRadius" to 8.dp,
    "popupShadowElevation" to 3.dp,
    "popupKeyboardCellWidth" to 44.dp,
    "popupKeyboardCellHeight" to 48.dp,
    "popupKeyboardTextSize" to 24.sp,
    "popupKeyboardPadding" to 4.dp,
    "popupKeyboardCornerRadius" to 10.dp,
    "popupKeyboardHighlightCornerRadius" to 6.dp,
    // symbol panel
    "symbolCellMinWidth" to 48.dp,
    "symbolLongTextHorizontalPadding" to 4.dp,
    "symbolSideBarWidth" to 64.dp,
    // panel lists
    "panelListPadding" to 4.dp,
    "panelCellSpacing" to 6.dp,
    "panelEntryTextSize" to 15.sp,
    "panelEntryPaddingHorizontal" to 10.dp,
    "panelEntryPaddingVertical" to 8.dp,
    "panelEntryMaxLines" to 4,
    "panelEntryPinSize" to 12.dp,
    "panelEntryPinAlpha" to 0.3f,
    "panelSwitchCellMinWidth" to 88.dp,
    "panelSwitchCellHeight" to 96.dp,
    "panelSwitchTileSize" to 48.dp,
    "panelSwitchIconSize" to 24.dp,
    "panelSwitchGlyphTextSize" to 20.sp,
    "panelSwitchLabelTextSize" to 12.sp,
    "panelSegmentPaddingHorizontal" to 8.dp,
    "panelSegmentPaddingVertical" to 4.dp,
    "panelSegmentMargin" to 4.dp,
    "panelSegmentEdgeScrollZone" to 10.dp,
    "panelSegmentEdgeScrollStep" to 12.dp,
    "panelBarIconSize" to 24.dp,
    "panelDisabledAlpha" to 0.38f,
    "panelMenuTextSize" to 16.sp,
    "panelEmptyHintTextSize" to 14.sp,
    "panelEmptyHintAlpha" to 0.6f,
    // input bar
    "barUnrollButtonWidth" to 40.dp,
    "barIconButtonPadding" to 4.dp,
    "barIconButtonCornerRadius" to 8.dp,
    "barClipboardIconSize" to 20.dp,
    "barClipboardSpacing" to 4.dp,
    "barClipboardMaxTextWidth" to 220.dp,
    "barClipboardVerticalMargin" to 4.dp,
    "barClipboardCornerRadius" to 8.dp,
    "barClipboardPreviewLength" to 42,
    "barInlinePinnedHorizontalMargin" to 10.dp,
    "barTabSpacing" to 8.dp,
    "barUnrolledItemMinWidth" to 40.dp,
)

// only the popup keyboard's cells shrink in landscape
private val expectedLandscape: Map<String, Any> = expectedPortrait + mapOf(
    "popupKeyboardCellHeight" to 40.dp,
    "popupKeyboardTextSize" to 22.sp,
)
