/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.popup

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import com.mikepenz.iconics.IconicsDrawable
import com.osfans.trime.data.theme.SourceHanSans
import com.osfans.trime.ime.compose.theme.ImeColors
import com.osfans.trime.ime.compose.theme.LocalImeColors
import kotlin.math.roundToInt

/**
 * Key preview bubbles and the long-press keyboard, drawn over the whole input view.
 *
 * Colours come from [ImeColors], so a tint change recolours an open popup too. Fills are drawn
 * opaque: the popup floats over the app's content, where a see-through block would be unreadable.
 */
@Composable
internal fun PopupLayer(state: PopupLayerState) {
    val colors = LocalImeColors.current
    val metrics = state.metrics
    val painter = remember(metrics) { PopupPainter(metrics) }
    Box(Modifier.fillMaxSize()) {
        for (slot in state.bubbles) {
            key(slot) { KeyPreviewBubble(slot, metrics, colors, painter) }
        }
        state.keyboard?.let {
            key(it) { PopupKeyboard(it, metrics, colors, painter) }
        }
    }
}

@Composable
private fun KeyPreviewBubble(
    slot: BubbleSlot,
    metrics: PopupMetrics,
    colors: ImeColors,
    painter: PopupPainter,
) {
    val shape = remember(metrics) { RoundedCornerShape(metrics.previewCornerRadius) }
    val back = colors.popupBack.copy(alpha = 1f)
    val text = colors.popupText.toArgb()
    Spacer(
        Modifier
            .offset { IntOffset(slot.x, slot.y) }
            .sizeInPx(metrics.previewWidth, metrics.previewHeight)
            .graphicsLayer {
                alpha = if (slot.visible) 1f else 0f
                shadowElevation = metrics.shadowElevation
                this.shape = shape
                clip = false
            }.drawBehind {
                drawRoundRect(back, cornerRadius = CornerRadius(metrics.previewCornerRadius))
                painter.drawText(nativeCanvas, slot.text, painter.preview, text, size.width / 2, size.height / 2, size.width)
            },
    )
}

@Composable
private fun PopupKeyboard(
    state: PopupKeyboardState,
    metrics: PopupMetrics,
    colors: ImeColors,
    painter: PopupPainter,
) {
    val layout = state.layout
    val shape = remember(metrics) { RoundedCornerShape(metrics.keyboardCornerRadius) }
    val back = colors.popupBack.copy(alpha = 1f)
    val highlightBack = colors.highlightedPopupBack
    val text = colors.popupText.toArgb()
    val highlightText = colors.highlightedPopupText.toArgb()
    Spacer(
        Modifier
            .offset { IntOffset(layout.left, layout.top) }
            .sizeInPx(layout.width, layout.height)
            .graphicsLayer {
                shadowElevation = metrics.shadowElevation
                this.shape = shape
                clip = false
            }.drawBehind {
                drawRoundRect(back, cornerRadius = CornerRadius(metrics.keyboardCornerRadius))
                val focus = state.focus
                val cellW = layout.cellWidth.toFloat()
                val cellH = layout.cellHeight.toFloat()
                for (i in 0 until layout.keyCount) {
                    val l = layout.cellLeft(i).toFloat()
                    val t = layout.cellTop(i).toFloat()
                    val focused = i == focus
                    if (focused) {
                        drawRoundRect(highlightBack, Offset(l, t), Size(cellW, cellH), CornerRadius(metrics.highlightCornerRadius))
                    }
                    val color = if (focused) highlightText else text
                    val icon = state.icons[i]
                    if (icon != null) {
                        painter.drawIcon(nativeCanvas, icon, color, l + cellW / 2, t + cellH / 2)
                    } else {
                        painter.drawText(nativeCanvas, state.labels[i], painter.cell, color, l + cellW / 2, t + cellH / 2, cellW)
                    }
                }
            },
    )
}

/** Exactly [width] x [height] pixels, whatever the parent allows: the delegate placed it already. */
private fun Modifier.sizeInPx(
    width: Int,
    height: Int,
) = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.place(0, 0) }
}

private val DrawScope.nativeCanvas: Canvas get() = drawContext.canvas.nativeCanvas

/**
 * Text goes through a platform [Paint] like the key labels do (see `KeyboardCanvas`): the popup
 * font is the app's platform typeface with the system fallback chain, and one `drawText` allocates
 * nothing where a paragraph layout would.
 */
internal class PopupPainter(
    metrics: PopupMetrics,
) {
    class Label(
        size: Float,
        weight: Int,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = size
            typeface = SourceHanSans.typeface(weight)
            textAlign = Paint.Align.CENTER
        }
        val centerShift = paint.fontMetrics.let { -(it.ascent + it.descent) / 2 }
    }

    val preview = Label(metrics.previewTextSize, metrics.textWeight)
    val cell = Label(metrics.cellTextSize, metrics.textWeight)
    private val iconSize = metrics.cellTextSize.roundToInt()
    private val colorFilters = SparseArray<ColorFilter>()

    /** Centred on ([cx], [cy]), shrunk to fit [maxWidth] when a label is too wide. */
    fun drawText(
        canvas: Canvas,
        text: String,
        label: Label,
        color: Int,
        cx: Float,
        cy: Float,
        maxWidth: Float,
    ) {
        if (text.isEmpty()) return
        val paint = label.paint
        paint.color = color
        val width = paint.measureText(text)
        if (width > maxWidth) {
            val scale = maxWidth / width
            canvas.save()
            canvas.scale(scale, scale, cx, cy)
            canvas.drawText(text, cx, cy + label.centerShift, paint)
            canvas.restore()
        } else {
            canvas.drawText(text, cx, cy + label.centerShift, paint)
        }
    }

    fun drawIcon(
        canvas: Canvas,
        icon: IconicsDrawable,
        color: Int,
        cx: Float,
        cy: Float,
    ) {
        icon.colorFilter = colorFilters[color] ?: PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN).also { colorFilters.put(color, it) }
        val l = (cx - iconSize / 2f).roundToInt()
        val t = (cy - iconSize / 2f).roundToInt()
        icon.setBounds(l, t, l + iconSize, t + iconSize)
        icon.draw(canvas)
    }
}

/**
 * Host of the popup layer. Never takes a touch, not even one landing on a popup: the finger that
 * opened a popup belongs to the keyboard's gesture handling, which drives the popup from there.
 */
@SuppressLint("ViewConstructor")
internal class PopupLayerView(
    context: Context,
    content: View,
) : FrameLayout(context) {
    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        // (0, 0) at the top left whatever the locale, as the popup positions assume
        layoutDirection = LAYOUT_DIRECTION_LTR
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = false

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean = false
}
