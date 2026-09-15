/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.segments

import android.app.SearchManager
import android.content.ClipData
import android.content.Intent
import android.view.View
import androidx.compose.ui.unit.dp
import com.osfans.trime.R
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.compose.imeComposeView
import com.osfans.trime.ime.compose.segments.SegmentsBar
import com.osfans.trime.ime.compose.segments.SegmentsPanel
import com.osfans.trime.ime.compose.segments.SegmentsPanelState
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.util.NativeTokenizer
import com.osfans.trime.util.toast
import org.kodein.di.instance
import splitties.systemservices.clipboardManager

/**
 * Splits [source] (a clipboard entry) into words to pick from. The selection is typed as
 * composing text while it changes, and committed when the window closes.
 */
class SegmentsWindow(
    private val source: String,
) : BoardWindow.BarBoardWindow() {
    private val service: TrimeInputMethodService by di.instance()
    private val theme: Theme by di.instance()
    private val windowManager: BoardWindowManager by di.instance()

    override val title: String by lazy {
        context.getString(R.string.word_segment)
    }

    private val panelState by lazy {
        SegmentsPanelState(source, NativeTokenizer.tokenize(source))
    }

    override fun onCreateView(): View = context.imeComposeView {
        SegmentsPanel(panelState, onSelectionChanged = ::onSelectionChanged)
    }

    override fun onCreateBarView(): View = context.imeComposeView {
        SegmentsBar(
            state = panelState,
            buttonSpacing = theme.toolBar.buttonSpacing.dp,
            onShare = ::share,
            onSearch = ::search,
            onStar = ::star,
            onCopy = ::copy,
            onToggleAll = ::toggleAll,
        )
    }

    private fun onSelectionChanged() {
        service.updateComposingText(panelState.joined)
    }

    private fun toggleAll() {
        if (panelState.isAllSelected) panelState.clearSelection() else panelState.selectAll()
        onSelectionChanged()
    }

    private fun share() {
        val target = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, panelState.joined)
        }
        val chooser = Intent.createChooser(target, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        service.startActivity(chooser)
    }

    private fun search() {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, panelState.joined)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        service.startActivity(intent)
    }

    private fun star() {
        CollectionHelper.addNewBean(panelState.joined)
        context.toast(R.string.star_success)
    }

    private fun copy() {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("", panelState.joined))
        windowManager.attachWindow(KeyboardWindow)
    }

    override fun onAttached() {}

    override fun onDetached() {
        service.currentInputConnection?.finishComposingText()
    }
}
