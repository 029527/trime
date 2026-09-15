/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.clipboard

import android.graphics.Typeface
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.osfans.trime.R
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.ime.compose.theme.LocalImeColors
import com.osfans.trime.ime.compose.theme.LocalImeFonts
import com.osfans.trime.ime.compose.theme.LocalImeTokens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.min

enum class ClipboardPage(
    @param:StringRes val label: Int,
    @param:StringRes val emptyHint: Int,
) {
    Clipboard(R.string.clipboard, R.string.clipboard_empty),
    Collection(R.string.collection, R.string.collection_empty),
}

/**
 * Shared by the tabs in the bar and the pages below it, which sit in two separate compose views.
 * Main-thread only.
 */
@Stable
class ClipboardPanelState(
    initialTab: Int,
) {
    val pagerState =
        PagerState(currentPage = initialTab.coerceIn(0, ClipboardPage.entries.lastIndex)) {
            ClipboardPage.entries.size
        }

    val currentPage: ClipboardPage
        get() = ClipboardPage.entries[pagerState.currentPage]
}

/** Tab titles and the delete-all button, for the bar above the keyboard. */
@Composable
fun ClipboardBar(
    state: ClipboardPanelState,
    onDeleteAll: (ClipboardPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalImeColors.current
    val tokens = LocalImeTokens.current
    val scope = rememberCoroutineScope()
    // a theme font loaded from a file has no bold face to pick, so embolden the typeface itself
    val titleFont = remember { FontFamily(Typeface.create(FontManager.getTypeface("candidate_font"), Typeface.BOLD)) }
    Row(modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        ClipboardPage.entries.forEach { page ->
            val selected = state.pagerState.targetPage == page.ordinal
            BasicText(
                text = stringResource(page.label),
                modifier =
                Modifier
                    .fillMaxHeight()
                    .clickable(interactionSource = null, indication = null) {
                        scope.launch { state.pagerState.animateScrollToPage(page.ordinal) }
                    }.wrapContentHeight()
                    .padding(horizontal = 8.dp),
                style = TextStyle(fontFamily = titleFont, fontSize = tokens.candidateTextSize),
                color = { if (selected) colors.keyText else colors.keyText.copy(alpha = colors.keyText.alpha * 0.5f) },
                maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f))
        PanelBarButton(R.drawable.ic_baseline_delete_sweep_24, onClick = { onDeleteAll(state.currentPage) })
    }
}

/**
 * The clipboard history and the collection side by side in a pager.
 *
 * [menuFor] builds an entry's long-press menu when it opens.
 */
@Composable
fun ClipboardPages(
    state: ClipboardPanelState,
    clipboardBeans: Flow<PagingData<DatabaseBean>>,
    collectionBeans: Flow<PagingData<DatabaseBean>>,
    onPaste: (DatabaseBean) -> Unit,
    menuFor: (ClipboardPage, DatabaseBean) -> List<PanelMenuAction>,
    modifier: Modifier = Modifier,
) {
    // collected here rather than inside a page, so a page swiped away keeps its loaded data
    val clipboard = clipboardBeans.collectAsLazyPagingItems()
    val collection = collectionBeans.collectAsLazyPagingItems()
    HorizontalPager(
        state = state.pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        key = { it },
    ) { index ->
        val page = ClipboardPage.entries[index]
        BeanList(
            page = page,
            items = if (page == ClipboardPage.Clipboard) clipboard else collection,
            onPaste = onPaste,
            menuFor = menuFor,
        )
    }
}

@Composable
private fun BeanList(
    page: ClipboardPage,
    items: LazyPagingItems<DatabaseBean>,
    onPaste: (DatabaseBean) -> Unit,
    menuFor: (ClipboardPage, DatabaseBean) -> List<PanelMenuAction>,
) {
    val tokens = LocalImeTokens.current
    if (items.itemCount == 0) {
        // no hint while the first page is still loading, or it flashes on every open
        if (items.loadState.refresh is LoadState.NotLoading) {
            PanelEmptyHint(stringResource(page.emptyHint))
        }
    } else {
        val listState = rememberLazyListState()
        // the list keeps its first visible item in place, so a new head (just copied or pinned)
        // would open above the viewport; follow it when the list was showing the top
        LaunchedEffect(items, listState) {
            snapshotFlow { if (items.itemCount > 0) items.peek(0)?.id else null }
                .distinctUntilChanged()
                .collect {
                    if (listState.firstVisibleItemIndex <= 1 && listState.firstVisibleItemScrollOffset == 0) {
                        listState.requestScrollToItem(0)
                    }
                }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(tokens.panelListPadding),
            verticalArrangement = Arrangement.spacedBy(tokens.panelCellSpacing),
        ) {
            items(
                count = items.itemCount,
                key = items.itemKey { it.id },
                contentType = items.itemContentType { 0 },
            ) { index ->
                val bean = items[index]
                if (bean != null) {
                    BeanEntry(bean, onPaste = { onPaste(bean) }, menu = { menuFor(page, bean) })
                }
            }
        }
    }
}

@Composable
private fun BeanEntry(
    bean: DatabaseBean,
    onPaste: () -> Unit,
    menu: () -> List<PanelMenuAction>,
) {
    val colors = LocalImeColors.current
    val fonts = LocalImeFonts.current
    val tokens = LocalImeTokens.current
    val text = remember(bean.text) { excerptText(bean.text.orEmpty(), tokens.panelEntryMaxLines) }
    val shape = RoundedCornerShape(tokens.keyCornerRadius)
    PanelCell(onClick = onPaste, longPressMenu = menu, modifier = Modifier.fillMaxWidth()) { pressed ->
        Box(Modifier.matchParentSize().clip(shape).background(if (pressed) colors.highlightedKeyBack else colors.keyBack))
        BasicText(
            text = text,
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = tokens.panelEntryPaddingHorizontal,
                    vertical = tokens.panelEntryPaddingVertical,
                ),
            style = TextStyle(fontFamily = fonts.key, fontSize = tokens.panelEntryTextSize),
            color = { colors.keyText },
            maxLines = tokens.panelEntryMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
        if (bean.pinned) {
            Icon(
                painterResource(R.drawable.ic_baseline_push_pin_24),
                contentDescription = null,
                modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(tokens.panelEntryPinSize),
                tint = colors.keyText.copy(alpha = colors.keyText.alpha * tokens.panelEntryPinAlpha),
            )
        }
    }
}

/** The first [lines] lines of [str], each cut at [chars]: a huge clip would otherwise be laid out in full. */
internal fun excerptText(
    str: String,
    lines: Int,
    chars: Int = 128,
): String = buildString {
    val length = str.length
    var lineBreak = -1
    for (i in 1..lines) {
        val start = lineBreak + 1 // skip previous '\n'
        if (start > length) break
        val excerptEnd = min(start + chars, length)
        lineBreak = str.indexOf('\n', start)
        if (lineBreak < 0) {
            // no line breaks remaining, substring to end of text
            append(str.substring(start, excerptEnd))
            break
        } else {
            val end = min(excerptEnd, lineBreak)
            // append one line exactly
            appendLine(str.substring(start, end))
        }
    }
}
