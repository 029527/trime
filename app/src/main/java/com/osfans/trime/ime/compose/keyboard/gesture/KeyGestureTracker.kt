/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.keyboard.gesture

import com.osfans.trime.ime.keyboard.KeyBehavior
import kotlin.math.abs
import kotlin.math.floor

/**
 * Turns raw pointers into per-key gestures, with `GestureFrame`'s semantics and event order.
 *
 * Every key keeps the state one `GestureFrame` used to keep, so double taps are remembered per
 * key while pointers come and go. A pointer belongs to the key it went down on until it lifts,
 * wherever it moves. A second pointer on a key that is already held only takes over once the
 * first one lifts, like the extra pointers of a split `MotionEvent` did.
 *
 * Free of Compose and of the Android clock: coordinates are keyboard px, time comes from [clock].
 */
class KeyGestureTracker(
    private val target: KeyGestureTarget,
    private val listener: KeyGestureListener,
    private val config: KeyGestureConfig,
    private val clock: GestureClock,
    /** Haptic feedback; `true` for the long-press effect. Called only where the preferences allow. */
    private val vibrate: (longPress: Boolean) -> Unit,
) {
    private val keys = HashMap<Int, KeyState>()

    /** Key of every pointer down, in the order they went down. */
    private val pointerKeys = LinkedHashMap<Long, Int>()

    val isTracking: Boolean get() = pointerKeys.isNotEmpty()

    /** Returns whether the pointer landed on a key and is now tracked. */
    fun down(
        pointer: Long,
        x: Float,
        y: Float,
    ): Boolean {
        if (pointer in pointerKeys) return true
        val key = target.keyAt(x, y)
        if (key < 0) return false
        val state = keys.getOrPut(key) { KeyState(key) }
        pointerKeys[pointer] = key
        state.pointers.add(pointer)
        if (state.pointers.size == 1) state.down(x, y)
        return true
    }

    fun move(
        pointer: Long,
        x: Float,
        y: Float,
    ) {
        val state = pointerKeys[pointer]?.let { keys[it] } ?: return
        if (state.pointers.firstOrNull() == pointer) state.move(x, y)
    }

    fun up(pointer: Long) {
        val state = pointerKeys.remove(pointer)?.let { keys[it] } ?: return
        state.pointers.remove(pointer)
        if (state.pointers.isEmpty()) state.up()
    }

    /** Cancels every gesture in progress, oldest pointer first. */
    fun cancel() {
        val held = pointerKeys.values.distinct().mapNotNull { keys[it] }
        pointerKeys.clear()
        held.forEach {
            it.pointers.clear()
            it.cancel()
        }
    }

    private inner class KeyState(
        val key: Int,
    ) {
        val pointers = ArrayList<Long>(1)

        private val caps get() = target.capsOf(key)

        private var touchId = 0
        private var startX = 0f
        private var startY = 0f
        private var lastX = 0f
        private var startTime = 0L
        private var originX = 0f
        private var originY = 0f

        private var isLongPressed = false
        private var slideActivated = false
        private var swipeTriggered = false

        private var longPressTimer: GestureTimer? = null
        private var repeatTimer: GestureTimer? = null
        private var repeating = false
        private var doubleTapTimer: GestureTimer? = null

        private var lastTapTime = 0L
        private var lastSwipeBehavior = KeyBehavior.CLICK

        fun down(
            x: Float,
            y: Float,
        ) {
            touchId = (touchId + 1) and 0xFFFF
            val currentTouchId = touchId
            startX = x
            startY = y
            lastX = startX
            startTime = clock.now()
            target.cellOrigin(key).let {
                originX = it.x
                originY = it.y
            }

            isLongPressed = false
            slideActivated = false
            swipeTriggered = false
            lastSwipeBehavior = KeyBehavior.CLICK

            if (config.vibrateOnKeyPress) vibrate(false)
            listener.onPress(key)

            val caps = caps
            if (caps.hasLongPress || caps.repeatable || caps.hasPopup) {
                longPressTimer =
                    clock.schedule(config.longPressTimeout.toLong()) {
                        longPressTimer = null
                        onLongPressTimeout(currentTouchId)
                    }
            }
        }

        fun move(
            x: Float,
            y: Float,
        ) {
            val dx = x - startX
            val dy = y - startY

            listener.onMove(key, x - originX, y - originY, isLongPressed)

            val caps = caps
            val swipeTravel = config.swipeTravel
            if ((caps.slideCursor || caps.slideDelete) && !isLongPressed && swipeTravel > 0) {
                if (!slideActivated && abs(dx) >= swipeTravel) {
                    slideActivated = true
                    lastX = startX
                }
                if (slideActivated) {
                    val step = steps(lastX, x, config.slideStepSize.toFloat())
                    if (step != 0) {
                        listener.onSlide(key, step)
                        lastX = x
                    }
                }
            }

            if (!isLongPressed) {
                val behavior = detectSwipe(dx, dy)
                if (behavior != lastSwipeBehavior) {
                    lastSwipeBehavior = behavior
                    if (behavior != KeyBehavior.CLICK) listener.onSwipe(key, behavior)
                }
            }
        }

        fun up() {
            if (config.vibrateOnKeyRelease) vibrate(false)
            cancelTimers()

            if (slideActivated) {
                listener.onSlide(key, 0)
                listener.onCancel(key)
                return
            }
            if (isLongPressed) {
                listener.onRelease(key, KeyBehavior.LONG_CLICK, true)
                return
            }
            if (swipeTriggered) {
                listener.onRelease(key, lastSwipeBehavior, false)
                return
            }

            val caps = caps
            if (!caps.hasDouble && !caps.hasLazyDouble) {
                listener.onRelease(key, KeyBehavior.CLICK, false)
                return
            }

            val now = clock.now()
            val timeout = config.doubleTapTimeout
            if (now - lastTapTime <= timeout) {
                lastTapTime = 0
                val behavior = if (caps.hasDouble) KeyBehavior.DOUBLE_CLICK else KeyBehavior.LAZY_DOUBLE_CLICK
                listener.onRelease(key, behavior, false)
            } else {
                lastTapTime = now
                if (caps.hasLazyDouble && !caps.hasDouble) {
                    // The click waits until no second tap can follow.
                    doubleTapTimer =
                        clock.schedule(timeout.toLong()) {
                            doubleTapTimer = null
                            if (lastTapTime == now) {
                                lastTapTime = 0
                                listener.onRelease(key, KeyBehavior.CLICK, false)
                            }
                        }
                } else {
                    listener.onRelease(key, KeyBehavior.CLICK, false)
                }
            }
        }

        fun cancel() {
            cancelTimers()
            isLongPressed = false
            slideActivated = false
            swipeTriggered = false
            listener.onCancel(key)
        }

        private fun onLongPressTimeout(currentTouchId: Int) {
            if (touchId != currentTouchId || pointers.isEmpty()) return
            if (swipeTriggered || slideActivated) return
            isLongPressed = true

            if (config.vibrateOnKeyPress) vibrate(true)

            if (caps.repeatable) {
                repeating = true
                repeat()
            } else {
                listener.onLongPress(key)
            }
        }

        private fun repeat() {
            if (config.vibrateOnKeyRepeat) vibrate(false)
            listener.onRelease(key, KeyBehavior.CLICK, true)
            // The release may have cancelled us, e.g. by switching the keyboard away.
            if (repeating) repeatTimer = clock.schedule(config.repeatInterval.toLong()) { repeat() }
        }

        /** Cancels pending timers. Stopping a repeat also cancels the key, as `GestureFrame`'s `finally` did. */
        private fun cancelTimers() {
            longPressTimer?.cancel()
            longPressTimer = null
            if (repeating) {
                repeating = false
                repeatTimer?.cancel()
                repeatTimer = null
                listener.onCancel(key)
            }
            doubleTapTimer?.cancel()
            doubleTapTimer = null
        }

        private fun detectSwipe(
            dx: Float,
            dy: Float,
        ): KeyBehavior {
            val absDx = abs(dx)
            val absDy = abs(dy)
            val distance = if (absDx > absDy) absDx else absDy
            val elapsed = clock.now() - startTime
            val velocity = if (elapsed > 0) distance / elapsed * 1000f else 0f

            val swipeTravel = config.swipeTravel
            val swipeVelocity = config.swipeVelocity
            val isSwipe =
                (swipeTravel > 0 && distance >= swipeTravel) ||
                    (swipeVelocity > 0 && velocity >= swipeVelocity)
            swipeTriggered = isSwipe

            if (!isSwipe) return KeyBehavior.CLICK
            return if (absDx > absDy) {
                if (dx > 0) KeyBehavior.SWIPE_RIGHT else KeyBehavior.SWIPE_LEFT
            } else {
                if (dy > 0) KeyBehavior.SWIPE_DOWN else KeyBehavior.SWIPE_UP
            }
        }

        private fun steps(
            start: Float,
            end: Float,
            step: Float,
        ): Int = (if (start < end) 1 else -1) * floor(abs(end - start) / step).toInt()
    }
}
