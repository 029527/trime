/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.compact

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.osfans.trime.R
import com.osfans.trime.ime.bar.InputBarDelegate
import com.osfans.trime.ime.bar.UnrollButtonStateMachine
import com.osfans.trime.ime.compose.candidates.CandidateBar
import com.osfans.trime.ime.compose.candidates.CandidateBarHead
import com.osfans.trime.ime.compose.candidates.CandidateBarState
import com.osfans.trime.ime.compose.imeComposeView
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.dependency.InputDependencyManager
import com.osfans.trime.ime.session.InputSession
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.kodein.di.instance
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.gravityStart

/**
 * Hosts the Compose candidate bar ([CandidateBar]) and tells the unroll button and the
 * unrolled grid how much of the list the bar covers.
 *
 * The bar scrolls horizontally, so "what the bar shows" is defined at its start position:
 * the candidates that fit there completely are the bar's head, and the grid continues
 * after them. Opening the grid scrolls the bar back to the start so the two line up.
 */
class CompactCandidateDelegate {
    private val di = InputDependencyManager.getInstance().di
    private val context: Context by di.instance()
    private val session: InputSession by di.instance()
    private val inputView: InputView by di.instance()
    private val bar: InputBarDelegate by di.instance()

    private val _unrolledCandidateOffset =
        MutableSharedFlow<Int>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /**
     * Global index the unrolled grid starts from, i.e. the size of the bar's head.
     * -1 when there is no candidate at all, which closes the grid.
     */
    val unrolledCandidateOffset = _unrolledCandidateOffset.asSharedFlow()

    /** Whether the bar's head is the whole list, so there is nothing to unroll. */
    var showsAllCandidates = true
        private set

    private val barState = CandidateBarState(session::loadCandidates)

    private fun onHeadMeasured(head: CandidateBarHead) {
        showsAllCandidates = head.showsAll
        _unrolledCandidateOffset.tryEmit(if (head.isEmpty) -1 else head.head)
        bar.unrollButtonStateMachine.push(
            UnrollButtonStateMachine.TransitionEvent.UnrolledCandidatesUpdated,
            UnrollButtonStateMachine.BooleanKey.UnrolledCandidatesEmpty to head.showsAll,
        )
    }

    /** Back to the start, where the bar's head is what the grid continues from. */
    fun scrollToStart() = barState.scrollToStart()

    /**
     * PopupMenu hangs from a View. The ComposeView itself would put the menu at the bar's
     * start edge, so this 1px view is moved under the pressed candidate instead.
     */
    private val menuAnchor = View(context)

    val view: View by lazy {
        val composeView =
            context
                .imeComposeView {
                    CandidateBar(
                        state = barState,
                        input = session.state,
                        onSelect = { session.selectCandidate(it) },
                        onLongPress = { index, text, x ->
                            menuAnchor.translationX = x.toFloat()
                            inputView.showCandidateActionMenu(index, text, menuAnchor, global = true)
                        },
                        onHeadMeasured = ::onHeadMeasured,
                        // from session.state, not the broadcast: a rebuilt keyboard gets its
                        // candidates back through InputSession.restoreFromEngine only
                        onEmptyChanged = bar::onCandidatesEmptyChanged,
                    )
                }.apply {
                    id = R.id.candidate_view
                    isFocusable = false
                    isFocusableInTouchMode = false
                    isSoundEffectsEnabled = false
                    isHapticFeedbackEnabled = false
                }
        FrameLayout(context).apply {
            add(composeView, lParams(matchParent, matchParent))
            add(menuAnchor, lParams(1, matchParent, gravityStart))
        }
    }
}
