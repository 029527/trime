/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.symbol

import android.graphics.Paint
import android.icu.text.BreakIterator
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.osfans.trime.data.theme.SourceHanSans
import com.osfans.trime.ime.compose.theme.ImeTokens
import com.osfans.trime.ime.compose.theme.LocalImeColors
import com.osfans.trime.ime.compose.theme.LocalImeFonts
import com.osfans.trime.ime.compose.theme.LocalImeTokens
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import kotlin.math.ceil

/**
 * The symbol panel below the bar: a grid of the selected category plus the fixed keys.
 *
 * Cells are keys: key body inset by the key gaps, key corner radius, the highlighted key colour
 * while pressed. A single character (an emoji included) uses the key colours and letter size;
 * anything longer uses the long-text colours at label size and spans as many columns as its text
 * needs, ellipsized past one full row.
 */
@Composable
fun SymbolPanel(
    state: SymbolPanelState,
    onItemClick: (SymbolItem) -> Unit,
    onBarKeyClick: (SymbolBarKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = state.spec
    val bar = spec.barKeys
    when (spec.barPosition) {
        SymbolBarPosition.Top, SymbolBarPosition.Bottom -> Column(modifier.fillMaxSize()) {
            if (spec.barPosition == SymbolBarPosition.Top) SymbolBar(bar, vertical = false, onBarKeyClick)
            SymbolGrid(state, onItemClick, Modifier.weight(1f))
            if (spec.barPosition == SymbolBarPosition.Bottom) SymbolBar(bar, vertical = false, onBarKeyClick)
        }
        SymbolBarPosition.Left, SymbolBarPosition.Right -> Row(modifier.fillMaxSize()) {
            if (spec.barPosition == SymbolBarPosition.Left) SymbolBar(bar, vertical = true, onBarKeyClick)
            SymbolGrid(state, onItemClick, Modifier.weight(1f))
            if (spec.barPosition == SymbolBarPosition.Right) SymbolBar(bar, vertical = true, onBarKeyClick)
        }
    }
}

/**
 * The grid. An emoji page holds a thousand cells and a fling brings in a row every few frames, so a
 * cell is a single layout node that draws its own body and text:
 * - text goes through one pre-configured platform [Paint] per style, like the keyboard canvas does,
 *   instead of a `BasicText` paragraph per cell;
 * - the grid width the column spans need is recorded by a layout modifier, not a subcomposition.
 */
@Composable
private fun SymbolGrid(
    state: SymbolPanelState,
    onItemClick: (SymbolItem) -> Unit,
    modifier: Modifier,
) {
    val tokens = LocalImeTokens.current
    val density = LocalDensity.current
    val spec = state.spec
    val items = state.items
    val cellWidth = spec.cellWidth ?: tokens.symbolCellMinWidth
    val rowHeight = (spec.cellHeight ?: (tokens.keyRowHeight - tokens.keyVerticalGap)) + tokens.keyVerticalGap
    val paints = remember(tokens, density) { CellPaints(tokens, density) }
    val sizer = remember(items, paints) { CellSizer(items, paints) }
    // written while the grid is measured, read by the span lambda during that same measure
    val gridWidth = remember { IntArray(1) }
    val horizontalPadding = tokens.keyboardHorizontalPadding
    LazyVerticalGrid(
        columns = GridCells.Adaptive(cellWidth + tokens.keyHorizontalGap),
        modifier = modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                gridWidth[0] = constraints.maxWidth - 2 * horizontalPadding.roundToPx()
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            },
        state = state.gridState,
        contentPadding = PaddingValues(horizontal = horizontalPadding),
    ) {
        items(
            count = items.size,
            span = { index -> GridItemSpan(sizer.span(index, maxLineSpan, maxCurrentLineSpan, gridWidth[0])) },
            contentType = { sizer.kind(it) },
        ) { index ->
            val kind = sizer.kind(index)
            if (kind == CellSizer.EMPTY) {
                Spacer(Modifier.height(rowHeight))
            } else {
                val item = items[index]
                SymbolCell(item.label, kind == CellSizer.LONG, paints, rowHeight) { onItemClick(item) }
            }
        }
    }
}

@Composable
private fun SymbolCell(
    label: String,
    long: Boolean,
    paints: CellPaints,
    rowHeight: Dp,
    onClick: () -> Unit,
) {
    val colors = LocalImeColors.current
    val tokens = LocalImeTokens.current
    val view = LocalView.current
    val insetX = tokens.keyHorizontalGap / 2
    val insetY = tokens.keyVerticalGap / 2
    val back = if (long) colors.longTextBack else colors.keyBack
    val text = if (long) colors.longText else colors.keyText
    val indication = remember(colors.highlightedKeyBack, tokens) {
        SymbolPressIndication(colors.highlightedKeyBack, tokens.keyCornerRadius, insetX, insetY)
    }
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .drawBehind { drawKeyBody(back, tokens.keyCornerRadius, insetX, insetY) }
            // the pressed body is drawn by the indication, between the body above and the text below
            .clickable(interactionSource = null, indication = indication) {
                InputFeedbackManager.keyPressVibrate(view)
                onClick()
            }.drawBehind { paints.draw(this, label, long, text) },
    )
}

