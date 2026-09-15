/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.popup

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import com.mikepenz.iconics.IconicsDrawable
import com.osfans.trime.ime.compose.theme.ImeTokens
import kotlin.math.roundToInt

/**
 * Popup sizes in pixels. Resolved once, so that the gesture side (focus hit-testing) and the
 * drawing side round the same way.
 */
@Immutable
internal class PopupMetrics(
    val previewWidth: Int,
    val previewHeight: Int,
    val previewTextSize: Float,
    val previewCornerRadius: Float,
    /** Added to the top of a key's rectangle to get the bottom of its popup. */
    val anchorOffset: Int,
    val shadowElevation: Float,
    val cellWidth: Int,
    val cellHeight: Int,
    val cellTextSize: Float,
    val keyboardPadding: Int,
    val keyboardCornerRadius: Float,
    val highlightCornerRadius: Float,
    /** Weight of bubble and cell text on the app font's axis. */
    val textWeight: Int,
) {
    companion object {
        fun of(
            density: Density,
            tokens: ImeTokens,
        ) = with(density) {
            PopupMetrics(
                previewWidth = tokens.keyPreviewWidth.roundToPx(),
                previewHeight = tokens.keyPreviewHeight.roundToPx(),
                previewTextSize = tokens.keyPreviewTextSize.toPx(),
                previewCornerRadius = tokens.popupPreviewCornerRadius.toPx(),
                // the key rectangle includes half the row gap; the body starts below it
                anchorOffset = (tokens.keyVerticalGap.toPx() / 2).roundToInt() - tokens.popupAnchorGap.roundToPx(),
                shadowElevation = tokens.popupShadowElevation.toPx(),
                cellWidth = tokens.popupKeyboardCellWidth.roundToPx(),
                cellHeight = tokens.popupKeyboardCellHeight.roundToPx(),
                cellTextSize = tokens.popupKeyboardTextSize.toPx(),
                keyboardPadding = tokens.popupKeyboardPadding.roundToPx(),
                keyboardCornerRadius = tokens.popupKeyboardCornerRadius.toPx(),
                highlightCornerRadius = tokens.popupKeyboardHighlightCornerRadius.toPx(),
                textWeight = tokens.popupWeight.weight,
            )
        }
    }
}

/**
 * One key preview bubble. Bubbles are pooled and never leave the composition: showing, moving and
 * relabelling one only writes these fields, which are read in the placement, layer and draw
 * phases respectively, so a key press recomposes nothing.
 */
@Stable
internal class BubbleSlot {
    var x by mutableIntStateOf(0)
    var y by mutableIntStateOf(0)
    var text by mutableStateOf("")
    var visible by mutableStateOf(false)

    /** Key the bubble belongs to while showing. */
    var viewId = -1
    var shownAt = 0L
    lateinit var hideTask: Runnable
}

/** A long-press keyboard; built per long press, only [focus] changes while it is up. */
@Stable
internal class PopupKeyboardState(
    val viewId: Int,
    val keys: List<String>,
    val labels: List<String>,
    /** Non-null where the label is an `ic@` icon. */
    val icons: Array<IconicsDrawable?>,
    val layout: PopupKeyboardLayout,
    /** Top-left of the held key, to turn gesture coordinates into layer coordinates. */
    val triggerLeft: Int,
    val triggerTop: Int,
) {
    var focus by mutableIntStateOf(layout.initialFocus)
}

@Stable
internal class PopupLayerState(
    val metrics: PopupMetrics,
) {
    /** Grows to the most bubbles ever shown at once, then stays. */
    val bubbles = mutableStateListOf<BubbleSlot>()

    var keyboard by mutableStateOf<PopupKeyboardState?>(null)
}
