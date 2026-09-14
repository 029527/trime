/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.keyboard.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.osfans.trime.ime.keyboard.KeyBehavior

/** What a key can do, which decides the gestures worth waiting for. Mirrors `GestureFrame`'s flags. */
@Immutable
data class KeyCaps(
    val repeatable: Boolean = false,
    val slideCursor: Boolean = false,
    val slideDelete: Boolean = false,
    val hasLongPress: Boolean = false,
    val hasDouble: Boolean = false,
    val hasLazyDouble: Boolean = false,
    val hasPopup: Boolean = false,
)

/** The keyboard as seen by the gesture layer: hit testing and per-key capabilities. */
interface KeyGestureTarget {
    /** Index of the key under ([x], [y]) in keyboard px, or -1. */
    fun keyAt(
        x: Float,
        y: Float,
    ): Int

    fun capsOf(key: Int): KeyCaps

    /** Top-left of the key's touch cell (extra touch width included), the origin of [KeyGestureListener.onMove]. */
    fun cellOrigin(key: Int): Offset
}

/**
 * Per-key gesture events, with the same meaning and order as the callbacks of the old
 * `GestureFrame`, so `KeyView`'s logic ports over one to one. One key per pointer: every
 * pointer is tracked on its own, which is what makes chords and fast two-thumb typing work.
 */
interface KeyGestureListener {
    fun onPress(key: Int)

    /** The finger crossed the swipe threshold (or changed direction): preview what releasing now would do. */
    fun onSwipe(
        key: Int,
        behavior: KeyBehavior,
    )

    /** The gesture ended with [behavior]; [fromLongPress] also covers each repeat of a repeatable key. */
    fun onRelease(
        key: Int,
        behavior: KeyBehavior,
        fromLongPress: Boolean,
    )

    fun onLongPress(key: Int)

    /** Pointer moved; [x] / [y] relative to [KeyGestureTarget.cellOrigin]. */
    fun onMove(
        key: Int,
        x: Float,
        y: Float,
        longPressed: Boolean,
    )

    /** Slide-cursor / slide-delete steps; a final 0 when the finger lifts. */
    fun onSlide(
        key: Int,
        delta: Int,
    )

    fun onCancel(key: Int)
}

/**
 * Keyboard-wide touch handling.
 *
 * Placeholder: taps only, one pointer at a time. Swipes, long press, repeat, double tap,
 * slide and multi-touch are yet to be ported from `GestureFrame`.
 */
fun Modifier.keyGestures(
    target: KeyGestureTarget,
    listener: KeyGestureListener,
): Modifier = pointerInput(target, listener) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val key = target.keyAt(down.position.x, down.position.y)
        if (key < 0) return@awaitEachGesture
        down.consume()
        listener.onPress(key)
        val up = waitForUpOrCancellation()
        if (up != null) {
            up.consume()
            listener.onRelease(key, KeyBehavior.CLICK, false)
        } else {
            listener.onCancel(key)
        }
    }
}
