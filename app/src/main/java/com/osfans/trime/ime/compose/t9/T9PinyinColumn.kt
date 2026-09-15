/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.t9

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.osfans.trime.ime.compose.clipboard.PanelCell
import com.osfans.trime.ime.compose.theme.LocalImeColors
import com.osfans.trime.ime.compose.theme.LocalImeFonts
import com.osfans.trime.ime.compose.theme.LocalImeTokens

/**
 * What the nine-key pinyin column shows: the syllables the next digits could spell, and the raw
 * [input] they were worked out from. A new input scrolls the list back to the top.
 */
@Immutable
data class T9PinyinChoices(
    val input: String,
    val syllables: List<String>,
)

/**
 * The "choose pinyin" column of a nine-key keyboard (选拼音 on iOS), drawn over its left key column
 * while a nine-key input is being composed. Scrolls without a scrollbar or an overscroll effect.
 *
 * It fills the key cells it covers; the list is inset by half a key gap on every side, so its edges
 * line up with the key bodies underneath and the covered keys are hidden.
 */
@Composable
fun T9PinyinColumn(
    choices: T9PinyinChoices,
    onSyllable: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalImeTokens.current
    val listState = rememberLazyListState()
    LaunchedEffect(choices.input) { listState.requestScrollToItem(0) }
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        LazyColumn(
            state = listState,
            // no fill: the keyboard canvas leaves the covered keys out, so the keyboard surface itself shows here
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = tokens.keyHorizontalGap / 2, vertical = tokens.keyVerticalGap / 2),
            verticalArrangement = Arrangement.spacedBy(tokens.t9ColumnItemSpacing),
        ) {
            items(choices.syllables, key = { it }, contentType = { 0 }) { syllable ->
                SyllableCell(syllable, onClick = { onSyllable(syllable) })
            }
        }
    }
}

@Composable
private fun SyllableCell(
    syllable: String,
    onClick: () -> Unit,
) {
    val colors = LocalImeColors.current
    val fonts = LocalImeFonts.current
    val tokens = LocalImeTokens.current
    val shape = RoundedCornerShape(tokens.keyCornerRadius)
    // a syllable types letters, so it weighs like the nine-key letter groups, not like a function key
    val style = remember(fonts, tokens) { TextStyle(fontFamily = fonts.family, fontWeight = tokens.keyTextWeight, textAlign = TextAlign.Center) }
    PanelCell(onClick = onClick, modifier = Modifier.fillMaxWidth().height(tokens.t9ColumnItemHeight)) { pressed ->
        Box(Modifier.matchParentSize().clip(shape).background(if (pressed) colors.highlightedKeyBack else colors.keyBack))
        BasicText(
            text = syllable,
            modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.symbolLongTextHorizontalPadding).align(Alignment.Center),
            style = style,
            color = { if (pressed) colors.highlightedKeyText else colors.keyText },
            maxLines = 1,
            // `zhuang` shrinks to fit a narrow key instead of being cut
            autoSize = TextAutoSize.StepBased(minFontSize = tokens.keySymbolTextSize, maxFontSize = tokens.keyLabelTextSize),
        )
    }
}
