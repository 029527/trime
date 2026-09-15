/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.symbol

import androidx.compose.ui.unit.dp
import com.osfans.trime.data.SymbolHistory
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.ime.compose.symbol.SymbolAction
import com.osfans.trime.ime.compose.symbol.SymbolBarKey
import com.osfans.trime.ime.compose.symbol.SymbolBarPosition
import com.osfans.trime.ime.compose.symbol.SymbolCategory
import com.osfans.trime.ime.compose.symbol.SymbolItem
import com.osfans.trime.ime.compose.symbol.SymbolPanelSource
import com.osfans.trime.ime.compose.symbol.SymbolPanelSpec

/**
 * The symbol panel's content as the theme's `liquid_keyboard` section defines it, read through
 * [LiquidData]. The only place that knows about the yaml; the panel itself sees [SymbolPanelSpec].
 *
 * Page types map to actions: `SINGLE` commits and remembers, `HISTORY` commits the remembered
 * symbols, `SYMBOL` types its `/xx` code through Rime, `TABS` lists the other categories.
 */
class LiquidSymbolSource(
    private val theme: Theme,
) : SymbolPanelSource {
    private val history = SymbolHistory(HISTORY_CAPACITY)
    private var historyLoaded = false

    /** Static pages converted once; emoji pages run to a thousand items. */
    private val pages = HashMap<Int, List<SymbolItem>>()

    override fun spec(): SymbolPanelSpec {
        val liquid = theme.liquidKeyboard
        return SymbolPanelSpec(
            categories = LiquidData.getTagList().map { SymbolCategory(it.label) },
            barKeys = liquid.fixedKeyBar.keys.map { name ->
                val preset = theme.presetKeys[name]
                SymbolBarKey(
                    label = preset?.label.orEmpty(),
                    action = name,
                    repeatable = preset?.repeatable ?: false,
                    // `functional` defaults to false in preset_keys, so it cannot tell space from backspace
                    functional = preset?.send != "space",
                )
            },
            barPosition = when (liquid.fixedKeyBar.position) {
                LiquidKeyboard.KeyBar.Position.TOP -> SymbolBarPosition.Top
                LiquidKeyboard.KeyBar.Position.BOTTOM -> SymbolBarPosition.Bottom
                LiquidKeyboard.KeyBar.Position.LEFT -> SymbolBarPosition.Left
                LiquidKeyboard.KeyBar.Position.RIGHT -> SymbolBarPosition.Right
            },
            cellWidth = liquid.singleWidth.takeIf { it > 0 }?.dp,
            cellHeight = liquid.keyHeight.takeIf { it > 0 }?.dp,
        )
    }

    override fun items(index: Int): List<SymbolItem> {
        val tags = LiquidData.getTagList()
        val tag = tags.getOrNull(index) ?: return emptyList()
        return when (tag.type) {
            LiquidData.Type.HISTORY -> {
                ensureHistory()
                history.toOrderedList().map { SymbolItem(it, SymbolAction.Commit(it, remember = false)) }
            }
            LiquidData.Type.TABS -> pages.getOrPut(index) {
                tags.mapIndexedNotNull { i, other ->
                    if (other.type == LiquidData.Type.TABS) null else SymbolItem(other.label, SymbolAction.OpenCategory(i))
                }
            }
            LiquidData.Type.SYMBOL -> pages.getOrPut(index) {
                LiquidData.getDataByIndex(index).map { SymbolItem(it.text, SymbolAction.TypeKeys(it.altText)) }
            }
            LiquidData.Type.SINGLE -> pages.getOrPut(index) {
                LiquidData.getDataByIndex(index).map { SymbolItem(it.text, SymbolAction.Commit(it.text, remember = true)) }
            }
        }
    }

    override fun remember(text: String) {
        ensureHistory()
        history.insert(text)
        history.save()
    }

    /** Saving before loading would overwrite the file with this session's symbols only. */
    private fun ensureHistory() {
        if (historyLoaded) return
        history.load()
        historyLoaded = true
    }

    companion object {
        private const val HISTORY_CAPACITY = 180
    }
}
