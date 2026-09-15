/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.segments

import kotlin.math.max
import kotlin.math.min

/**
 * The whitespace that follows each segment in [rawText]; the tokenizer drops blank segments,
 * so this is what goes back between two neighbours that are both selected.
 */
internal fun segmentSeparators(
    segments: List<String>,
    rawText: String,
): List<String> {
    if (rawText.isEmpty() || segments.isEmpty()) return emptyList()
    val result = ArrayList<String>(segments.size)
    var idx = 0
    for (item in segments) {
        val sb = StringBuilder()
        var pos = idx + item.length
        while (pos < rawText.length) {
            val c = rawText[pos]
            if (c == ' ' || c == '\n' || c == '\t' || c == '\r') {
                sb.append(c)
                pos++
            } else {
                break
            }
        }
        result.add(sb.toString())
        idx = pos
    }
    return result
}

/** Selected segments in order, keeping the whitespace between two selected neighbours. */
internal fun joinSelectedSegments(
    segments: List<String>,
    separators: List<String>,
    isSelected: (Int) -> Boolean,
): String = buildString {
    segments.forEachIndexed { i, item ->
        if (isSelected(i)) {
            append(item)
            if (isSelected(i + 1)) {
                append(separators.getOrElse(i) { "" })
            }
        }
    }
}

/**
 * A left-to-right wrapping layout worked out ahead of composition, so its rows can go into a
 * lazy list and a touch can be mapped back to a segment without asking the layout.
 *
 * Cell positions and widths are in pixels and include the chip's margins.
 */
internal class SegmentRows(
    val rows: List<IntRange>,
    val cellX: IntArray,
    val cellWidth: IntArray,
) {
    /** The segment in [row] under [x], or -1. */
    fun indexAt(
        row: Int,
        x: Float,
    ): Int {
        val range = rows.getOrNull(row) ?: return -1
        for (i in range) {
            if (x >= cellX[i] && x < cellX[i] + cellWidth[i]) return i
        }
        return -1
    }

    companion object {
        /** A cell wider than [maxWidth] gets a row of its own, cut to [maxWidth]. */
        fun layout(
            cellWidths: IntArray,
            maxWidth: Int,
        ): SegmentRows {
            val limit = max(maxWidth, 1)
            val count = cellWidths.size
            val rows = mutableListOf<IntRange>()
            val x = IntArray(count)
            val width = IntArray(count)
            var rowStart = 0
            var cursor = 0
            for (i in 0 until count) {
                val w = cellWidths[i].coerceIn(0, limit)
                if (cursor > 0 && cursor + w > limit) {
                    rows += rowStart until i
                    rowStart = i
                    cursor = 0
                }
                x[i] = cursor
                width[i] = w
                cursor += w
            }
            if (count > rowStart) rows += rowStart until count
            return SegmentRows(rows, x, width)
        }
    }
}

/**
 * Dragging across segments selects or clears the run from where the drag started to where the
 * finger is now, like dragging over files. The first segment decides which: a drag starting on
 * a selected segment clears. Segments the run passes and then leaves get their old state back.
 */
internal class DragSelectRange(
    private val count: Int,
    private val isSelected: (Int) -> Boolean,
    private val setSelected: (Int, Boolean) -> Unit,
) {
    private var start = -1
    private var lastEnd = -1
    private var target = false
    private var snapshot = BooleanArray(0)

    val isActive: Boolean
        get() = start >= 0

    fun begin(position: Int) {
        if (position !in 0 until count) return
        snapshot = BooleanArray(count) { isSelected(it) }
        start = position
        lastEnd = position
        target = !snapshot[position]
        setSelected(position, target)
    }

    fun extendTo(end: Int) {
        if (start < 0 || end !in 0 until count || end == lastEnd) return
        val newLow = min(start, end)
        val newHigh = max(start, end)
        val low = min(newLow, min(start, lastEnd))
        val high = max(newHigh, max(start, lastEnd))
        for (i in low..high) {
            val expected = if (i in newLow..newHigh) target else snapshot[i]
            if (isSelected(i) != expected) setSelected(i, expected)
        }
        lastEnd = end
    }

    fun end() {
        start = -1
        lastEnd = -1
        snapshot = BooleanArray(0)
    }
}
