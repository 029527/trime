/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.symbol

import android.view.View
import androidx.core.content.ContextCompat
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.compose.imeComposeView
import com.osfans.trime.ime.compose.symbol.SymbolAction
import com.osfans.trime.ime.compose.symbol.SymbolBarKey
import com.osfans.trime.ime.compose.symbol.SymbolItem
import com.osfans.trime.ime.compose.symbol.SymbolPanel
import com.osfans.trime.ime.compose.symbol.SymbolPanelState
import com.osfans.trime.ime.compose.symbol.SymbolTabs
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ime.window.ResidentWindow
import org.kodein.di.instance

/**
 * The symbol panel ("liquid keyboard"). Content comes from [LiquidSymbolSource]; the grid and the
 * category tabs in the bar are Compose and follow the colour scheme and tint on their own.
 */
class LiquidWindow :
    BoardWindow.BarBoardWindow(),
    ResidentWindow,
    InputBroadcastReceiver {
    override val showTitle = false

    private val service: TrimeInputMethodService by di.instance()
    private val rime: RimeSession by di.instance()
    private val theme: Theme by di.instance()
    private val windowManager: BoardWindowManager by di.instance()
    private val commonKeyboardActionListener: CommonKeyboardActionListener by di.instance()

    private val source by lazy { LiquidSymbolSource(theme) }

    private val state by lazy { SymbolPanelState(source) }

    // the bar asks for this view on every attach, so hand out the same one
    private val tabsView by lazy { context.imeComposeView { SymbolTabs(state) } }

    companion object : ResidentWindow.Key

    override val key: ResidentWindow.Key
        get() = LiquidWindow

    override fun onCreateView(): View = context.imeComposeView {
        SymbolPanel(state, onItemClick = ::onItemClick, onBarKeyClick = ::onBarKeyClick)
    }

    override fun onCreateBarView(): View = tabsView

    override fun onAttached() {}

    override fun onDetached() {}

    fun setDataByIndex(i: Int) = state.select(i)

    private fun onItemClick(item: SymbolItem) {
        when (val action = item.action) {
            is SymbolAction.Commit -> {
                service.commitText(action.text)
                if (action.remember) source.remember(action.text)
            }
            is SymbolAction.TypeKeys -> triggerSymbolInput(action.keys)
            is SymbolAction.OpenCategory -> state.select(action.index)
        }
    }

    private fun onBarKeyClick(key: SymbolBarKey) {
        commonKeyboardActionListener.listener.onAction(KeyActionManager.getAction(key.action))
    }

    private fun triggerSymbolInput(symbol: String) {
        rime.launchOnReady {
            val (isAsciiMode, isAsciiPunch) = it.statusCached.run { isAsciiMode to isAsciiPunct }
            if (isAsciiMode) it.setRuntimeOption("ascii_mode", false)
            if (isAsciiPunch) it.setRuntimeOption("ascii_punch", false)
            it.clearComposition()
            it.simulateKeySequence(symbol)
            if (isAsciiPunch) it.setRuntimeOption("ascii_punch", true)
            ContextCompat.getMainExecutor(service).execute {
                windowManager.attachWindow(KeyboardWindow)
            }
        }
    }
}