/** The two text styles of a cell as platform paints, with what [draw] needs precomputed. */
private class CellPaints(
    tokens: ImeTokens,
    density: Density,
) {
    val glyphPaint = paint(with(density) { tokens.keyTextSize.toPx() }, tokens.keyTextWeight.weight)
    val longPaint = paint(with(density) { tokens.keyLabelTextSize.toPx() }, tokens.panelLabelWeight.weight)

    /** Space around long text inside the cell, gaps included, per side. */
    val longPadding = with(density) { (tokens.keyHorizontalGap / 2 + tokens.symbolLongTextHorizontalPadding).toPx() }

    private val glyphBaseline = centeredBaseline(glyphPaint)
    private val longBaseline = centeredBaseline(longPaint)

    private fun paint(
        size: Float,
        weight: Int,
    ) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = SourceHanSans.typeface(weight)
        textSize = size
        textAlign = Paint.Align.CENTER
    }

    /** Offset from the vertical centre to the baseline, the same ascent / descent rule as the keys. */
    private fun centeredBaseline(paint: Paint) = paint.fontMetrics.let { -(it.ascent + it.descent) / 2 }

    fun draw(
        scope: DrawScope,
        label: String,
        long: Boolean,
        color: Color,
    ) = with(scope) {
        val paint = if (long) longPaint else glyphPaint
        var shown: CharSequence = label
        if (long) {
            val available = size.width - 2 * longPadding
            if (available <= 0f) return
            if (paint.measureText(label) > available) shown = TextUtils.ellipsize(label, paint, available, TextUtils.TruncateAt.END)
        }
        paint.color = color.toArgb()
        val baseline = size.height / 2 + if (long) longBaseline else glyphBaseline
        drawIntoCanvas { it.nativeCanvas.drawText(shown, 0, shown.length, size.width / 2, baseline, paint) }
    }
}

/**
 * Sorts a page's items into single glyphs, long text, and row breaks, and works out how many
 * columns long text needs. Both are computed on first use and kept, so scrolling back up a page of
 * hundreds of emoji never segments or measures a label twice.
 */
private class CellSizer(
    private val items: List<SymbolItem>,
    private val paints: CellPaints,
) {
    private val kinds = ByteArray(items.size)
    private val widths = FloatArray(items.size) { -1f }

    fun kind(index: Int): Int {
        var kind = kinds[index].toInt()
        if (kind == UNKNOWN) {
            val label = items[index].label
            kind = when {
                label.isEmpty() -> EMPTY
                isSingleGlyph(label) -> GLYPH
                else -> LONG
            }
            kinds[index] = kind.toByte()
        }
        return kind
    }

    fun span(
        index: Int,
        maxLineSpan: Int,
        maxCurrentLineSpan: Int,
        gridWidth: Int,
    ): Int = when (kind(index)) {
        GLYPH -> 1
        // take the rest of the row, so the next item starts a new one
        EMPTY -> maxCurrentLineSpan
        else -> {
            var width = widths[index]
            if (width < 0f) {
                width = paints.longPaint.measureText(items[index].label) + 2 * paints.longPadding
                widths[index] = width
            }
            val column = gridWidth.toFloat() / maxLineSpan
            if (column <= 0f) 1 else ceil(width / column).toInt().coerceIn(1, maxLineSpan)
        }
    }

    companion object {
        const val UNKNOWN = 0
        const val GLYPH = 1
        const val LONG = 2
        const val EMPTY = 3

        private val characters: BreakIterator = BreakIterator.getCharacterInstance()

        /** One user-perceived character: a letter, a symbol, or an emoji sequence. */
        fun isSingleGlyph(text: String): Boolean {
            if (text.length == 1 || Character.charCount(text.codePointAt(0)) == text.length) return true
            characters.setText(text)
            characters.first()
            return characters.next() == text.length
        }
    }
}

@Composable
private fun SymbolBar(
    keys: List<SymbolBarKey>,
    vertical: Boolean,
    onClick: (SymbolBarKey) -> Unit,
) {
    if (keys.isEmpty()) return
    val tokens = LocalImeTokens.current
    if (vertical) {
        Column(Modifier.width(tokens.symbolSideBarWidth).fillMaxHeight()) {
            keys.forEach { SymbolBarKeyView(it, onClick, Modifier.weight(1f).fillMaxWidth()) }
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .height(tokens.keyRowHeight)
                .padding(horizontal = tokens.keyboardHorizontalPadding),
        ) {
            keys.forEach { SymbolBarKeyView(it, onClick, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

@Composable
private fun SymbolBarKeyView(
    key: SymbolBarKey,
    onClick: (SymbolBarKey) -> Unit,
    modifier: Modifier,
) {
    val colors = LocalImeColors.current
    val tokens = LocalImeTokens.current
    val fonts = LocalImeFonts.current
    val view = LocalView.current
    var pressed by remember { mutableStateOf(false) }
    val insetX = tokens.keyHorizontalGap / 2
    val insetY = tokens.keyVerticalGap / 2
    val style = remember(fonts, tokens) {
        TextStyle(fontFamily = fonts.family, fontWeight = tokens.keyLabelWeight, fontSize = tokens.keyLabelTextSize, textAlign = TextAlign.Center)
    }
    val onPressedChange = remember { { value: Boolean -> pressed = value } }
    val onTrigger = remember(key, onClick) { { onClick(key) } }
    Box(
        modifier = modifier
            .drawBehind {
                // read in draw only: a press repaints this key and nothing else
                val back = when {
                    !pressed -> if (key.functional) colors.functionKeyBack else colors.keyBack
                    key.functional && tokens.keyPressedFunctionSwap -> colors.keyBack
                    key.functional -> colors.highlightedFunctionKeyBack
                    else -> colors.highlightedKeyBack
                }
                drawKeyBody(back, tokens.keyCornerRadius, insetX, insetY)
            }.barKeyGestures(key.repeatable, view, onPressedChange, onTrigger)
            .padding(horizontal = insetX, vertical = insetY),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = key.label,
            style = style,
            color = { if (key.functional) colors.functionKeyText else colors.keyText },
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
