/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.switches

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osfans.trime.ime.compose.clipboard.PanelCell
import com.osfans.trime.ime.compose.clipboard.PanelMenuAction
import com.osfans.trime.ime.compose.theme.LocalImeColors
import com.osfans.trime.ime.compose.theme.LocalImeFonts
import com.osfans.trime.ime.compose.theme.LocalImeTokens
import com.osfans.trime.ime.switches.SwitchOptionEntry
import java.text.BreakIterator

/**
 * Grid of quick settings and the current schema's switches.
 *
 * A switch with more than two states opens a menu of them on tap, built by [menuFor];
 * every other entry calls [onClick].
 */
@Composable
fun SwitchOptionGrid(
    entries: List<SwitchOptionEntry>,
    onClick: (SwitchOptionEntry) -> Unit,
    menuFor: (SwitchOptionEntry.Custom) -> List<PanelMenuAction>,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalImeTokens.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(tokens.panelSwitchCellMinWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(tokens.panelListPadding),
    ) {
        items(entries, contentType = { 0 }) { entry ->
            val tapMenu =
                if (entry is SwitchOptionEntry.Custom && entry.switch.options.isNotEmpty()) {
                    { menuFor(entry) }
                } else {
                    null
                }
            SwitchOptionCell(entry, onClick = { onClick(entry) }, tapMenu = tapMenu)
        }
    }
}

@Composable
private fun SwitchOptionCell(
    entry: SwitchOptionEntry,
    onClick: () -> Unit,
    tapMenu: (() -> List<PanelMenuAction>)?,
) {
    val colors = LocalImeColors.current
    val fonts = LocalImeFonts.current
    val tokens = LocalImeTokens.current
    PanelCell(
        onClick = onClick,
        tapMenu = tapMenu,
        modifier = Modifier.fillMaxWidth().height(tokens.panelSwitchCellHeight),
    ) { pressed ->
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                Modifier
                    .size(tokens.panelSwitchTileSize)
                    .clip(RoundedCornerShape(tokens.keyCornerRadius))
                    .background(if (pressed) colors.highlightedKeyBack else colors.keyBack),
                contentAlignment = Alignment.Center,
            ) {
                if (entry.icon != 0) {
                    Icon(
                        painterResource(entry.icon),
                        contentDescription = null,
                        modifier = Modifier.size(tokens.panelSwitchIconSize),
                        tint = colors.keyText,
                    )
                } else {
                    val glyph = remember(entry.label) { firstCharacter(entry.label) }
                    BasicText(
                        text = glyph,
                        modifier = Modifier.padding(4.dp),
                        style = TextStyle(fontFamily = fonts.key, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                        color = { colors.keyText },
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = tokens.panelSwitchGlyphTextSize),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = entry.label,
                modifier = Modifier.padding(horizontal = 2.dp),
                style = TextStyle(fontFamily = fonts.key, fontSize = tokens.panelSwitchLabelTextSize, textAlign = TextAlign.Center),
                color = { colors.keyText },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** First user-perceived character, so an emoji or a combined glyph is not cut in half. */
private fun firstCharacter(s: String): String {
    if (s.isEmpty()) return ""
    val iterator = BreakIterator.getCharacterInstance().apply { setText(s) }
    return s.substring(iterator.first(), iterator.next())
}
