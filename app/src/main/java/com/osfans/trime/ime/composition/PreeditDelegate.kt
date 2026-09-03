/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewOutlineProvider
import com.osfans.trime.core.Candidates
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.core.TouchEventReceiverWindow
import com.osfans.trime.ime.dependency.InputDependencyManager
import com.osfans.trime.ime.keyboard.T9Assist
import org.kodein.di.instance
import splitties.dimensions.dp
import splitties.views.horizontalPadding

class PreeditDelegate : InputBroadcastReceiver {

    private val context: Context by InputDependencyManager.getInstance().di.instance()
    private val theme: Theme by InputDependencyManager.getInstance().di.instance()
    private val rime: RimeSession by InputDependencyManager.getInstance().di.instance()

    val ui =
        PreeditUi(
            context,
            theme,
            setupPreeditView = {
                val startRadius = dp(theme.preedit.topStartRadius)
                val endRadius = dp(theme.preedit.topEndRadius)
                val radii = if (layoutDirection == View.LAYOUT_DIRECTION_LTR) {
                    floatArrayOf(startRadius, startRadius, endRadius, endRadius, 0f, 0f, 0f, 0f)
                } else {
                    floatArrayOf(endRadius, endRadius, startRadius, startRadius, 0f, 0f, 0f, 0f)
                }
                background = GradientDrawable().apply {
                    setColor(ColorManager.getColor("text_back_color"))
                    shape = GradientDrawable.RECTANGLE
                    cornerRadii = radii
                }
                clipToOutline = true
                outlineProvider = ViewOutlineProvider.BACKGROUND
                horizontalPadding = dp(theme.preedit.horizontalPadding)
            },
            onMoveCursor = { pos -> rime.launchOnReady { it.moveCursorPos(pos) } },
        ).apply {
            root.alpha = theme.preedit.alpha
            root.visibility = View.INVISIBLE
        }

    private val touchEventReceiverWindow = TouchEventReceiverWindow(ui.root)

    private var lastComposition = CompositionProto()
    private var firstComment = ""

    override fun onCandidateListUpdate(data: Candidates.Bulk) {
        val comment = data.candidates.firstOrNull()?.comment.orEmpty()
        if (comment != firstComment) {
            firstComment = comment
            render()
        }
    }

    override fun onCompositionUpdate(data: CompositionProto) {
        lastComposition = data
        render()
    }

    /**
     * For nine-key schemas the raw preedit is a digit string; show the pinyin of the
     * first candidate for the digits it covers instead, like iOS does.
     */
    private fun render() {
        val data = lastComposition
        val preedit = data.preedit
        val isT9 = preedit != null && T9Assist.isT9Input(T9Assist.stripPreedit(preedit))
        T9Assist.composing = isT9
        val shown =
            if (isT9 && firstComment.isNotEmpty()) {
                CompositionProto(T9Assist.displayPreedit(preedit!!, firstComment))
            } else {
                data
            }
        // nine-key: the pinyin above the keyboard is a hint, show it smaller than a normal preedit
        ui.preedit.textSize = theme.preedit.foreground.fontSize * (if (isT9) 0.72f else 1f)
        ui.update(shown)
        ui.root.visibility = if (ui.visible) View.VISIBLE else View.INVISIBLE
        if (data.length > 0) {
            touchEventReceiverWindow.show()
        } else {
            touchEventReceiverWindow.dismiss()
        }
    }
}
