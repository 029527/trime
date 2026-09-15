/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.symbol

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.osfans.trime.ime.compose.theme.LocalImeColors
import com.osfans.trime.ime.compose.theme.LocalImeFonts
import com.osfans.trime.ime.compose.theme.LocalImeTokens

/**
 * Category tabs, shown in the bar above the panel. Styled like the candidate bar: the selected
 * tab gets the highlighted-candidate block hugging its text.
 */
@Composable
fun SymbolTabs(
    state: SymbolPanelState,
    modifier: Modifier = Modifier,
) {
    val categories = state.spec.categories
    val listState = state.tabsState

    // keep the selected tab in view, also when a grid cell or a key switched the category
    LaunchedEffect(state) {
        snapshotFlow { state.selected }.collect { index ->
            if (index !in categories.indices) return@collect
            val info = listState.layoutInfo
            if (info.totalItemsCount == 0) {
                // not laid out yet: the category was picked before the bar appeared
                listState.requestScrollToItem(index)
                return@collect
            }
            val fullyVisible = info.visibleItemsInfo.any {
                it.index == index && it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset
            }
            if (!fullyVisible) listState.animateScrollToItem(index)
        }
    }

    LazyRow(
        modifier = modifier.fillMaxSize(),
        state = listState,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(count = categories.size, contentType = { 0 }) { index ->
            SymbolTab(
                label = categories[index].label,
                isSelected = { state.selected == index },
                onClick = { state.select(index) },
            )
        }
    }
}

@Composable
private fun SymbolTab(
    label: String,
    isSelected: () -> Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalImeColors.current
    val fonts = LocalImeFonts.current
    val tokens = LocalImeTokens.current
    val style = remember(fonts, tokens) { TextStyle(fontFamily = fonts.candidate, fontSize = tokens.candidateTextSize) }
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = (tokens.candidateHorizontalPadding - tokens.candidateHighlightPadding).coerceAtLeast(0.dp)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            modifier = Modifier
                .drawBehind {
                    if (isSelected()) {
                        val radius = tokens.candidateHighlightCornerRadius.toPx()
                        drawRoundRect(colors.highlightedCandidateBack, cornerRadius = CornerRadius(radius, radius))
                    }
                }.padding(tokens.candidateHighlightPadding),
            style = style,
            color = { if (isSelected()) colors.highlightedCandidateText else colors.candidateText },
            maxLines = 1,
            softWrap = false,
        )
    }
}
