/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.content.Context
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osfans.trime.ime.compose.composition.PreeditBar
import com.osfans.trime.ime.compose.composition.PreeditStyle
import com.osfans.trime.ime.compose.composition.PreeditText
import com.osfans.trime.ime.compose.imeComposeView
import com.osfans.trime.ime.core.TouchEventReceiverWindow
import com.osfans.trime.ime.dependency.InputDependencyManager
import com.osfans.trime.ime.session.InputSession
import org.kodein.di.instance
import splitties.views.dsl.core.Ui

/**
 * Owns the preedit bar of one `InputView`.
 *
 * Above the keyboard the bar sits outside the IME window's touchable region, so taps would
 * fall through to the app; a [TouchEventReceiverWindow] laid over it takes them and hands
 * them back. When [embedded] the bar lives inside the candidate bar, which is touchable.
 */
class PreeditDelegate {
    private val context: Context by InputDependencyManager.getInstance().di.instance()
    private val session: InputSession by InputDependencyManager.getInstance().di.instance()

    /**
     * Embedded: the preedit lives inside the candidate bar (floating keyboard) instead of
     * popping up above the keyboard. Set before [ui] is attached anywhere.
     */
    var embedded by mutableStateOf(false)

    val ui =
        object : Ui {
            override val ctx = context
            override val root =
                context.imeComposeView {
                    val state by session.state.collectAsStateWithLifecycle()
                    val preedit =
                        remember(state.composition, state.candidates) {
                            PreeditText.of(state.composition, state.candidates.candidates)
                        }
                    PreeditBar(
                        preedit = preedit,
                        style = if (embedded) PreeditStyle.Embedded else PreeditStyle.Floating,
                        onMoveCursor = session::moveCursor,
                    )
                }
        }

    private val touchEventReceiverWindow = TouchEventReceiverWindow(ui.root)

    init {
        // the bar emits nothing when empty, so its height tells whether there is anything to touch;
        // every content change that matters resizes the view and ends up here
        ui.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncTouchWindow() }
        ui.root.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = syncTouchWindow()

                override fun onViewDetachedFromWindow(v: View) = touchEventReceiverWindow.dismiss()
            },
        )
    }

    private fun syncTouchWindow() {
        val root = ui.root
        // the popup needs a window token: never show it for a preedit view that is not attached
        if (!embedded && root.isAttachedToWindow && root.width > 0 && root.height > 0) {
            touchEventReceiverWindow.show()
        } else {
            touchEventReceiverWindow.dismiss()
        }
    }
}
