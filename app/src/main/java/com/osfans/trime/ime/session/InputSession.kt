/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.session

import android.os.Looper
import android.view.inputmethod.EditorInfo
import com.osfans.trime.BuildConfig
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.core.Candidates
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.SchemaItem
import com.osfans.trime.core.StatusProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

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
 *
 * Every `on*` feed must be called on the main thread: `InputView` collects the message
 * flow in the service's lifecycle scope (`Dispatchers.Main.immediate`) and the service
 * callbacks run there too. A call from another thread throws in debug builds and is
 * logged in release builds.
 *
 * @param isMainThread overridable for JVM unit tests, where there is no main looper
 */
class DefaultInputSession(
    private val rime: RimeSession,
    private val isMainThread: () -> Boolean = { Looper.myLooper() == Looper.getMainLooper() },
) : InputSession {
    private val _state = MutableStateFlow(InputState())
    override val state: StateFlow<InputState> = _state.asStateFlow()

    /**
     * The engine fields of the response being received, published as a whole when the
     * response ends. Null outside a response.
     */
    private var pending: InputState? = null

    /** Bumped by every engine update, so a slow [restoreFromEngine] fetch can tell it is stale. */
    private var engineRevision = 0

    /**
     * Feed one message from the rime message flow.
     *
     * `Rime.emitResponse` sends every engine response as a fixed run of messages:
     * [RimeMessage.CommitTextMessage] first and [RimeMessage.StatusMessage] last, with the
     * composition and the candidates in between. They reach the main thread as separate
     * dispatches, so updating the state per message lets the UI draw a frame with the new
     * preedit and the old candidates. Instead the run is collected into [pending] and
     * published once, at the status message.
     *
     * Messages outside such a run (librime notifications, the ascii mode tip and its
     * removal) apply immediately. A commit message arriving while a run is still open,
     * which only happens if the flow dropped the closing status, publishes the open run first.
     */
    fun onRimeMessage(message: RimeMessage<*>) {
        checkMainThread()
        when (message) {
            is RimeMessage.CommitTextMessage -> beginResponse()
            is RimeMessage.SchemaMessage -> onSchema(message.data)
            is RimeMessage.OptionMessage -> onOption(message.data.option, message.data.value)
            is RimeMessage.BulkCandidatesMessage -> onCandidates(message.data)
            is RimeMessage.PagedCandidatesMessage -> onPagedCandidates(message.data)
            is RimeMessage.StatusMessage -> {
                onStatus(message.data)
                endResponse()
            }
            // composition is fed through onComposition: InputView may blank it first
            else -> {}
        }
    }

    fun onComposition(data: CompositionProto) = updateEngine { it.copy(composition = data) }

    fun onCandidates(data: Candidates.Bulk) = updateEngine {
        it.copy(candidates = data, hasMenu = data.candidates.isNotEmpty(), paging = false)
    }

    /** The floating candidates window renders a paged menu; the keyboard's own list stays empty. */
    fun onPagedCandidates(data: Candidates.Paged) = updateEngine {
        it.copy(candidates = Candidates.Bulk(), hasMenu = data.candidates.isNotEmpty(), paging = data.hasPrevPage)
    }

    fun onStatus(data: StatusProto) = updateEngine { it.copy(status = data) }

    fun onSchema(data: SchemaItem) = updateEngine { it.copy(schema = data) }

    /**
     * librime sends no status after an option set outside a key press (e.g. the ascii mode
     * toggle on the bar), so the options [StatusProto] mirrors are patched here.
     */
    fun onOption(
        option: String,
        value: Boolean,
    ) = updateEngine { it.copy(options = it.options + (option to value), status = it.status.withOption(option, value)) }

    fun onStartInput(info: EditorInfo) = updateEditor {
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
    ) = updateEditor { it.copy(editor = it.editor.copy(selectionStart = start, selectionEnd = end)) }

    fun onEnterKey(
        label: String,
        isPrimaryAction: Boolean,
    ) = updateEditor { it.copy(enterKey = EnterKeyState(label, isPrimaryAction)) }

    /**
     * Start from what the engine already has. `InputView` is rebuilt on every theme or
     * setting change while librime may be in the middle of a composition, and the next
     * response may be a key press away.
     *
     * The status, composition and menu flags come from `Rime`'s caches synchronously.
     * Rime caches no candidate list, so when there is a menu the head of it is fetched
     * in [scope] and dropped if a newer engine update arrived meanwhile.
     *
     * @param pagedMode the floating candidates window owns composition and menu
     *   (`PopupCandidatesMode.ALWAYS_SHOW`): keep [InputState.composition] and
     *   [InputState.candidates] blank, as [onRimeMessage] would
     */
    fun restoreFromEngine(
        scope: CoroutineScope,
        pagedMode: Boolean,
    ) {
        checkMainThread()
        val (status, composition, hasMenu, paging) =
            rime.run { EngineCache(statusCached, compositionCached, hasMenu, paging) }
        // an empty schema id means librime has not answered yet: StatusProto's defaults say nothing
        val known = status.schemaId.isNotEmpty()
        updateEngine {
            it.copy(
                composition = if (pagedMode) CompositionProto() else composition,
                candidates = Candidates.Bulk(),
                status = status,
                schema = if (known) SchemaItem(status.schemaId, status.schemaName) else it.schema,
                options = if (known) it.options + status.options() else it.options,
                hasMenu = hasMenu,
                paging = pagedMode && paging,
            )
        }
        if (pagedMode || !hasMenu) return
        val revision = engineRevision
        scope.launch {
            val head = rime.runOnReady { getCandidates(0, BULK_LIMIT) }
            if (revision != engineRevision || pending != null) return@launch
            val total = if (head.size < BULK_LIMIT) head.size else -1
            updateEngine { it.copy(candidates = Candidates.Bulk(total, 0, head)) }
        }
    }

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

    private fun beginResponse() {
        endResponse()
        pending = _state.value
    }

    private fun endResponse() {
        val done = pending ?: return
        pending = null
        _state.update {
            it.copy(
                composition = done.composition,
                candidates = done.candidates,
                status = done.status,
                schema = done.schema,
                options = done.options,
                hasMenu = done.hasMenu,
                paging = done.paging,
            )
        }
    }

    private inline fun updateEngine(transform: (InputState) -> InputState) {
        checkMainThread()
        engineRevision++
        val open = pending
        if (open != null) {
            pending = transform(open)
        } else {
            _state.update(transform)
        }
    }

    /** Editor facts come from the service, not the engine: never held back by an open response. */
    private inline fun updateEditor(transform: (InputState) -> InputState) {
        checkMainThread()
        _state.update(transform)
    }

    private fun checkMainThread() {
        if (isMainThread()) return
        val error = IllegalStateException("InputSession updated off the main thread: ${Thread.currentThread().name}")
        if (BuildConfig.DEBUG) throw error
        Timber.e(error)
    }

    private data class EngineCache(
        val status: StatusProto,
        val composition: CompositionProto,
        val hasMenu: Boolean,
        val paging: Boolean,
    )

    companion object {
        /** Same as the bulk list librime_jni puts into every response. */
        private const val BULK_LIMIT = 16

        /** The option names behind [StatusProto]'s flags, see `RimeGetStatus` in librime. */
        private fun StatusProto.withOption(
            option: String,
            value: Boolean,
        ) = when (option) {
            "ascii_mode" -> copy(isAsciiMode = value)
            "full_shape" -> copy(isFullShape = value)
            "simplification" -> copy(isSimplified = value)
            "traditional" -> copy(isTraditional = value)
            "ascii_punct" -> copy(isAsciiPunct = value)
            else -> this
        }

        private fun StatusProto.options() = mapOf(
            "ascii_mode" to isAsciiMode,
            "full_shape" to isFullShape,
            "simplification" to isSimplified,
            "traditional" to isTraditional,
            "ascii_punct" to isAsciiPunct,
        )
    }
}
