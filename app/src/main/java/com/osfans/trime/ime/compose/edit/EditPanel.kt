/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.edit

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizeDp
import com.osfans.trime.R
import com.osfans.trime.ime.compose.bar.DrawablePainter
import com.osfans.trime.ime.compose.symbol.barKeyGestures
import com.osfans.trime.ime.compose.symbol.drawKeyBody
import com.osfans.trime.ime.compose.theme.ImeIcons
import com.osfans.trime.ime.compose.theme.LocalImeColors
import com.osfans.trime.ime.compose.theme.LocalImeFonts
import com.osfans.trime.ime.compose.theme.LocalImeTokens
import com.osfans.trime.ime.edit.EditCommand
import com.osfans.trime.ime.edit.EditKey

/**
 * The edit panel: cursor keys around the select toggle, line start / end under them, and the
 * clipboard commands in a column at the end.
 *
 * ```
 * ┌────┬──────┬────┬──────┐
 * │    │  ↑   │    │ 全选 │
 * │    ├──────┤    ├──────┤
 * │ ←  │ 选择 │ →  │ 剪切 │
 * │    ├──────┤    ├──────┤
 * │    │  ↓   │    │ 复制 │
 * ├────┴──┬───┴────┼──────┤
 * │ 行首  │  行尾  │ 粘贴 │
 * └───────┴────────┴──────┘
 * ```
 *
 * Cells share the window's height through weights; nothing here measures the window, so the
 * panel does not recompose when the input view measures it twice.
 */
