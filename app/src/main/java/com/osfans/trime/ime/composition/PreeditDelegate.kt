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
import splitties.views.verticalPadding

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

    /**
     * Embedded: the preedit lives inside the candidate bar (floating keyboard) instead of
     * popping up above the keyboard, so it is a small chip that takes no space when empty.
     */
    var embedded = false
        set(value) {
            field = value
            if (value) {
                // a small pinyin line above the candidates, like iOS: plain text, no pill
                ui.root.visibility = View.GONE
                ui.preedit.background = null
                ui.preedit.setTextColor(ColorManager.getColor("comment_text_color"))
                ui.preedit.textSize = theme.generalStyle.commentTextSize
                ui.preedit.horizontalPadding = ui.preedit.dp(4)
                ui.preedit.verticalPadding = 0
                ui.preedit.includeFontPadding = false
            }
        }

    /** Character offsets of the pinyin groups shown for a nine-key input and the raw input length before each. */
    private var t9Groups: List<Pair<IntRange, Int>> = emptyList()

    /**
     * Nine-key: tapping a pinyin group drops that group and everything after it from the
     * input, so the user can type those digits again.
     */
    private fun retypeT9Group(offset: Int) {
        val group = t9Groups.firstOrNull { offset <= it.first.last + 1 } ?: t9Groups.lastOrNull() ?: return
        val keep = group.second
        rime.launchOnReady { api ->
            val raw = api.getRawInput()
            if (!T9Assist.isT9Input(raw)) return@launchOnReady
            api.clearComposition()
            if (keep > 0) api.simulateKeySequence(raw.take(keep))
        }
    }

    private fun t9GroupsOf(shown: String): List<Pair<IntRange, Int>> {
        val out = mutableListOf<Pair<IntRange, Int>>()
        var rawBefore = 0
        var start = 0
        while (start < shown.length) {
            val end = shown.indexOf(' ', start).let { if (it < 0) shown.length else it }
            if (end > start) {
                out += (start until end) to rawBefore
                rawBefore += shown.substring(start, end).count { it.isLetterOrDigit() }
            }
            start = end + 1
        }
        return out
    }

    private var lastComposition = CompositionProto()
    private var firstComment = ""

    override fun onCandidateListUpdate(data: Candidates.Bulk) {
        // the first candidate may be a hot word or an English word without pinyin;
        // take the first candidate that carries one
        val comment = data.candidates.firstOrNull { it.comment.isNotEmpty() }?.comment.orEmpty()
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
        val shown =
            if (isT9 && firstComment.isNotEmpty()) {
                CompositionProto(T9Assist.displayPreedit(preedit!!, firstComment))
            } else {
                data
            }
        if (isT9) {
            t9Groups = t9GroupsOf(shown.preedit.orEmpty())
            ui.preedit.onTapOffset = { retypeT9Group(it) }
        } else {
            t9Groups = emptyList()
            ui.preedit.onTapOffset = null
        }
        // nine-key: the pinyin above the keyboard is a hint, show it smaller than a normal preedit
        if (!embedded) ui.preedit.textSize = theme.preedit.foreground.fontSize * (if (isT9) 0.72f else 1f)
        ui.update(shown)
        if (embedded) {
            ui.root.visibility = if (ui.visible) View.VISIBLE else View.GONE
            return
        }
        ui.root.visibility = if (ui.visible) View.VISIBLE else View.INVISIBLE
        if (data.length > 0) {
            touchEventReceiverWindow.show()
        } else {
            touchEventReceiverWindow.dismiss()
        }
    }
}
