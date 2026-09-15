/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.dictation

import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * Where the dictation pill goes. Pure arithmetic, so it is tested without a device.
 *
 * Coordinates: the editor reports its caret in its own view's coordinates plus a matrix to the
 * screen (`CursorAnchorInfo.getMatrix`); [mapPoint] applies that matrix, and the caller subtracts
 * the overlay's position on screen before calling [place].
 */
object DictationPillPlacement {
    /**
     * Maps ([x], [y]) through a 3x3 matrix in `android.graphics.Matrix.getValues` order
     * (scaleX, skewX, transX, skewY, scaleY, transY, persp0, persp1, persp2) into [out].
     */
    fun mapPoint(
        values: FloatArray,
        x: Float,
        y: Float,
        out: FloatArray,
    ) {
        val w = values[6] * x + values[7] * y + values[8]
        val divisor = if (w == 0f) 1f else w
        out[0] = (values[0] * x + values[1] * y + values[2]) / divisor
        out[1] = (values[3] * x + values[4] * y + values[5]) / divisor
    }

    /**
     * Top-left of a [pillWidth] x [pillHeight] pill in an overlay [areaWidth] wide whose keyboard
     * starts at ([keyboardLeft], [keyboardTop]).
     *
     * With a usable caret (finite, inside the area, above the keyboard) the pill sits just after
     * the caret, [caretGap] away, centred on the caret's line, like the dictation glyph on iOS.
     * Otherwise it falls back to the start of the keyboard's top edge, just above the bar.
     * Either way it keeps [margin] from the area's sides and from the keyboard.
     */
    fun place(
        caretX: Float,
        caretTop: Float,
        caretBottom: Float,
        pillWidth: Int,
        pillHeight: Int,
        areaWidth: Int,
        keyboardLeft: Int,
        keyboardTop: Int,
        caretGap: Int,
        margin: Int,
    ): IntOffset {
        val maxX = (areaWidth - margin - pillWidth).coerceAtLeast(margin)
        val maxY = keyboardTop - margin - pillHeight
        if (!isCaretUsable(caretX, caretTop, caretBottom, areaWidth, keyboardTop)) {
            return IntOffset((keyboardLeft + margin).coerceIn(margin, maxX), maxY)
        }
        val x = (caretX.roundToInt() + caretGap).coerceIn(margin, maxX)
        val centerY = (caretTop + caretBottom) / 2
        val y = (centerY - pillHeight / 2f).roundToInt().coerceIn(minOf(margin, maxY), maxY)
        return IntOffset(x, y)
    }

    fun isCaretUsable(
        caretX: Float,
        caretTop: Float,
        caretBottom: Float,
        areaWidth: Int,
        keyboardTop: Int,
    ): Boolean {
        if (!caretX.isFinite() || !caretTop.isFinite() || !caretBottom.isFinite()) return false
        if (caretBottom < caretTop) return false
        // scrolled out of sight, or covered by the keyboard
        if (caretBottom <= 0f || caretTop >= keyboardTop) return false
        return caretX >= 0f && caretX <= areaWidth
    }
}
