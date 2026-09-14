/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.session

import android.view.inputmethod.EditorInfo
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.core.Candidates
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.SchemaItem
import com.osfans.trime.core.StatusProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The seam between the engine and the keyboard UI: one [state] to read, a handful of
 * intents to send. UI code must not talk to [RimeSession] directly for anything this
 * interface covers, so it can be rendered against a fake in previews and tests.
 *
 * All members are main-thread only.
 */
interface InputSession {
    val state: StateFlow<InputState>

    /** Select the candidate at [index], counted from the first candidate of the whole list. */
    fun selectCandidate(index: Int)

    /** Remove the candidate at [index] (global) from the user dictionary. */
    fun forgetCandidate(index: Int)

    /** Move the caret inside the composition, [position] in preedit characters. */
    fun moveCursor(position: Int)

    /** Fetch candidates beyond what [InputState.candidates] carries, for scrolling lists. */
    suspend fun loadCandidates(
        start: Int,
        limit: Int,
    ): List<CandidateProto>
}

/**
 * The real session. `InputView` feeds it from the rime message flow and the service
 * callbacks, in the same order the old broadcaster sees them.
 */
class DefaultInputSession(
    private val rime: RimeSession,
) : InputSession {
    private val _state = MutableStateFlow(InputState())
    override val state: StateFlow<InputState> = _state.asStateFlow()

    fun onRimeMessage(message: RimeMessage<*>) {
        when (message) {
            is RimeMessage.SchemaMessage -> onSchema(message.data)
            is RimeMessage.OptionMessage ->
                _state.update { it.copy(options = it.options + (message.data.option to message.data.value)) }
            is RimeMessage.StatusMessage -> onStatus(message.data)
            is RimeMessage.BulkCandidatesMessage -> onCandidates(message.data)
            // composition is fed through onComposition: InputView may blank it first
            else -> {}
        }
    }

    fun onComposition(data: CompositionProto) = _state.update { it.copy(composition = data) }

    fun onCandidates(data: Candidates.Bulk) = _state.update { it.copy(candidates = data) }

    fun onStatus(data: StatusProto) = _state.update { it.copy(status = data) }

    fun onSchema(data: SchemaItem) = _state.update { it.copy(schema = data) }

    fun onStartInput(info: EditorInfo) = _state.update {
        it.copy(
            editor =
                EditorState(
                    packageName = info.packageName.orEmpty(),
                    inputType = info.inputType,
                    imeOptions = info.imeOptions,
                    selectionStart = info.initialSelStart,
                    selectionEnd = info.initialSelEnd,
                ),
        )
    }

    fun onSelection(
        start: Int,
        end: Int,
    ) = _state.update { it.copy(editor = it.editor.copy(selectionStart = start, selectionEnd = end)) }

    override fun selectCandidate(index: Int) {
        rime.launchOnReady { it.selectCandidate(index, global = true) }
    }

    override fun forgetCandidate(index: Int) {
        rime.launchOnReady { it.deleteCandidate(index, global = true) }
    }

    override fun moveCursor(position: Int) {
        rime.launchOnReady { it.moveCursorPos(position) }
    }

    override suspend fun loadCandidates(
        start: Int,
        limit: Int,
    ): List<CandidateProto> = rime.runOnReady { getCandidates(start, limit) }.toList()
}
