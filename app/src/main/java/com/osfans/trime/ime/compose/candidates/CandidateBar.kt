/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.candidates

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.ime.compose.theme.LocalImeColors
import com.osfans.trime.ime.compose.theme.LocalImeFonts
import com.osfans.trime.ime.compose.theme.LocalImeTokens
import com.osfans.trime.ime.keyboard.T9Assist
import com.osfans.trime.ime.session.InputState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber

/**
 * State of the horizontal candidate bar, owned by its host view so the host can scroll
 * it (e.g. back to the start before the unrolled grid opens).
 *
 * Main-thread only.
 */
@Stable
class CandidateBarState(
    private val loadCandidates: suspend (start: Int, limit: Int) -> List<CandidateProto>,
) {
    val listState = LazyListState()

    var loaded by mutableStateOf(LoadedCandidates())
        private set

    /** Global index of the highlighted candidate. Read it in draw lambdas, not in composition. */
    var highlighted by mutableIntStateOf(-1)
        private set

    /** Bumped for every new candidate list, never for a highlight change or a loaded page. */
    var generation by mutableIntStateOf(0)
        private set

    private var lastPreedit: String? = null
    private var lastBulk: Array<CandidateProto>? = null
    private var lastTotal = 0

    fun submit(state: InputState) {
        val bulk = state.candidates
        val preedit = state.composition.preedit.orEmpty()
        val sameList =
            preedit == lastPreedit &&
                bulk.total == lastTotal &&
                lastBulk?.contentEquals(bulk.candidates) == true
        lastPreedit = preedit
        lastBulk = bulk.candidates
        lastTotal = bulk.total
        if (sameList) {
            highlighted = bulk.highlighted
            return
        }
        // nine-key: the pinyin is shown above the keyboard instead of beside every candidate
        val stripComment = T9Assist.composing
        loaded = LoadedCandidates.from(bulk) { if (stripComment) it.copy(comment = "") else it }
        highlighted = bulk.highlighted
        generation++
        listState.requestScrollToItem(0)
    }

    fun scrollToStart() {
        listState.requestScrollToItem(0)
    }

    /** Fetch the next page if the list may have one. Safe to call repeatedly. */
    suspend fun loadMore() {
        val current = loaded
        if (current.endReached) return
        val gen = generation
        val page =
            try {
                loadCandidates(current.nextStart, PAGE_SIZE)
            } catch (e: IllegalStateException) {
                Timber.w(e, "Failed to load candidates from ${current.nextStart}")
                return
            }
        // the list moved on while librime was busy: the page belongs to a stale composition
        if (gen != generation || loaded !== current) return
        val stripComment = T9Assist.composing
        loaded = current.append(if (stripComment) page.map { it.copy(comment = "") } else page, PAGE_SIZE)
    }

    companion object {
        const val PAGE_SIZE = 32

        /** Start loading when the last visible item is this close to the end of what is loaded. */
        const val PRELOAD_DISTANCE = 8
    }
}

/**
 * How much of the list the bar shows when scrolled to the start: [head] candidates fit
 * completely, and [showsAll] means they are the whole list.
 */
data class CandidateBarHead(
    val generation: Int,
    val head: Int,
    val isEmpty: Boolean,
    val showsAll: Boolean,
    val highlighted: Int,
)

