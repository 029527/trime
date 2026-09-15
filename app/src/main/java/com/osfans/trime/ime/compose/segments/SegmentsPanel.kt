/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.segments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.osfans.trime.R
import com.osfans.trime.ime.compose.clipboard.PanelBarButton
import com.osfans.trime.ime.compose.clipboard.PanelListTokens
import com.osfans.trime.ime.compose.theme.LocalImeColors
import com.osfans.trime.ime.compose.theme.LocalImeFonts
import com.osfans.trime.ime.compose.theme.LocalImeTokens
import kotlin.math.abs

/**
 * The segments of one text and which of them are selected. Main-thread only.
 */
@Stable
class SegmentsPanelState(
    source: String,
    val segments: List<String>,
) {
    private val selected = mutableStateListOf<Boolean>().apply { repeat(segments.size) { add(false) } }
    private val separators = segmentSeparators(segments, source)

    fun isSelected(index: Int): Boolean = selected.getOrElse(index) { false }

    fun setSelected(
        index: Int,
        value: Boolean,
    ) {
        if (index in selected.indices && selected[index] != value) selected[index] = value
    }

    fun toggle(index: Int) = setSelected(index, !isSelected(index))

    val hasSelection: Boolean
        get() = selected.any { it }

    val isAllSelected: Boolean
        get() = selected.all { it }

    fun selectAll() {
        for (i in selected.indices) setSelected(i, true)
    }

    fun clearSelection() {
        for (i in selected.indices) setSelected(i, false)
    }

    /** What the selection would type: the selected segments with their original spacing. */
    val joined: String
        get() = joinSelectedSegments(segments, separators, ::isSelected)
}

/**
 * Segments as wrapping chips. Tap toggles one; a sideways drag selects or clears a run (see
 * [DragSelectRange]) while a vertical drag scrolls. [onSelectionChanged] fires after a tap and
 * when a drag ends.
 */
@Composable
fun SegmentsPanel(
    state: SegmentsPanelState,
    onSelectionChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fonts = LocalImeFonts.current
    val tokens = LocalImeTokens.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val currentOnSelectionChanged by rememberUpdatedState(onSelectionChanged)
    val style = TextStyle(fontFamily = fonts.key, fontSize = tokens.keyLabelTextSize)

    // the width comes from placement, not from constraints: the IME's ConstraintLayout also measures
    // with a wider AT_MOST probe, and BoxWithConstraints would recompose (and relayout) every frame
    var maxWidth by remember { mutableIntStateOf(0) }
    Box(modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp).onSizeChanged { maxWidth = it.width }) {
        val marginPx = with(density) { PanelListTokens.segmentMargin.roundToPx() }
        val paddingPx = with(density) { PanelListTokens.segmentPaddingHorizontal.roundToPx() }
        val rows =
            remember(state, maxWidth, style, density) {
                if (maxWidth <= 0) {
                    // not placed yet
                    SegmentRows(emptyList(), IntArray(0), IntArray(0))
                } else {
                    val widths =
                        IntArray(state.segments.size) { i ->
                            val text = measurer.measure(state.segments[i], style, maxLines = 1, softWrap = false)
                            // +1: keep a sub-pixel rounding difference from ellipsizing the chip
                            text.size.width + 1 + 2 * (paddingPx + marginPx)
                        }
                    SegmentRows.layout(widths, maxWidth)
                }
            }
        val rowHeight =
            remember(style, density) {
                val line = measurer.measure("中Ag", style, maxLines = 1).size.height
                line + 2 * (with(density) { PanelListTokens.segmentPaddingVertical.roundToPx() } + marginPx)
            }
        val listState = rememberLazyListState()
        val drag = remember(state) { DragSelectRange(state.segments.size, state::isSelected, state::setSelected) }
        val edgeZone = with(density) { PanelListTokens.segmentEdgeScrollZone.toPx() }
        val edgeStep = with(density) { PanelListTokens.segmentEdgeScrollStep.toPx() }
        // read through state, not as pointerInput keys: a key change restarts the handler mid-drag
        val currentRows by rememberUpdatedState(rows)
        val currentDrag by rememberUpdatedState(drag)

        LazyColumn(
            state = listState,
            modifier =
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    val rows = currentRows
                    val drag = currentDrag
                    // Initial pass: a sideways drag is claimed before the list or a chip sees it
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val start = hitTest(listState, rows, down.position)
                    if (start < 0) return@awaitEachGesture
                    var dragging = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (!dragging) {
                                val dx = abs(change.position.x - down.position.x)
                                val dy = abs(change.position.y - down.position.y)
                                val slop = viewConfiguration.touchSlop
                                if (dx <= slop && dy <= slop) continue
                                // mostly vertical: leave it to scrolling
                                if (dx <= dy) break
                                dragging = true
                                drag.begin(start)
                            }
                            change.consume()
                            val height = size.height.toFloat()
                            val y = change.position.y.coerceIn(0f, height)
                            val scroll =
                                when {
                                    y < edgeZone -> -edgeStep
                                    y > height - edgeZone -> edgeStep
                                    else -> 0f
                                }
                            if (scroll != 0f) listState.dispatchRawDelta(scroll)
                            val index = hitTest(listState, rows, Offset(change.position.x, y))
                            if (index >= 0) drag.extendTo(index)
                        }
                    } finally {
                        if (dragging) {
                            drag.end()
                            currentOnSelectionChanged()
                        }
                    }
                }
            },
        ) {
            items(count = rows.rows.size, contentType = { 0 }) { row ->
                SegmentRow(
                    state = state,
                    rows = rows,
                    range = rows.rows[row],
                    heightPx = rowHeight,
                    marginPx = marginPx,
                    style = style,
                    onToggle = { index ->
                        state.toggle(index)
                        currentOnSelectionChanged()
                    },
                )
            }
        }
    }
}

