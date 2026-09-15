/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.candidates.unrolled

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.core.Candidates
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.ime.compose.theme.LocalImeColors
import com.osfans.trime.ime.compose.theme.LocalImeFonts
import com.osfans.trime.ime.compose.theme.LocalImeTokens
import com.osfans.trime.ime.keyboard.T9Assist
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The candidates after what the bar shows, loaded page by page as the grid scrolls.
 *
 * Starts at a global [offset] (the size of the bar's head), so index `i` of [items] is the
 * candidate `offset + i` of the whole list. Main-thread only.
 */
@Stable
class UnrolledCandidatesState(
    private val loadCandidates: suspend (start: Int, limit: Int) -> List<CandidateProto>,
) {
    val listState = LazyListState()

    var offset by mutableIntStateOf(0)
        private set

    var items by mutableStateOf<List<CandidateProto>>(emptyList())
        private set

    /** Global index of the highlighted candidate when the grid was filled. */
    var highlighted by mutableIntStateOf(-1)
        private set

    /** Bumped for every refill, so a page fetched for an older list is dropped. */
    var generation by mutableIntStateOf(0)
        private set

    private var total = 0
    private var endReached = true

    /**
     * Refill from global index [offset]. What the engine already pushed with the composition
     * ([head]) is used right away, so the grid is not empty for a frame while librime answers.
     */
    fun reset(
        offset: Int,
        head: Candidates.Bulk,
    ) {
        val bulk = head.candidates
        this.offset = offset
        total = head.total
        highlighted = head.highlighted
        items = if (offset < bulk.size) bulk.asList().subList(offset, bulk.size).map(::strip) else emptyList()
        val next = maxOf(offset, bulk.size)
        endReached = total >= 0 && next >= total
        generation++
        listState.requestScrollToItem(0)
    }

    /** Fetch the next page if the list may have one. Safe to call repeatedly. */
    suspend fun loadMore() {
        if (endReached) return
        val gen = generation
        val start = offset + items.size
        val page =
            try {
                loadCandidates(start, PAGE_SIZE)
            } catch (e: IllegalStateException) {
                Timber.w(e, "Failed to load candidates from $start")
                return
            }
        if (gen != generation) return
        items = items + page.map(::strip)
        endReached = page.size < PAGE_SIZE || (total >= 0 && offset + items.size >= total)
    }

    // nine-key: the pinyin is shown above the keyboard instead of beside every candidate, as in the bar
    private fun strip(candidate: CandidateProto) = if (T9Assist.composing) candidate.copy(comment = "") else candidate

    companion object {
        const val PAGE_SIZE = 48

        /** Start loading when the last visible row is this close to the last loaded one. */
        const val PRELOAD_ROWS = 3
    }
}

/** Sizes the grid takes from the theme, where [com.osfans.trime.ime.compose.theme.ImeTokens] has none. */
@Immutable
data class UnrolledGridConfig(
    val itemHeightDp: Int,
    val separatorDp: Float,
    val borderPx: Int,
    val borderRadiusPx: Float,
)

/** Where a long-pressed item sits in the grid's root, in px. */
@Immutable
data class ItemBounds(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/**
 * The unrolled candidates as wrapped rows: each row takes as many candidates as fit at their
 * natural width, and shares what is left of the row equally among them, like the flexbox
 * grid it replaces. Rows are laid out lazily, so a long list costs only what is on screen.
 */
@Composable
fun UnrolledCandidates(
    state: UnrolledCandidatesState,
    config: UnrolledGridConfig,
    onSelect: (index: Int) -> Unit,
    onLongPress: (index: Int, text: String, bounds: ItemBounds) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalImeColors.current
    val fonts = LocalImeFonts.current
    val tokens = LocalImeTokens.current
    val density = LocalDensity.current
    val background =
        remember(colors) {
            ColorManager.getDecorDrawable("candidate_background", "candidate_border_color", config.borderPx, config.borderRadiusPx)
        }
    // the width comes from placement, not from constraints: the IME's ConstraintLayout also measures
    // with a wider AT_MOST probe, and BoxWithConstraints would recompose (and relayout) every frame
    var widthPx by remember { mutableIntStateOf(0) }
    Box(
        modifier =
        modifier
            .fillMaxSize()
            .onSizeChanged { widthPx = it.width }
            .drawBehind {
                val d = background ?: return@drawBehind
                drawIntoCanvas {
                    d.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
                    d.draw(it.nativeCanvas)
                }
            },
    ) {
        val measurer = rememberTextMeasurer(cacheSize = 0)
        val textStyle = remember(fonts, tokens) { TextStyle(fontFamily = fonts.family, fontWeight = tokens.candidateWeight, fontSize = tokens.candidateTextSize) }
        val commentStyle = remember(fonts, tokens) { TextStyle(fontFamily = fonts.family, fontWeight = tokens.candidateCommentWeight, fontSize = tokens.candidateCommentTextSize) }
        val items = state.items
        // natural widths only grow with the list, so a loaded page measures just its own items
        val widths = remember(state.generation, widthPx, textStyle) { ArrayList<Int>() }
        val rows =
            remember(items, widths) {
                with(density) {
                    val padding = tokens.candidateHorizontalPadding.roundToPx() * 2
                    val gap = tokens.candidateCommentGap.roundToPx()
                    val minWidth = tokens.barUnrolledItemMinWidth.roundToPx()
                    for (i in widths.size until items.size) {
                        val c = items[i]
                        var w = measurer.measure(c.text, textStyle, maxLines = 1, softWrap = false).size.width
                        if (c.comment.isNotEmpty()) {
                            w += gap + measurer.measure(c.comment, commentStyle, maxLines = 1, softWrap = false).size.width
                        }
                        widths.add(maxOf(minWidth, w + padding))
                    }
                }
                // not placed yet: an empty first frame beats one row per candidate
                if (widthPx <= 0) emptyList() else chunkRows(widths, items.size, widthPx)
            }

        LaunchedEffect(state) {
            snapshotFlow {
                val info = state.listState.layoutInfo
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                // counts the list rows, not candidates; the loaded rows are all there is
                state.items.isEmpty() || lastVisible >= info.totalItemsCount - UnrolledCandidatesState.PRELOAD_ROWS
            }.distinctUntilChanged().collect { nearEnd ->
                if (nearEnd) state.loadMore()
            }
        }
        // a page can also be too short to fill the screen; keep going until it scrolls or ends
        LaunchedEffect(state, items.size) {
            val info = state.listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            if (lastVisible >= info.totalItemsCount - UnrolledCandidatesState.PRELOAD_ROWS) state.loadMore()
        }

        val itemHeight = config.itemHeightDp.dp
        val separator = config.separatorDp.dp
        LazyColumn(state = state.listState, modifier = Modifier.fillMaxSize()) {
            items(rows, key = { it.first }, contentType = { 0 }) { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    GrowRow(modifier = Modifier.fillMaxWidth().height(itemHeight)) {
                        for (i in row.first until row.last + 1) {
                            val index = state.offset + i
                            val candidate = items[i]
                            GridItem(
                                candidate = candidate,
                                isHighlighted = index == state.highlighted,
                                textStyle = textStyle,
                                commentStyle = commentStyle,
                                onClick = { onSelect(index) },
                                onLongPress = { bounds -> onLongPress(index, candidate.text, bounds) },
                            )
                        }
                    }
                }
                if (separator > 0.dp) {
                    Spacer(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(separator)
                            .drawBehind { drawRect(colors.candidateSeparator) },
                    )
                }
            }
        }
    }
}

/** Index ranges of [count] items of the given widths, wrapped at [maxWidth]; every row holds at least one. */
internal fun chunkRows(
    widths: List<Int>,
    count: Int,
    maxWidth: Int,
): List<IntRange> {
    val rows = ArrayList<IntRange>()
    var start = 0
    var used = 0
    for (i in 0 until count) {
        val w = widths[i]
        if (i > start && used + w > maxWidth) {
            rows.add(start until i)
            start = i
            used = 0
        }
        used += w
    }
    if (start < count) rows.add(start until count)
    return rows
}

/** Children at their natural width, the rest of the row shared equally between them. */
@Composable
private fun GrowRow(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val height = constraints.maxHeight
        val natural = measurables.map { it.maxIntrinsicWidth(height) }
        val extra = (constraints.maxWidth - natural.sum()).coerceAtLeast(0)
        val share = if (measurables.isEmpty()) 0 else extra / measurables.size
        var remainder = if (measurables.isEmpty()) 0 else extra - share * measurables.size
        val placeables =
            measurables.mapIndexed { i, m ->
                val w = natural[i] + share + if (remainder-- > 0) 1 else 0
                m.measure(Constraints.fixed(w.coerceAtMost(constraints.maxWidth), height))
            }
        layout(constraints.maxWidth, height) {
            var x = 0
            placeables.forEach {
                it.place(IntOffset(x, 0))
                x += it.width
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridItem(
    candidate: CandidateProto,
    isHighlighted: Boolean,
    textStyle: TextStyle,
    commentStyle: TextStyle,
    onClick: () -> Unit,
    onLongPress: (ItemBounds) -> Unit,
) {
    val colors = LocalImeColors.current
    val tokens = LocalImeTokens.current
    val highlightPaddingX = tokens.candidateHighlightPaddingHorizontal
    val highlightPaddingY = tokens.candidateHighlightPaddingVertical
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(
        modifier =
        Modifier
            .onPlaced { coordinates[0] = it }
            .combinedClickable(
                interactionSource = null,
                indication = null,
                hapticFeedbackEnabled = false,
                onLongClick = {
                    val c = coordinates[0]
                    val bounds =
                        if (c != null && c.isAttached) {
                            val p = c.positionInRoot()
                            ItemBounds(p.x.roundToInt(), p.y.roundToInt(), c.size.width, c.size.height)
                        } else {
                            ItemBounds(0, 0, 0, 0)
                        }
                    onLongPress(bounds)
                },
                onClick = onClick,
            ).padding(horizontal = (tokens.candidateHorizontalPadding - highlightPaddingX).coerceAtLeast(0.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
            Modifier
                .drawBehind {
                    if (isHighlighted) {
                        val r = tokens.candidateHighlightCornerRadius.toPx()
                        drawRoundRect(colors.highlightedCandidateBack, cornerRadius = CornerRadius(r, r))
                    }
                }.padding(horizontal = highlightPaddingX, vertical = highlightPaddingY),
        ) {
            BasicText(
                text = candidate.text,
                modifier = Modifier.alignByBaseline(),
                style = textStyle,
                color = { if (isHighlighted) colors.highlightedCandidateText else colors.candidateText },
                maxLines = 1,
                softWrap = false,
            )
            if (candidate.comment.isNotEmpty()) {
                BasicText(
                    text = candidate.comment,
                    modifier = Modifier.alignByBaseline().padding(start = tokens.candidateCommentGap),
                    style = commentStyle,
                    color = { if (isHighlighted) colors.highlightedCandidateComment else colors.candidateComment },
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