@Composable
fun CandidateBar(
    state: CandidateBarState,
    input: StateFlow<InputState>,
    onSelect: (index: Int) -> Unit,
    onLongPress: (index: Int, text: String, x: Int) -> Unit,
    onHeadMeasured: (CandidateBarHead) -> Unit,
    onEmptyChanged: (isEmpty: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = state.listState
    val loaded = state.loaded
    val items = loaded.items

    // collected by the composition, so it stops with the keyboard view it lives in
    LaunchedEffect(state, input) {
        input.collect { state.submit(it) }
    }

    // not tied to layout: the bar is GONE while the toolbar shows, and this is what brings it back
    LaunchedEffect(state) {
        snapshotFlow { state.loaded.items.isEmpty() }
            .distinctUntilChanged()
            .collect { onEmptyChanged(it) }
    }

    LaunchedEffect(state) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= info.totalItemsCount - CandidateBarState.PRELOAD_DISTANCE
        }.distinctUntilChanged().collect { nearEnd ->
            // collect is sequential, so at most one page is in flight
            if (nearEnd) state.loadMore()
        }
    }

    // a candidate whose text is visible counts as shown even if its trailing padding is clipped,
    // otherwise the last one in the bar shows up again as the first one in the unrolled grid
    val clippablePadding = with(LocalDensity.current) { LocalImeTokens.current.candidateHorizontalPadding.roundToPx() }
    LaunchedEffect(state, clippablePadding) {
        snapshotFlow {
            val current = state.loaded
            val info = listState.layoutInfo
            when {
                current.items.isEmpty() ->
                    CandidateBarHead(state.generation, 0, isEmpty = true, showsAll = true, highlighted = -1)
                // only the layout at the start says what "the first row" is; otherwise keep the last answer
                info.totalItemsCount != current.items.size ||
                    listState.firstVisibleItemIndex != 0 ||
                    listState.firstVisibleItemScrollOffset != 0 -> null
                else -> {
                    val end = info.viewportEndOffset - info.afterContentPadding
                    val head = info.visibleItemsInfo.count { it.offset + it.size - clippablePadding <= end }
                    CandidateBarHead(
                        state.generation,
                        head,
                        isEmpty = false,
                        showsAll = current.endReached && head >= current.items.size,
                        highlighted = state.highlighted,
                    )
                }
            }
        }.distinctUntilChanged().collect { if (it != null) onHeadMeasured(it) }
    }

    // keep a highlight moved by keys (not by touch) in view
    LaunchedEffect(state) {
        snapshotFlow { state.highlighted }.collect { index ->
            if (index < 0 || index >= state.loaded.items.size) return@collect
            val info = listState.layoutInfo
            val end = info.viewportEndOffset
            val visible = info.visibleItemsInfo.any { it.index == index && it.offset >= 0 && it.offset + it.size <= end }
            if (!visible && info.totalItemsCount > 0) listState.animateScrollToItem(index)
        }
    }

    LazyRow(
        modifier = modifier.fillMaxSize(),
        state = listState,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(
            count = items.size,
            key = { it },
            contentType = { 0 },
        ) { index ->
            CandidateItem(
                candidate = items[index],
                isHighlighted = { state.highlighted == index },
                onClick = { onSelect(index) },
                onLongClick = {
                    val x = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.offset ?: 0
                    onLongPress(index, items[index].text, x)
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CandidateItem(
    candidate: CandidateProto,
    isHighlighted: () -> Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = LocalImeColors.current
    val fonts = LocalImeFonts.current
    val tokens = LocalImeTokens.current
    val highlightPaddingX = tokens.candidateHighlightPaddingHorizontal
    val highlightPaddingY = tokens.candidateHighlightPaddingVertical
    // the weight is a layout input, so it is read in composition; derived, so only the two items
    // whose highlight flips recompose when it moves
    val highlighted by remember(isHighlighted) { derivedStateOf(isHighlighted) }
    val textStyle =
        remember(fonts, tokens, highlighted) {
            TextStyle(
                fontFamily = fonts.family,
                fontWeight = if (highlighted) tokens.candidateHighlightWeight else tokens.candidateWeight,
                fontSize = tokens.candidateTextSize,
            )
        }
    Box(
        modifier =
        Modifier
            .fillMaxHeight()
            .combinedClickable(
                interactionSource = null,
                indication = null,
                hapticFeedbackEnabled = false,
                onLongClick = onLongClick,
                onClick = onClick,
            ).padding(horizontal = (tokens.candidateHorizontalPadding - highlightPaddingX).coerceAtLeast(0.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // the highlight is drawn, not composed: moving it only redraws the two items involved
        Row(
            modifier =
            Modifier
                .drawBehind {
                    if (isHighlighted()) {
                        val radius = tokens.candidateHighlightCornerRadius.toPx()
                        drawRoundRect(colors.highlightedCandidateBack, cornerRadius = CornerRadius(radius, radius))
                    }
                }.padding(horizontal = highlightPaddingX, vertical = highlightPaddingY),
        ) {
            BasicText(
                text = candidate.text,
                modifier = Modifier.alignByBaseline(),
                style = textStyle,
                color = { if (isHighlighted()) colors.highlightedCandidateText else colors.candidateText },
                maxLines = 1,
                softWrap = false,
            )
            if (candidate.comment.isNotEmpty()) {
                BasicText(
                    text = candidate.comment,
                    modifier = Modifier.alignByBaseline().padding(start = tokens.candidateCommentGap),
                    style = TextStyle(fontFamily = fonts.family, fontWeight = tokens.candidateCommentWeight, fontSize = tokens.candidateCommentTextSize),
                    color = { if (isHighlighted()) colors.highlightedCandidateComment else colors.candidateComment },
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
