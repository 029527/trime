/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizeDp
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.ime.keyboard.Key
import com.osfans.trime.ime.keyboard.Keyboard
import com.osfans.trime.ime.keyboard.isIconFont
import com.osfans.trime.ime.keyboard.toIconName
import com.osfans.trime.util.sp
import splitties.dimensions.dp

/**
 * Draws every key of [keyboard] on one canvas.
 *
 * Placeholder: a straight port of `KeyView.onDraw`, still sized and styled by the theme
 * yaml rather than by [com.osfans.trime.ime.compose.theme.ImeTokens].
 */
@Composable
fun KeyboardCanvas(
    keyboard: Keyboard,
    state: KeyboardRenderState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val painter = remember(keyboard, state) { KeyPainter(context, keyboard, state) }
    Canvas(modifier) {
        state.observe()
        drawIntoCanvas { painter.draw(it.nativeCanvas) }
    }
}

private class KeyPainter(
    private val context: Context,
    private val keyboard: Keyboard,
    private val state: KeyboardRenderState,
) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val icons = HashMap<String, IconicsDrawable>()
    private val body = RectF()

    fun draw(canvas: Canvas) {
        keyboard.keys.forEach { drawKey(canvas, it) }
    }

    private fun drawKey(
        canvas: Canvas,
        key: Key,
    ) {
        val hGap = keyboard.horizontalGap / 2f
        val vGap = keyboard.verticalGap / 2f
        body.set(key.x + hGap, key.y + vGap, key.x + key.width - hGap, key.y + key.height - vGap)

        val isEnter = key.getLabel() == ENTER_LABELS
        val primary = isEnter && state.isEnterPrimaryAction
        drawBackground(canvas, key, primary)

        val label = if (isEnter) state.labelEnter else key.getLabel()
        if (label.isNotEmpty()) drawLabel(canvas, key, label, primary)

        val symbol = key.symbolLabel
        if (symbol.isNotEmpty() && !state.hideKeySymbol) drawSymbol(canvas, key, symbol, isTop = true)

        val hint = key.hint
        if (hint.isNotEmpty() && !state.hideKeyHint) drawSymbol(canvas, key, hint, isTop = false)
    }

    private fun drawBackground(
        canvas: Canvas,
        key: Key,
        primary: Boolean,
    ) {
        val actionBg = if (primary) (if (key.isPressed) state.hlActionKeyBackground else state.actionKeyBackground) else null
        val bg = actionBg ?: key.getBackgroundDrawable() ?: return
        if (bg is GradientDrawable) {
            (key.roundCorner ?: keyboard.roundCorner).takeIf { it > 0f }?.let { bg.cornerRadius = context.dp(it) }
            (key.keyBorder ?: keyboard.keyBorder).takeIf { it > 0 }?.let { bg.setStroke(context.dp(it), key.getBorderColor()) }
        }
        bg.setBounds(body.left.toInt(), body.top.toInt(), body.right.toInt(), body.bottom.toInt())
        bg.draw(canvas)
    }

    private fun drawLabel(
        canvas: Canvas,
        key: Key,
        label: String,
        primary: Boolean,
    ) {
        val textColor = (if (primary) state.actionKeyTextColor else null) ?: key.getTextColor()
        val textSize =
            context.sp(
                key.keyTextSize.takeIf { it > 0 }?.let { it * state.textScale }
                    ?: if (label.length > 1 && !label.isIconFont) state.keyLongTextSize else state.keyTextSize,
            )
        if (label.isIconFont) {
            drawIcon(canvas, label, textSize.toInt(), textColor, key.keyTextOffsetX, key.keyTextOffsetY, isTop = null)
            return
        }
        textPaint.color = textColor
        textPaint.textSize = textSize
        textPaint.typeface = FontManager.getTypeface("key_font")
        val fm = textPaint.fontMetrics
        val adjustmentY = -(fm.ascent + fm.descent) / 2f
        canvas.drawText(
            label,
            body.centerX() + context.sp(key.keyTextOffsetX),
            body.centerY() + adjustmentY + context.sp(key.keyTextOffsetY),
            textPaint,
        )
    }

    private fun drawIcon(
        canvas: Canvas,
        iconName: String,
        size: Int,
        color: Int,
        offsetX: Float,
        offsetY: Float,
        isTop: Boolean?,
    ) {
        val half = size / 2
        val name = iconName.toIconName()
        val icon = icons.getOrPut("$name@$size") { IconicsDrawable(context, name).apply { sizeDp = size } }
        icon.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        val cx = body.centerX() + context.sp(offsetX)
        val cy =
            when (isTop) {
                true -> body.top + half + context.sp(offsetY)
                false -> body.bottom - size + context.sp(offsetY)
                null -> body.centerY() + context.sp(offsetY)
            }
        icon.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
        icon.draw(canvas)
    }

    private fun drawSymbol(
        canvas: Canvas,
        key: Key,
        text: String,
        isTop: Boolean,
    ) {
        val textColor = key.getSymbolColor()
        val textSize = context.sp(key.symbolTextSize.takeIf { it > 0f }?.let { it * state.textScale } ?: state.symbolTextSize)
        val offsetX = if (isTop) key.keySymbolOffsetX else key.keyHintOffsetX
        val offsetY = if (isTop) key.keySymbolOffsetY else key.keyHintOffsetY
        if (text.isIconFont) {
            drawIcon(canvas, text, textSize.toInt(), textColor, offsetX, offsetY, isTop)
            return
        }
        symbolPaint.color = textColor
        symbolPaint.textSize = textSize
        symbolPaint.typeface = FontManager.getTypeface("symbol_font")
        val lines = text.split("\n")
        val fm = symbolPaint.fontMetrics
        val lineHeight = fm.descent - fm.ascent
        val totalHeight = lineHeight * lines.size
        val cx = body.centerX() + context.sp(offsetX)
        val startY =
            if (isTop) {
                body.top - fm.top + context.sp(offsetY) - (totalHeight - lineHeight) / 2
            } else {
                body.bottom - fm.bottom + context.sp(offsetY) - (totalHeight - lineHeight) / 2
            }
        lines.forEachIndexed { i, line -> canvas.drawText(line, cx, startY + lineHeight * i, symbolPaint) }
    }

    companion object {
        private const val ENTER_LABELS = "enter_labels"
    }
}
