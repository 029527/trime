/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.keyboard.gesture

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import com.osfans.trime.ime.keyboard.InputFeedbackManager
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
 * Keyboard-wide touch handling: every pointer drives its own key through [KeyGestureTracker].
 *
 * Pointer events are handled synchronously in the main pass, so [KeyGestureListener.onPress]
 * fires while the DOWN event is being dispatched, with no touch slop or gesture arbitration.
 */
fun Modifier.keyGestures(
    target: KeyGestureTarget,
    listener: KeyGestureListener,
): Modifier = this then KeyGesturesElement(target, listener)

private class KeyGesturesElement(
    private val target: KeyGestureTarget,
    private val listener: KeyGestureListener,
) : ModifierNodeElement<KeyGesturesNode>() {
    override fun create() = KeyGesturesNode(target, listener)

    override fun update(node: KeyGesturesNode) = node.update(target, listener)

    override fun equals(other: Any?) = other is KeyGesturesElement && other.target === target && other.listener === listener

    override fun hashCode() = 31 * System.identityHashCode(target) + System.identityHashCode(listener)

    override fun InspectorInfo.inspectableProperties() {
        name = "keyGestures"
    }
}

private class KeyGesturesNode(
    target: KeyGestureTarget,
    listener: KeyGestureListener,
) : Modifier.Node(),
    PointerInputModifierNode,
    CompositionLocalConsumerModifierNode {
    private var tracker = newTracker(target, listener)

    fun update(
        target: KeyGestureTarget,
        listener: KeyGestureListener,
    ) {
        tracker.cancel()
        tracker = newTracker(target, listener)
    }

    private fun newTracker(
        target: KeyGestureTarget,
        listener: KeyGestureListener,
    ) = KeyGestureTracker(target, listener, PrefsKeyGestureConfig, MainGestureClock, ::vibrate)

    private fun vibrate(longPress: Boolean) {
        if (!isAttached) return
        InputFeedbackManager.keyPressVibrate(currentValueOf(LocalView), longPress)
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        if (pass != PointerEventPass.Main) return
        for (change in pointerEvent.changes) {
            val pointer = change.id.value
            when {
                change.changedToDownIgnoreConsumed() -> {
                    if (tracker.down(pointer, change.position.x, change.position.y)) change.consume()
                }
                change.changedToUpIgnoreConsumed() -> {
                    tracker.up(pointer)
                    change.consume()
                }
                change.pressed && change.positionChangeIgnoreConsumed() != Offset.Zero -> {
                    tracker.move(pointer, change.position.x, change.position.y)
                    change.consume()
                }
            }
        }
    }

    override fun onCancelPointerInput() = tracker.cancel()

    override fun onDetach() = tracker.cancel()
}
