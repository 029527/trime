/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Trace
import android.util.SparseArray
import android.view.KeyCharacterMap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizePx
import com.osfans.trime.data.theme.SourceHanSans
import com.osfans.trime.ime.compose.theme.ImeIcons
import com.osfans.trime.ime.compose.theme.ImeTokens
import com.osfans.trime.ime.compose.theme.LocalImeTokens
import com.osfans.trime.ime.compose.voice.DictationGlyphs
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import com.osfans.trime.ime.keyboard.Key
import com.osfans.trime.ime.keyboard.Keyboard
import com.osfans.trime.ime.keyboard.isIconFont
import com.osfans.trime.ime.keyboard.toIconName
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.ColorFilter as ComposeColorFilter

/**
 * Draws every key of [keyboard] on one canvas, sized by [ImeTokens] and coloured by the theme.
 *
 * Geometry: each key's touch cell (from [Keyboard]) is inset by half of [ImeTokens.keyHorizontalGap]
 * / [ImeTokens.keyVerticalGap] on every side. Label size follows the label: an `ic@` icon gets
 * [ImeTokens.keyIconSize], one character [ImeTokens.keyTextSize], a run of letters on a key that
 * types a character (nine-key `ABC`) [ImeTokens.keyLetterGroupTextSize], anything else
 * [ImeTokens.keyLabelTextSize]. Per-key sizes and offsets in the yaml are ignored; see
 * docs/ime-design-system.md §3 for the full override rule.
 *
 * Key backgrounds go through Compose's `drawRoundRect`: it draws with a pooled paint and only
 * inline value classes, so it allocates nothing and costs the same as the platform call. Text and
 * icons stay on the native canvas on purpose:
 * - the key font is a platform [Typeface] from [SourceHanSans], one per weight, with the system fallback chain;
 * - `TextMeasurer` would lay out a paragraph per label (and again whenever shift or the ascii mode
 *   changes a label), then draw it through a `MultiParagraph`, where one `drawText` on a
 *   pre-configured [Paint] does the job;
 * - label centring matches the View keyboard's ascent/descent rule, which paragraph layout does
 *   not expose directly;
 * - `ic@` glyphs are Material vectors (see [ImeIcons]) drawn through remembered vector painters,
 *   or `IconicsDrawable`s for a name `ImeIcons` does not map.
 *
 * The enter key is the one key in the accent colour, drawn as a pill ([ImeTokens.enterKeyPill]). While
 * dictating, the mic key joins it: an accent capsule with a filled mic and a halo that follows the voice.
 */
@Composable
fun KeyboardCanvas(
    keyboard: Keyboard,
    state: KeyboardRenderState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val tokens = LocalImeTokens.current
    val density = LocalDensity.current
    // one vector painter per glyph the layout names; a painter caches its raster, so each glyph is
    // rasterised once per size instead of once per frame
    val iconNames =
        remember(keyboard) {
            keyboard.keys
                .flatMap { listOf(it.getLabel(), it.symbolLabel) }
                .filter { it.isIconFont && ImeIcons.vector(it) != null }
                .distinct()
        }
    val vectorIcons = remember(keyboard) { HashMap<String, Painter>() }
    for (name in iconNames) {
        key(name) { vectorIcons[name] = rememberVectorPainter(ImeIcons.vector(name)!!) }
    }
    val activeMic = rememberVectorPainter(DictationGlyphs.Mic)
    val painter = remember(keyboard, state, tokens, density, activeMic) { KeyPainter(context, keyboard, state, tokens, density, vectorIcons, activeMic) }
    Canvas(modifier) {
        state.observe()
        Trace.beginSection("KeyboardCanvas")
        painter.draw(this)
        Trace.endSection()
    }
}

/**
 * Everything a frame needs, resolved once per keyboard layout, token set and density: key bodies,
 * paints with their metrics, border strokes, and icon drawables (filled lazily per key, rebuilt
 * only when that key's label changes). [draw] then only reads key state and sets colours.
 */