/** Actions on the selection, for the bar above the keyboard; all but select-all need a selection. */
@Composable
fun SegmentsBar(
    state: SegmentsPanelState,
    buttonSpacing: Dp,
    onShare: () -> Unit,
    onSearch: () -> Unit,
    onStar: () -> Unit,
    onCopy: () -> Unit,
    onToggleAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = state.hasSelection
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(buttonSpacing, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val squeeze = Modifier.weight(1f, fill = false)
        PanelBarButton(R.drawable.ic_baseline_share_24, onShare, squeeze, enabled)
        PanelBarButton(R.drawable.ic_baseline_search_24, onSearch, squeeze, enabled)
        PanelBarButton(R.drawable.ic_baseline_star_24, onStar, squeeze, enabled)
        PanelBarButton(R.drawable.ic_baseline_content_copy_24, onCopy, squeeze, enabled)
        PanelBarButton(
            if (state.isAllSelected) R.drawable.ic_baseline_deselect_24 else R.drawable.ic_baseline_select_all_24,
            onToggleAll,
            squeeze,
        )
    }
}

private fun hitTest(
    listState: LazyListState,
    rows: SegmentRows,
    position: Offset,
): Int {
    val item =
        listState.layoutInfo.visibleItemsInfo.firstOrNull {
            position.y >= it.offset && position.y < it.offset + it.size
        } ?: return -1
    return rows.indexAt(item.index, position.x)
}

@Composable
private fun SegmentRow(
    state: SegmentsPanelState,
    rows: SegmentRows,
    range: IntRange,
    heightPx: Int,
    marginPx: Int,
    style: TextStyle,
    onToggle: (Int) -> Unit,
) {
    Layout(
        content = {
            for (i in range) {
                SegmentChip(state.segments[i], state.isSelected(i), style, onClick = { onToggle(i) })
            }
        },
    ) { measurables, constraints ->
        val chipHeight = (heightPx - 2 * marginPx).coerceAtLeast(0)
        val placeables =
            measurables.mapIndexed { k, measurable ->
                val i = range.first + k
                measurable.measure(Constraints.fixed((rows.cellWidth[i] - 2 * marginPx).coerceAtLeast(0), chipHeight))
            }
        layout(constraints.maxWidth, heightPx) {
            placeables.forEachIndexed { k, placeable ->
                placeable.place(rows.cellX[range.first + k] + marginPx, marginPx)
            }
        }
    }
}

@Composable
private fun SegmentChip(
    text: String,
    selected: Boolean,
    style: TextStyle,
    onClick: () -> Unit,
) {
    val colors = LocalImeColors.current
    val tokens = LocalImeTokens.current
    Box(
        modifier =
        Modifier
            .clip(RoundedCornerShape(tokens.keyCornerRadius))
            // the candidate highlight: key colours alone may not tell selected from not (iOS: both white)
            .background(if (selected) colors.highlightedCandidateBack else colors.keyBack)
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = PanelListTokens.segmentPaddingHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = style,
            color = { if (selected) colors.highlightedCandidateText else colors.keyText },
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
