/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.popup

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** Pixel geometry of popups, in the popup layer's own coordinates. */
internal object PopupGeometry {
    /** Left edge of a box [width] wide centred on [centerX], kept inside `0..containerWidth`. */
    fun centeredLeft(
        centerX: Int,
        width: Int,
        containerWidth: Int,
    ): Int = (centerX - width / 2).coerceIn(0, max(0, containerWidth - width))

    /** Top edge of a box [height] tall whose bottom is [bottom], kept below the top of the layer. */
    fun topAbove(
        bottom: Int,
        height: Int,
    ): Int = max(0, bottom - height)

    /**
     * The column that starts under the finger: the middle one, shifted towards the side that has
     * room when [leftSpace] or [rightSpace] cannot fit the columns on that side.
     */
    fun initialFocusColumn(
        columnCount: Int,
        columnWidth: Int,
        leftSpace: Int,
        rightSpace: Int,
    ): Int {
        var col = (columnCount - 1) / 2
        while (col > 0 && columnWidth * col > leftSpace) col--
        while (col < columnCount - 1 && columnWidth * (columnCount - col - 1) > rightSpace) col++
        return col.coerceIn(0, columnCount - 1)
    }

    /**
     * Priority of each column, counting outwards from [initialFocus]: centre, right, left.
     * ```
     * | 6 | 4 | 2 | 0 | 1 | 3 | 5 |
     * ```
     * A side that runs out of columns is skipped:
     * ```
     * | 3 | 2 | 1 | 0 |
     * ```
     */
    fun columnOrder(
        columnCount: Int,
        initialFocus: Int,
    ) = IntArray(columnCount).also {
        var order = 0
        it[initialFocus] = order++
        for (i in 1 until columnCount * 2) {
            val sign = if (i % 2 == 0) -1 else 1
            val delta = (i / 2f).roundToInt()
            val next = initialFocus + sign * delta
            if (next < 0 || next >= columnCount) continue
            it[next] = order++
        }
    }
}

/**
 * Where the long-press keyboard goes and which of its keys a finger is on.
 *
 * Keys fill rows of at most [MAX_COLUMNS], bottom row first, each row from the initially focused
 * column outwards (see [PopupGeometry.columnOrder]), so the first key starts under the finger
 * and the next ones are the shortest slide away.
 *
 * @param triggerLeft left of the held key, in layer coordinates; so are the other positions.
 * @param bottom where the bottom edge of the keyboard wants to be.
 */
internal class PopupKeyboardLayout(
    val keyCount: Int,
    containerWidth: Int,
    triggerLeft: Int,
    triggerRight: Int,
    bottom: Int,
    val cellWidth: Int,
    val cellHeight: Int,
    val padding: Int,
) {
    val rowCount = max(1, (keyCount + MAX_COLUMNS - 1) / MAX_COLUMNS)
    val columnCount = max(1, (keyCount + rowCount - 1) / rowCount)

    val width = columnCount * cellWidth + padding * 2
    val height = rowCount * cellHeight + padding * 2

    private val focusColumn: Int
    private val order: IntArray

    val left: Int
    val top: Int = PopupGeometry.topAbove(bottom, height)

    /** Cell positions inside the keyboard, by key index. */
    private val cellLefts = IntArray(keyCount)
    private val cellTops = IntArray(keyCount)

    init {
        val centerX = (triggerLeft + triggerRight) / 2
        // space the columns beside the focused one can take, focused cell centred on the key
        focusColumn = PopupGeometry.initialFocusColumn(
            columnCount,
            cellWidth,
            leftSpace = centerX - cellWidth / 2 - padding,
            rightSpace = containerWidth - (centerX + cellWidth / 2) - padding,
        )
        order = PopupGeometry.columnOrder(columnCount, focusColumn)
        val wantedLeft = centerX - cellWidth / 2 - focusColumn * cellWidth - padding
        left = wantedLeft.coerceIn(0, max(0, containerWidth - width))
        for (row in 0 until rowCount) {
            for (col in 0 until columnCount) {
                val index = keyIndex(row, col)
                if (index >= keyCount) continue
                cellLefts[index] = padding + col * cellWidth
                cellTops[index] = padding + (rowCount - 1 - row) * cellHeight
            }
        }
    }

    /** Row 0 is the bottom row. */
    private fun keyIndex(
        row: Int,
        col: Int,
    ) = row * columnCount + order[col]

    val initialFocus: Int get() = keyIndex(0, focusColumn)

    fun cellLeft(index: Int) = cellLefts[index]

    fun cellTop(index: Int) = cellTops[index]

    /**
     * The key under ([x], [y]), in layer coordinates.
     *
     * A row is entered once the finger is [ROW_ENTER] of a cell above its bottom edge, so a finger
     * resting on the held key (below the keyboard) stays on the bottom row. Outside the keyboard
     * the nearest cell is kept, up to two cells away.
     *
     * @return the key index, [EMPTY] over a cell with no key, or [OUTSIDE] when the finger left.
     */
    fun focusAt(
        x: Float,
        y: Float,
    ): Int {
        val col = floor((x - left - padding) / cellWidth).toInt()
        val row = floor((top + height - padding - y) / cellHeight + (1 - ROW_ENTER)).toInt()
        if (row < -2 || row > rowCount + 1 || col < -2 || col > columnCount + 1) return OUTSIDE
        val index = keyIndex(row.coerceIn(0, rowCount - 1), col.coerceIn(0, columnCount - 1))
        return if (index < keyCount) index else EMPTY
    }

    companion object {
        const val MAX_COLUMNS = 5
        const val ROW_ENTER = 0.3f
        const val EMPTY = -1
        const val OUTSIDE = -2
    }
}
