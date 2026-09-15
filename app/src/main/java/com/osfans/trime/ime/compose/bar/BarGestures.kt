/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.bar

import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import com.osfans.trime.ime.compose.keyboard.gesture.KeyGestureConfig
import com.osfans.trime.ime.compose.keyboard.gesture.PrefsKeyGestureConfig
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import kotlin.math.abs
import kotlin.math.max

/**
 * Touch handling of a bar button, with the semantics the View bar's `GestureFrame` had:
 *
 * - a tap clicks on release, wherever the finger is by then;
 * - holding past the long-press timeout long-clicks, or repeats the click while held when
 *   [repeatable];
 * - moving far or fast enough is a swipe and never clicks; a downward swipe calls
 *   [onSwipeDown] at once, which is how the hide and unroll buttons hide the keyboard.
 *
 * [pressed] follows the finger for the pressed look.
 */
@Composable
internal fun Modifier.barButtonGestures(
    pressed: MutableState<Boolean>,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
    repeatable: Boolean = false,
    config: KeyGestureConfig = PrefsKeyGestureConfig,
): Modifier {
    val view = LocalView.current
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    val swipeDown by rememberUpdatedState(onSwipeDown)
    val repeat by rememberUpdatedState(repeatable)
    return pointerInput(config) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            pressed.value = true
            vibrate(view, config)
            try {
                val swipe = SwipeTracker(down.position, down.uptimeMillis, config) { swipeDown?.invoke() }
                val holdable = longClick != null || repeat
                val timeout = if (holdable) config.longPressTimeout.toLong() else Long.MAX_VALUE
                // null: the long-press timeout ran out with the finger still down
                val released = withTimeoutOrNull(timeout) { awaitRelease(down.id, swipe) }
                when {
                    released == true -> if (!swipe.swiped) click()
                    released == false || swipe.swiped -> awaitRelease(down.id, swipe)
                    repeat -> {
                        vibrate(view, config, longPress = true)
                        do {
                            click()
                            val up = withTimeoutOrNull(config.repeatInterval.toLong()) { awaitRelease(down.id, swipe) }
                            if (up == null && config.vibrateOnKeyRepeat) InputFeedbackManager.keyPressVibrate(view)
                        } while (up == null)
                    }
                    else -> {
                        vibrate(view, config, longPress = true)
                        longClick?.invoke()
                        awaitRelease(down.id, swipe)
                    }
                }
            } finally {
                pressed.value = false
            }
        }
    }
}

/** Swipe detection with `GestureFrame`'s thresholds; fires [onSwipeDown] once, when it first applies. */
private class SwipeTracker(
    private val start: Offset,
    private val startTime: Long,
    private val config: KeyGestureConfig,
    private val onSwipeDown: () -> Unit,
) {
    var swiped = false
        private set

    fun track(change: PointerInputChange) {
        if (swiped) return
        val dx = change.position.x - start.x
        val dy = change.position.y - start.y
        val distance = max(abs(dx), abs(dy))
        val elapsed = change.uptimeMillis - startTime
        val velocity = if (elapsed > 0) distance / elapsed * 1000f else 0f
        val isSwipe =
            (config.swipeTravel > 0 && distance >= config.swipeTravel) ||
                (config.swipeVelocity > 0 && velocity >= config.swipeVelocity)
        if (!isSwipe) return
        swiped = true
        if (abs(dy) > abs(dx) && dy > 0) onSwipeDown()
    }
}

/** Follows pointer [id] until it lifts (true) or the gesture is cancelled (false). */
private suspend fun AwaitPointerEventScope.awaitRelease(
    id: PointerId,
    swipe: SwipeTracker,
): Boolean {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return false
        change.consume()
        if (!change.pressed) return true
        swipe.track(change)
    }
}

private fun vibrate(
    view: View,
    config: KeyGestureConfig,
    longPress: Boolean = false,
) {
    if (config.vibrateOnKeyPress) InputFeedbackManager.keyPressVibrate(view, longPress)
}
