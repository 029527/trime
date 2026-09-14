/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.keyboard.gesture

/**
 * A key's touch area in keyboard px: its cell plus extra touch width, right and bottom exclusive.
 * [edges] uses the `Keyboard.EDGE_*` bits.
 */
data class KeyTouchBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val edges: Int = 0,
) {
    fun contains(
        x: Int,
        y: Int,
    ) = x in left until right && y in top until bottom

    /** Like `Key.isInside`: a key on an edge of the keyboard also owns everything beyond that edge. */
    fun containsWithEdges(
        x: Int,
        y: Int,
    ) = (x >= left || edges and EDGE_LEFT != 0 && x < right) &&
        (x < right || edges and EDGE_RIGHT != 0 && x >= left) &&
        (y >= top || edges and EDGE_TOP != 0 && y < bottom) &&
        (y < bottom || edges and EDGE_BOTTOM != 0 && y >= top)

    /** Squared distance from the point to the nearest point of the box, 0 inside. */
    fun squaredDistanceTo(
        x: Int,
        y: Int,
    ): Long {
        val dx = gap(x, left, right).toLong()
        val dy = gap(y, top, bottom).toLong()
        return dx * dx + dy * dy
    }

    private fun gap(
        v: Int,
        start: Int,
        end: Int,
    ) = when {
        v < start -> start - v
        v >= end -> v - end + 1
        else -> 0
    }

    companion object {
        // Same values as Keyboard.EDGE_*, kept here so the hit test stays free of Android.
        const val EDGE_LEFT = 0x01
        const val EDGE_RIGHT = 0x02
        const val EDGE_TOP = 0x04
        const val EDGE_BOTTOM = 0x08
    }
}

/**
 * Finds the key a finger meant, in three tries:
 * 1. the touch box that contains the point; on overlap the later key wins, as with stacked views;
 * 2. an edge key whose box, extended beyond the keyboard edge, contains the point;
 * 3. among the keys [nearestKeys] reports close enough (`Keyboard.getNearestKeys`), the one
 *    whose box is closest, so a press in a gap or a spacer lands on a neighbour.
 */
object KeyHitTester {
    fun keyAt(
        boxes: List<KeyTouchBox>,
        x: Int,
        y: Int,
        nearestKeys: (x: Int, y: Int) -> IntArray,
    ): Int {
        for (i in boxes.indices.reversed()) {
            if (boxes[i].contains(x, y)) return i
        }
        for (i in boxes.indices.reversed()) {
            if (boxes[i].containsWithEdges(x, y)) return i
        }
        var best = -1
        var bestDistance = Long.MAX_VALUE
        for (i in nearestKeys(x, y)) {
            if (i !in boxes.indices) continue
            val d = boxes[i].squaredDistanceTo(x, y)
            if (d < bestDistance) {
                best = i
                bestDistance = d
            }
        }
        return best
    }
}
