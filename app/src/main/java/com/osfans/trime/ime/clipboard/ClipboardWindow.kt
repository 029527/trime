/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.app.AlertDialog
import android.content.Intent
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.osfans.trime.R
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ime.compose.clipboard.ClipboardBar
import com.osfans.trime.ime.compose.clipboard.ClipboardPage
import com.osfans.trime.ime.compose.clipboard.ClipboardPages
import com.osfans.trime.ime.compose.clipboard.ClipboardPanelState
import com.osfans.trime.ime.compose.clipboard.PanelMenuAction
import com.osfans.trime.ime.compose.imeComposeView
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.segments.SegmentsWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ui.main.ClipEditActivity
import com.osfans.trime.util.AppUtils
import kotlinx.coroutines.launch
import org.kodein.di.instance

/** Clipboard history and collection; the tabs go into the bar, the lists cover the keyboard. */
class ClipboardWindow(
    initialTab: Int = 0,
) : BoardWindow.BarBoardWindow() {
    private val service: TrimeInputMethodService by di.instance()
    private val windowManager: BoardWindowManager by di.instance()

    private val prefs = AppPrefs.defaultInstance().clipboard
    private val clipboardReturnAfterPaste by prefs.clipboardReturnAfterPaste

    private val panelState = ClipboardPanelState(initialTab)

    private val clipboardBeans = Pager(PagingConfig(pageSize = 16)) { ClipboardHelper.allBeans() }.flow
    private val collectionBeans = Pager(PagingConfig(pageSize = 16)) { CollectionHelper.allBeans() }.flow

    override fun onCreateView(): View = context.imeComposeView {
        ClipboardPages(
            state = panelState,
            clipboardBeans = clipboardBeans,
            collectionBeans = collectionBeans,
            onPaste = ::paste,
            menuFor = ::menuFor,
        )
    }

    override fun onCreateBarView(): View = context.imeComposeView {
        ClipboardBar(panelState, onDeleteAll = ::promptDeleteAll)
    }

    private fun paste(bean: DatabaseBean) {
        val text = bean.text ?: return
        service.commitText(text)
        if (clipboardReturnAfterPaste) {
            windowManager.attachWindow(KeyboardWindow)
        }
    }

    private fun menuFor(
        page: ClipboardPage,
        bean: DatabaseBean,
    ): List<PanelMenuAction> = buildList {
        val text = bean.text
        add(
            PanelMenuAction(context.getString(R.string.edit), R.drawable.ic_baseline_edit_24) {
                val from = if (page == ClipboardPage.Clipboard) ClipEditActivity.FROM_CLIPBOARD else ClipEditActivity.FROM_COLLECTION
                AppUtils.launchClipEdit(context, bean.id, from)
            },
        )
        add(
            PanelMenuAction(context.getString(R.string.share), R.drawable.ic_baseline_share_24) {
                text?.let(::launchTextSharing)
            },
        )
        add(
            PanelMenuAction(context.getString(R.string.word_segment), R.drawable.ic_baseline_view_comfy_24) {
                text?.let { windowManager.attachWindow(SegmentsWindow(it)) }
            },
        )
        if (page == ClipboardPage.Clipboard) {
            add(
                PanelMenuAction(context.getString(R.string.collect), R.drawable.ic_baseline_star_24) {
                    CollectionHelper.addNewBean(text ?: "")
                },
            )
            if (bean.pinned) {
                add(
                    PanelMenuAction(context.getString(R.string.simple_key_unpin), R.drawable.ic_outline_push_pin_24) {
                        service.lifecycleScope.launch { ClipboardHelper.unpin(bean.id) }
                    },
                )
            } else {
                add(
                    PanelMenuAction(context.getString(R.string.simple_key_pin), R.drawable.ic_baseline_push_pin_24) {
                        service.lifecycleScope.launch { ClipboardHelper.pin(bean.id) }
                    },
                )
            }
        }
        add(
            PanelMenuAction(context.getString(R.string.delete), R.drawable.ic_baseline_delete_24) {
                service.lifecycleScope.launch {
                    when (page) {
                        ClipboardPage.Clipboard -> ClipboardHelper.delete(bean.id)
                        ClipboardPage.Collection -> CollectionHelper.delete(bean.id)
                    }
                }
            },
        )
    }

    private fun launchTextSharing(text: String) {
        val target = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(target, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        service.startActivity(chooser)
    }

    private fun promptDeleteAll(page: ClipboardPage) {
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.delete_all)
            .setMessage(R.string.ask_to_delete_all)
            .setPositiveButton(R.string.ok) { _, _ ->
                service.lifecycleScope.launch {
                    when (page) {
                        ClipboardPage.Clipboard -> ClipboardHelper.deleteAll(ClipboardHelper.haveUnpinned())
                        ClipboardPage.Collection -> CollectionHelper.deleteAll(CollectionHelper.haveUnpinned())
                    }
                }
            }.setNegativeButton(R.string.cancel, null)
            .create()
        service.showDialog(dialog)
    }

    // the lists collect their pagers inside the composition, which ends with the view
    override fun onAttached() {}

    override fun onDetached() {}
}