@Composable
fun EditPanel(
    selecting: Boolean,
    onKey: (EditKey) -> Unit,
    onCommand: (EditCommand) -> Unit,
    onToggleSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalImeTokens.current
    Row(modifier.fillMaxSize().padding(horizontal = tokens.keyboardHorizontalPadding)) {
        Column(Modifier.weight(1f - tokens.editPanelCommandColumnFraction).fillMaxHeight()) {
            Row(Modifier.weight(3f).fillMaxWidth()) {
                KeyCell(EditKey.Left, onKey, Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(tokens.editPanelCenterColumnWeight).fillMaxHeight()) {
                    KeyCell(EditKey.Up, onKey, Modifier.weight(1f).fillMaxWidth())
                    EditCell(
                        icon = ImeIcons.EditSelect,
                        label = stringResource(R.string.edit_select),
                        level = if (selecting) CellLevel.On else CellLevel.Function,
                        repeatable = false,
                        onTrigger = onToggleSelect,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    KeyCell(EditKey.Down, onKey, Modifier.weight(1f).fillMaxWidth())
                }
                KeyCell(EditKey.Right, onKey, Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                KeyCell(EditKey.LineStart, onKey, Modifier.weight(1f).fillMaxHeight())
                KeyCell(EditKey.LineEnd, onKey, Modifier.weight(1f).fillMaxHeight())
            }
        }
        Column(Modifier.weight(tokens.editPanelCommandColumnFraction).fillMaxHeight()) {
            EditCommand.entries.forEach { command ->
                CommandCell(command, onCommand, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

/** How a cell is shaded, following the keyboard's two levels plus the accent for a toggle that is on. */
private enum class CellLevel {
    /** The arrows: pressed over and over, so they get the letter-key shade. */
    Letter,
    Function,
    On,
}

@Composable
private fun KeyCell(
    key: EditKey,
    onKey: (EditKey) -> Unit,
    modifier: Modifier,
) {
    val (icon, label) =
        when (key) {
            EditKey.Up -> ImeIcons.EditUp to null
            EditKey.Down -> ImeIcons.EditDown to null
            EditKey.Left -> ImeIcons.EditLeft to null
            EditKey.Right -> ImeIcons.EditRight to null
            EditKey.LineStart -> ImeIcons.EditLineStart to stringResource(R.string.edit_line_start)
            EditKey.LineEnd -> ImeIcons.EditLineEnd to stringResource(R.string.edit_line_end)
        }
    EditCell(
        icon = icon,
        label = label,
        level = if (label == null) CellLevel.Letter else CellLevel.Function,
        repeatable = key.repeatable,
        onTrigger = { onKey(key) },
        modifier = modifier,
    )
}

@Composable
private fun CommandCell(
    command: EditCommand,
    onCommand: (EditCommand) -> Unit,
    modifier: Modifier,
) {
    val (icon, label) =
        when (command) {
            EditCommand.SelectAll -> ImeIcons.SelectAll to R.string.edit_select_all
            EditCommand.Cut -> ImeIcons.Cut to R.string.edit_cut
            EditCommand.Copy -> ImeIcons.Copy to R.string.edit_copy
            EditCommand.Paste -> ImeIcons.Paste to R.string.edit_paste
        }
    EditCell(
        icon = icon,
        label = stringResource(label),
        level = CellLevel.Function,
        repeatable = false,
        onTrigger = { onCommand(command) },
        modifier = modifier,
    )
}

/**
 * One key of the panel, drawn like a keyboard key: a rounded body inset by half the key gaps, the
 * pressed shade painted in draw only, so holding a key repaints it without recomposing.
 */
@Composable
private fun EditCell(
    icon: ImageVector,
    label: String?,
    level: CellLevel,
    repeatable: Boolean,
    onTrigger: () -> Unit,
    modifier: Modifier,
) {
    val colors = LocalImeColors.current
    val tokens = LocalImeTokens.current
    val fonts = LocalImeFonts.current
    val context = LocalContext.current
    val view = LocalView.current
    var pressed by remember { mutableStateOf(false) }
    val insetX = tokens.keyHorizontalGap / 2
    val insetY = tokens.keyVerticalGap / 2
    val onPressedChange = remember { { value: Boolean -> pressed = value } }
    // the gesture detector is keyed on this lambda: keep it stable across recompositions
    val currentTrigger by rememberUpdatedState(onTrigger)
    val trigger = remember { { currentTrigger() } }
    val iconSize: Dp = if (label == null) tokens.editPanelArrowIconSize else tokens.editPanelIconSize
    val painter = rememberVectorPainter(icon)
    val foreground: Color =
        when (level) {
            CellLevel.Letter -> colors.keyText
            CellLevel.Function -> colors.functionKeyText
            CellLevel.On -> colors.accentText
        }
    Box(
        modifier =
        modifier
            .drawBehind {
                val back =
                    when (level) {
                        CellLevel.On -> colors.accentBack
                        CellLevel.Letter -> if (pressed) colors.highlightedKeyBack else colors.keyBack
                        CellLevel.Function ->
                            when {
                                !pressed -> colors.functionKeyBack
                                tokens.keyPressedFunctionSwap -> colors.keyBack
                                else -> colors.highlightedFunctionKeyBack
                            }
                    }
                drawKeyBody(back, tokens.keyCornerRadius, insetX, insetY)
            }.barKeyGestures(repeatable, view, onPressedChange, trigger)
            .padding(horizontal = insetX, vertical = insetY),
        contentAlignment = Alignment.Center,
    ) {
        val glyph =
            @Composable {
                Image(
                    painter = painter,
                    contentDescription = label,
                    modifier = Modifier.size(iconSize),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(foreground),
                )
            }
        if (label == null) {
            glyph()
            return@Box
        }
        val text =
            @Composable {
                BasicText(
                    text = label,
                    style = TextStyle(fontFamily = fonts.family, fontWeight = tokens.panelLabelWeight, fontSize = tokens.editPanelLabelTextSize, textAlign = TextAlign.Center),
                    color = { foreground },
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        val gap = Arrangement.spacedBy(tokens.editPanelIconLabelGap, Alignment.CenterHorizontally)
        if (tokens.editPanelLabelBesideIcon) {
            Row(horizontalArrangement = gap, verticalAlignment = Alignment.CenterVertically) {
                glyph()
                text()
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(tokens.editPanelIconLabelGap, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                glyph()
                text()
            }
        }
    }
}
