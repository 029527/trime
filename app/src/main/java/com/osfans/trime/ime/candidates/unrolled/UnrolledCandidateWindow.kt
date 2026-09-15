/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.unrolled

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import androidx.transition.Slide
import androidx.transition.Transition
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.InputBarDelegate
import com.osfans.trime.ime.bar.UnrollButtonStateMachine
import com.osfans.trime.ime.candidates.compact.CompactCandidateDelegate
import com.osfans.trime.ime.compose.candidates.unrolled.UnrolledCandidates
import com.osfans.trime.ime.compose.candidates.unrolled.UnrolledCandidatesState
import com.osfans.trime.ime.compose.candidates.unrolled.UnrolledGridConfig
import com.osfans.trime.ime.compose.imeComposeView
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.session.InputSession
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.kodein.di.instance
import splitties.dimensions.dp

/**
 * The candidates that do not fit in the bar, in place of the keyboard. Opened and closed by the
 * bar's unroll button; starts right after the bar's head
 * ([CompactCandidateDelegate.unrolledCandidateOffset]) and follows it as the composition changes.
 */
class UnrolledCandidateWindow : BoardWindow.NoBarBoardWindow() {
    private val service: TrimeInputMethodService by di.instance()
    private val theme: Theme by di.instance()
    private val inputView: InputView by di.instance()
    private val bar: InputBarDelegate by di.instance()
    private val windowManager: BoardWindowManager by di.instance()
    private val compactCandidate: CompactCandidateDelegate by di.instance()
    private val session: InputSession by di.instance()

    private val state = UnrolledCandidatesState(session::loadCandidates)

    private val itemHeightDp = theme.generalStyle.run { candidateViewHeight + commentHeight }

    /** PopupMenu hangs from a View: this one is moved onto the long-pressed candidate. */
    private val menuAnchor by lazy { View(context) }

    private var offsetJob: Job? = null

    override fun exitAnimation(nextWindow: BoardWindow): Transition = Slide().apply {
        slideEdge = Gravity.TOP
    }

    override fun onCreateView(): View {
        val config =
            UnrolledGridConfig(
                itemHeightDp = itemHeightDp,
                separatorDp = theme.generalStyle.candidateSpacing,
                borderPx = context.dp(theme.generalStyle.candidateBorder),
                borderRadiusPx = context.dp(theme.generalStyle.candidateBorderRound),
            )
        val grid =
            context.imeComposeView {
                UnrolledCandidates(
                    state = state,
                    config = config,
                    onSelect = session::selectCandidate,
                    onLongPress = { index, text, bounds ->
                        menuAnchor.translationX = bounds.left.toFloat()
                        menuAnchor.translationY = bounds.top.toFloat()
                        inputView.showCandidateActionMenu(index, text, menuAnchor, global = true)
                    },
                )
            }
        return FrameLayout(context).apply {
            addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(menuAnchor, FrameLayout.LayoutParams(1, context.dp(itemHeightDp), Gravity.TOP or Gravity.START))
        }
    }

    override fun onAttached() {
        bar.unrollButtonStateMachine.push(UnrollButtonStateMachine.TransitionEvent.UnrolledCandidatesAttached)
        offsetJob =
            service.lifecycleScope.launch {
                compactCandidate.unrolledCandidateOffset.collect {
                    // -1: no candidates left; 0 is a real offset (the first candidate is wider than the bar)
                    if (it < 0) {
                        windowManager.attachWindow(KeyboardWindow)
                    } else {
                        state.reset(it, session.state.value.candidates)
                    }
                }
            }
    }

    override fun onDetached() {
        bar.unrollButtonStateMachine.push(
            UnrollButtonStateMachine.TransitionEvent.UnrolledCandidatesDetached,
            UnrollButtonStateMachine.BooleanKey.UnrolledCandidatesEmpty to compactCandidate.showsAllCandidates,
        )
        offsetJob?.cancel()
    }
}