private class KeyPainter(
    private val context: Context,
    keyboard: Keyboard,
    private val state: KeyboardRenderState,
    private val tokens: ImeTokens,
    density: Density,
    private val vectorIcons: Map<String, Painter>,
    /** The filled mic of the mic key while dictating. */
    private val activeMicIcon: Painter,
) {
    private val keys = keyboard.keys
    private val count = keys.size

    /** left, top, right, bottom of each key body. */
    private val bodies = FloatArray(count * 4)

    /** Whether the key types a character, as opposed to switching keyboards or sending a command. */
    private val typesCharacter = BooleanArray(count)
    private val borders = arrayOfNulls<Stroke>(count)

    /** Keys that start dictation (`command: voice_input`). */
    private val voiceKeys = BooleanArray(count)
    private val hasVoiceKey: Boolean
    private val voiceLevelSpread: Float

    private val cornerRadius: CornerRadius
    private val iconSize: Float
    private val symbolIconSize: Float
    private val symbolInsetTop: Float
    private val symbolInsetEnd: Float

    private val letter: TextStyle
    private val letterGroup: TextStyle
    private val label: TextStyle
    private val symbolPaint: Paint
    private val hintPaint: Paint
    private val symbolAscent: Float
    private val symbolDescent: Float
    private val symbolLineHeight: Float

    private val labelIcons = arrayOfNulls<IconSlot>(count)
    private val symbolIcons = arrayOfNulls<IconSlot>(count)
    private val colorFilters = SparseArray<ColorFilter>()
    private val vectorFilters = SparseArray<ComposeColorFilter>()

    init {
        val scale = state.textScale
        val keyFont = SourceHanSans.typeface(tokens.keyTextWeight)
        val labelFont = SourceHanSans.typeface(tokens.keyLabelWeight)
        val symbolFont = SourceHanSans.typeface(tokens.keySymbolWeight)
        with(density) {
            val halfH = tokens.keyHorizontalGap.toPx() / 2
            val halfV = tokens.keyVerticalGap.toPx() / 2
            val characterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
            keys.forEachIndexed { i, key ->
                bodies[i * 4] = key.x + halfH
                bodies[i * 4 + 1] = key.y + halfV
                bodies[i * 4 + 2] = key.x + key.width - halfH
                bodies[i * 4 + 3] = key.y + key.height - halfV
                typesCharacter[i] = characterMap.isPrintingKey(key.code)
                voiceKeys[i] = key.click?.command == CommonKeyboardActionListener.VOICE_INPUT_COMMAND
                (key.keyBorder ?: keyboard.keyBorder).takeIf { it > 0 }?.let { borders[i] = Stroke(it.dp.toPx()) }
            }
            cornerRadius = CornerRadius(tokens.keyCornerRadius.toPx())
            iconSize = tokens.keyIconSize.toPx() * scale
            symbolInsetTop = tokens.keySymbolInsetTop.toPx() * scale
            symbolInsetEnd = tokens.keySymbolInsetEnd.toPx() * scale
            letter = TextStyle(textPaint(tokens.keyTextSize, scale, keyFont, Paint.Align.CENTER))
            letterGroup = TextStyle(textPaint(tokens.keyLetterGroupTextSize, scale, keyFont, Paint.Align.CENTER))
            label = TextStyle(textPaint(tokens.keyLabelTextSize, scale, labelFont, Paint.Align.CENTER))
            symbolPaint = textPaint(tokens.keySymbolTextSize, scale, symbolFont, Paint.Align.RIGHT)
            hintPaint = textPaint(tokens.keySymbolTextSize, scale, symbolFont, Paint.Align.CENTER)
            symbolIconSize = tokens.keySymbolIconSize.toPx() * scale
            voiceLevelSpread = tokens.voiceKeyLevelSpread.toPx()
        }
        hasVoiceKey = voiceKeys.any { it }
        val fm = symbolPaint.fontMetrics
        symbolAscent = fm.ascent
        symbolDescent = fm.descent
        symbolLineHeight = fm.descent - fm.ascent
    }

    fun draw(scope: DrawScope) {
        val canvas = scope.drawContext.canvas.nativeCanvas
        val hideSymbol = state.hideKeySymbol
        val hideHint = state.hideKeyHint
        // only a layout with a mic key reads dictation, so the others do not redraw with the voice level
        val dictating = hasVoiceKey && state.voice.keyActive
        val dictationLevel = if (dictating) state.voice.level else 0f
        // keys under an overlay are left out, so the overlay shows the same keyboard surface instead of painting its own
        val covered = state.coveredArea
        for (i in 0 until count) {
            val key = keys[i]
            if (covered != null && key.x >= covered.left && key.y >= covered.top &&
                key.x + key.width <= covered.right && key.y + key.height <= covered.bottom
            ) {
                continue
            }
            val l = bodies[i * 4]
            val t = bodies[i * 4 + 1]
            val r = bodies[i * 4 + 2]
            val b = bodies[i * 4 + 3]

            if (dictating && voiceKeys[i]) {
                scope.drawDictationKey(l, t, r, b, dictationLevel)
                continue
            }

            var text = key.getLabel()
            // the enter key always wears the accent, not only when the editor asks to go / search / send
            val isEnter = text == ENTER_LABELS
            if (isEnter) text = state.labelEnter

            scope.drawBackground(canvas, i, key, isEnter, l, t, r, b)

            val textColor = (if (isEnter) state.actionKeyTextColor else null) ?: key.getTextColor()
            if (text.isNotEmpty()) scope.drawLabel(canvas, i, text, textColor, (l + r) / 2, (t + b) / 2)

            val secondary = symbolColor(textColor)
            val symbol = key.symbolLabel
            if (!hideSymbol && symbol.isNotBlank()) scope.drawSymbol(canvas, i, symbol, secondary, r - symbolInsetEnd, t + symbolInsetTop)
            val hint = key.hint
            if (!hideHint && hint.isNotBlank()) drawHint(canvas, hint, secondary, (l + r) / 2, b - symbolInsetTop)
        }
    }

    private fun DrawScope.drawBackground(
        canvas: Canvas,
        i: Int,
        key: Key,
        accent: Boolean,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
    ) {
        val actionBackground = if (accent) (if (key.isPressed) state.hlActionKeyBackground else state.actionKeyBackground) else null
        val background = actionBackground ?: key.getBackgroundDrawable()
        val radius = if (accent && tokens.enterKeyPill) CornerRadius((b - t) / 2) else cornerRadius
        // ColorManager turns every plain colour into a GradientDrawable; images and nine-patches
        // are drawn as they are, without rounding.
        val solid = (background as? GradientDrawable)?.color?.defaultColor
        if (solid != null) {
            if (solid ushr 24 != 0) {
                drawRoundRect(Color(solid), Offset(l, t), Size(r - l, b - t), radius, alpha = background.alpha / 255f)
            }
        } else if (background != null) {
            background.setBounds(l.toInt(), t.toInt(), r.toInt(), b.toInt())
            background.draw(canvas)
        }
        val border = borders[i] ?: return
        val inset = border.width / 2
        drawRoundRect(
            Color(key.getBorderColor()),
            Offset(l + inset, t + inset),
            Size(r - l - border.width, b - t - border.width),
            radius,
            style = border,
        )
    }

    /** The mic key while dictating: an accent capsule with a filled mic, haloed by the voice [level]. */
    private fun DrawScope.drawDictationKey(
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        level: Float,
    ) {
        val accent = Color(state.accentBackColor)
        val height = b - t
        val spread = voiceLevelSpread * level
        if (spread >= 0.5f) {
            drawRoundRect(
                accent,
                Offset(l - spread, t - spread),
                Size(r - l + 2 * spread, height + 2 * spread),
                CornerRadius(height / 2 + spread),
                alpha = tokens.voiceKeyLevelAlpha,
            )
        }
        drawRoundRect(accent, Offset(l, t), Size(r - l, height), CornerRadius(height / 2))
        val color = state.accentTextColor
        val filter = vectorFilters[color] ?: ComposeColorFilter.tint(Color(color)).also { vectorFilters.put(color, it) }
        translate((l + r) / 2 - iconSize / 2, (t + b) / 2 - iconSize / 2) {
            with(activeMicIcon) { draw(Size(iconSize, iconSize), colorFilter = filter) }
        }
    }

    private fun DrawScope.drawLabel(
        canvas: Canvas,
        i: Int,
        text: String,
        color: Int,
        cx: Float,
        cy: Float,
    ) {
        if (text.isIconFont) {
            drawIcon(canvas, labelIcons, i, text, iconSize, color, cx - iconSize / 2, cy - iconSize / 2)
            return
        }
        val style =
            when {
                text.codePointCount(0, text.length) == 1 -> letter
                typesCharacter[i] && text.all { it in 'A'..'Z' || it in 'a'..'z' } -> letterGroup
                else -> label
            }
        style.paint.color = color
        canvas.drawText(text, cx, cy + style.centerShift, style.paint)
    }

    /** Top-end corner: [right] and [top] are the inner edges of the inset. */
    private fun DrawScope.drawSymbol(
        canvas: Canvas,
        i: Int,
        text: String,
        color: Int,
        right: Float,
        top: Float,
    ) {
        if (text.isIconFont) {
            drawIcon(canvas, symbolIcons, i, text, symbolIconSize, color, right - symbolIconSize, top)
            return
        }
        symbolPaint.color = color
        var baseline = top - symbolAscent
        var start = 0
        while (true) {
            val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
            canvas.drawText(text, start, end, right, baseline, symbolPaint)
            if (end == text.length) break
            start = end + 1
            baseline += symbolLineHeight
        }
    }

    /** Bottom centre, mirroring the symbol's inset; multi-line hints grow upwards. */
    private fun drawHint(
        canvas: Canvas,
        text: String,
        color: Int,
        cx: Float,
        bottom: Float,
    ) {
        hintPaint.color = color
        var lines = 1
        for (c in text) if (c == '\n') lines++
        var baseline = bottom - symbolDescent - (lines - 1) * symbolLineHeight
        var start = 0
        while (true) {
            val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
            canvas.drawText(text, start, end, cx, baseline, hintPaint)
            if (end == text.length) break
            start = end + 1
            baseline += symbolLineHeight
        }
    }

    /**
     * A Material glyph from [vectorIcons] when the name is mapped (see `ImeIcons`), otherwise the
     * Community Material glyph. The vector painter keeps its raster between frames; the tint filter
     * is cached per colour, so a frame allocates nothing.
     */
    private fun DrawScope.drawIcon(
        canvas: Canvas,
        slots: Array<IconSlot?>,
        i: Int,
        name: String,
        size: Float,
        color: Int,
        left: Float,
        top: Float,
    ) {
        val vector = vectorIcons[name]
        if (vector != null) {
            val filter = vectorFilters[color] ?: ComposeColorFilter.tint(Color(color)).also { vectorFilters.put(color, it) }
            translate(left, top) {
                with(vector) { draw(Size(size, size), colorFilter = filter) }
            }
            return
        }
        val px = size.roundToInt()
        var slot = slots[i]
        if (slot == null || slot.name != name) {
            slot = IconSlot(name, IconicsDrawable(context, name.toIconName()).apply { sizePx = px })
            slots[i] = slot
        }
        if (!slot.tinted || slot.color != color) {
            slot.drawable.colorFilter = colorFilters[color] ?: PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN).also { colorFilters.put(color, it) }
            slot.color = color
            slot.tinted = true
        }
        val x = left.roundToInt()
        val y = top.roundToInt()
        slot.drawable.setBounds(x, y, x + px, y + px)
        slot.drawable.draw(canvas)
    }

    /** Key text colour with its alpha scaled by [ImeTokens.keySymbolAlpha]. */
    private fun symbolColor(textColor: Int): Int {
        val alpha = ((textColor ushr 24) * tokens.keySymbolAlpha).roundToInt()
        return (alpha shl 24) or (textColor and 0xFFFFFF)
    }

    private fun Density.textPaint(
        size: TextUnit,
        scale: Float,
        font: Typeface,
        align: Paint.Align,
    ) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textSize = size.toPx() * scale
        typeface = font
        textAlign = align
    }

    /** A label paint and the baseline shift that centres its ascent-to-descent box on a point. */
    private class TextStyle(
        val paint: Paint,
    ) {
        val centerShift = paint.fontMetrics.let { -(it.ascent + it.descent) / 2 }
    }

    private class IconSlot(
        val name: String,
        val drawable: IconicsDrawable,
    ) {
        var color = 0
        var tinted = false
    }

    companion object {
        private const val ENTER_LABELS = "enter_labels"
    }
}
