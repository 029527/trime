/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.edit

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.osfans.trime.R
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.ime.compose.edit.EditPanel
import com.osfans.trime.ime.compose.imeComposeView
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import com.osfans.trime.ime.window.BoardWindow
import org.kodein.di.instance

/**
 * Cursor keys, select mode and clipboard commands over the keyboard, opened by the `edit_panel`
 * command. Every key goes through the keyboard's action listener as a key action, so it behaves
 * the same as the equivalent key on a keyboard layout.
 */
class EditPanelWindow : BoardWindow.BarBoardWindow() {
    private val rime: RimeSession by di.instance()
    private val actionListener: CommonKeyboardActionListener by di.instance()

    override val title: String by lazy { context.getString(R.string.edit) }

    /** Cursor keys extend the selection while on; off each time the panel opens. */
    private var selecting by mutableStateOf(false)

    override fun onCreateView(): View = context.imeComposeView {
        EditPanel(
            selecting = selecting,
            onKey = ::sendKey,
            onCommand = ::runCommand,
            onToggleSelect = ::toggleSelect,
        )
    }

    private fun sendKey(key: EditKey) {
        val composing = rime.run { statusCached }.isComposing
        actionListener.listener.onAction(KeyActionManager.getAction(key.token(selecting, composing)))
    }

    private fun runCommand(command: EditCommand) {
        actionListener.listener.onAction(KeyActionManager.getAction(command.presetKey))
        selecting = command.selectingAfter
    }

    private fun toggleSelect() {
        selecting = !selecting
    }

    override fun onAttached() {}

    override fun onDetached() {}
}
