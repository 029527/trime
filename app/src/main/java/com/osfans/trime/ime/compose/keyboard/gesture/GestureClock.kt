/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.keyboard.gesture

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** A pending [GestureClock.schedule] call. */
fun interface GestureTimer {
    fun cancel()
}

/** Time source and timers of [KeyGestureTracker], injectable so tests can run on virtual time. */
interface GestureClock {
    /** Milliseconds on a monotonic clock. */
    fun now(): Long

    /** Runs [action] on the gesture thread after [delayMillis], unless cancelled first. */
    fun schedule(
        delayMillis: Long,
        action: () -> Unit,
    ): GestureTimer
}

/** [GestureClock] on the main looper, where Compose delivers pointer events. */
object MainGestureClock : GestureClock {
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    override fun now(): Long = SystemClock.elapsedRealtime()

    override fun schedule(
        delayMillis: Long,
        action: () -> Unit,
    ): GestureTimer {
        val runnable = Runnable { action() }
        handler.postDelayed(runnable, delayMillis)
        return GestureTimer { handler.removeCallbacks(runnable) }
    }
}
