/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.bar

import android.graphics.drawable.Drawable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import kotlin.math.roundToInt

/**
 * Draws an Android [Drawable] as a Compose [Painter]: theme images, `ic@` iconics glyphs and
 * the bar background, which a theme may set to an image with a border.
 *
 * Tint the drawable before handing it over; the painter only sets bounds and draws.
 */
internal class DrawablePainter(
    private val drawable: Drawable,
) : Painter() {
    override val intrinsicSize: Size
        get() {
            val w = drawable.intrinsicWidth
            val h = drawable.intrinsicHeight
            return if (w > 0 && h > 0) Size(w.toFloat(), h.toFloat()) else Size.Unspecified
        }

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
            drawable.draw(canvas.nativeCanvas)
        }
    }
}
