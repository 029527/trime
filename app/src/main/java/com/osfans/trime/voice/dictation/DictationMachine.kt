/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.dictation

/** Why listening ended gracefully. Every reason waits for the recogniser's final result. */
enum class StopReason { USER, SILENCE, NO_SPEECH, MAX_DURATION }

sealed interface DictationState {
    data object Idle : DictationState

    /** Microphone open. [text] is the pending (composing) part shown in the editor. */
    data class Listening(
        val text: String,
    ) : DictationState

    /** Microphone closed, waiting for the recogniser to finalise [text]. */
    data class Finishing(
        val text: String,
    ) : DictationState

    /** Shown briefly in the dictation pill, then back to [Idle]. */
    data class Failed(
        val message: String,
    ) : DictationState
}

sealed interface DictationEvent {
    data object Start : DictationEvent

    data class Partial(
        val text: String,
    ) : DictationEvent

    data class Final(
        val text: String,
    ) : DictationEvent

    data class Stop(
        val reason: StopReason,
    ) : DictationEvent

    /** Another key, the cursor leaving the text, the keyboard closing, the editor changing. */
    data object Interrupt : DictationEvent

    data class Failure(
        val message: String,
    ) : DictationEvent

    /** The recogniser's event stream ended. */
    data object Completed : DictationEvent

    data object ErrorDismissed : DictationEvent
}

sealed interface DictationEffect {
    /** Open the microphone and the recogniser. */
    data object BeginSession : DictationEffect

    /** Close the microphone; the recogniser keeps running to deliver its final result. */
    data object StopAudio : DictationEffect

    /** Tear the session down: cancel the recogniser, close the microphone, stop watching the cursor. */
    data object EndSession : DictationEffect

    data class SetComposing(
        val text: String,
    ) : DictationEffect

    /** Replace the composing text with [text] and commit it; an empty [text] commits what is shown. */
    data class Commit(
        val text: String,
    ) : DictationEffect

    /** Commit the composing text as shown. */
    data object FinishComposing : DictationEffect

    data object ScheduleErrorDismiss : DictationEffect
}

data class DictationTransition(
    val state: DictationState,
    val effects: List<DictationEffect> = emptyList(),
)

/**
 * The dictation state machine, free of Android: `Idle → Listening → Finishing → Idle`, with
 * `Failed` as a short-lived branch.
 *
 * One rule for text: **whatever was recognised is kept**. Ending gracefully (tap the mic again,
 * silence, time limit) waits for the final result and commits it; ending abruptly (another key,
 * the cursor moving away, the keyboard closing, an error) commits the pending text as shown at
 * once. Nothing is ever discarded, and nothing is left as composing text.
 */
object DictationMachine {
    fun reduce(
        state: DictationState,
        event: DictationEvent,
    ): DictationTransition = when (event) {
        DictationEvent.Start -> when (state) {
            DictationState.Idle, is DictationState.Failed ->
                DictationTransition(DictationState.Listening(""), listOf(DictationEffect.BeginSession))
            else -> DictationTransition(state)
        }

        is DictationEvent.Partial -> when (state) {
            is DictationState.Listening ->
                DictationTransition(DictationState.Listening(event.text), listOf(DictationEffect.SetComposing(event.text)))
            is DictationState.Finishing ->
                DictationTransition(DictationState.Finishing(event.text), listOf(DictationEffect.SetComposing(event.text)))
            else -> DictationTransition(state)
        }

        // a final result may arrive mid-session (a finished sentence): commit it and keep going
        is DictationEvent.Final -> when (state) {
            is DictationState.Listening ->
                DictationTransition(DictationState.Listening(""), listOf(DictationEffect.Commit(event.text)))
            is DictationState.Finishing ->
                DictationTransition(DictationState.Finishing(""), listOf(DictationEffect.Commit(event.text)))
            else -> DictationTransition(state)
        }

        is DictationEvent.Stop -> when (state) {
            is DictationState.Listening ->
                DictationTransition(DictationState.Finishing(state.text), listOf(DictationEffect.StopAudio))
            else -> DictationTransition(state)
        }

        DictationEvent.Interrupt -> when (state) {
            is DictationState.Listening, is DictationState.Finishing ->
                DictationTransition(DictationState.Idle, keepPending(state) + DictationEffect.EndSession)
            is DictationState.Failed -> DictationTransition(DictationState.Idle)
            DictationState.Idle -> DictationTransition(state)
        }

        is DictationEvent.Failure -> when (state) {
            is DictationState.Listening, is DictationState.Finishing ->
                DictationTransition(
                    DictationState.Failed(event.message),
                    keepPending(state) + DictationEffect.EndSession + DictationEffect.ScheduleErrorDismiss,
                )
            // refused before a session started (disabled, no credentials), or a newer error
            else -> DictationTransition(DictationState.Failed(event.message), listOf(DictationEffect.ScheduleErrorDismiss))
        }

        DictationEvent.Completed -> when (state) {
            is DictationState.Listening, is DictationState.Finishing ->
                DictationTransition(DictationState.Idle, keepPending(state) + DictationEffect.EndSession)
            else -> DictationTransition(state)
        }

        DictationEvent.ErrorDismissed -> when (state) {
            is DictationState.Failed -> DictationTransition(DictationState.Idle)
            else -> DictationTransition(state)
        }
    }

    /**
     * Whether a selection update means the user put the cursor somewhere else while dictating.
     * Only judged while there is composing text: dictation keeps the cursor at its end.
     */
    fun cursorLeftComposition(
        selStart: Int,
        selEnd: Int,
        composingStart: Int,
        composingEnd: Int,
    ): Boolean = composingStart >= 0 && composingEnd >= 0 && (selStart != selEnd || selEnd != composingEnd)

    private fun keepPending(state: DictationState): List<DictationEffect> {
        val text = when (state) {
            is DictationState.Listening -> state.text
            is DictationState.Finishing -> state.text
            else -> ""
        }
        return if (text.isEmpty()) emptyList() else listOf(DictationEffect.FinishComposing)
    }
}

/**
 * Turns the recogniser's running transcripts into the part not committed yet.
 *
 * Recognisers send the whole transcript so far with every result, also after a sentence was
 * finalised mid-session and committed. Without this, the next partial would repeat the committed
 * sentence in the composing text.
 */
class TranscriptSegmenter {
    private var committed = ""

    fun reset() {
        committed = ""
    }

    fun partial(transcript: String): String = remainder(transcript)

    fun final(transcript: String): String {
        val rest = remainder(transcript)
        committed = if (transcript.startsWith(committed)) transcript else committed + rest
        return rest
    }

    private fun remainder(transcript: String): String = if (committed.isNotEmpty() && transcript.startsWith(committed)) {
        transcript.substring(committed.length)
    } else {
        transcript
    }
}
