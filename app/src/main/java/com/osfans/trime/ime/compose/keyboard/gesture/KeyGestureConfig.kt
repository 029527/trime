/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.keyboard.gesture

import com.osfans.trime.data.prefs.AppPrefs

/**
 * Thresholds and feedback switches of [KeyGestureTracker], read on every use so preference
 * changes apply without rebuilding the keyboard.
 *
 * Distances are compared with raw px, as `GestureFrame` did, even though the preferences are
 * labelled in dp.
 */
interface KeyGestureConfig {
    val swipeTravel: Int
    val swipeVelocity: Int
    val longPressTimeout: Int
    val repeatInterval: Int
    val doubleTapTimeout: Int
    val slideStepSize: Int
    val vibrateOnKeyPress: Boolean
    val vibrateOnKeyRelease: Boolean
    val vibrateOnKeyRepeat: Boolean
}

/** [KeyGestureConfig] backed by the keyboard preferences. */
object PrefsKeyGestureConfig : KeyGestureConfig {
    private val prefs get() = AppPrefs.defaultInstance().keyboard

    override val swipeTravel by prefs.swipeTravel
    override val swipeVelocity by prefs.swipeVelocity
    override val longPressTimeout by prefs.longPressTimeout
    override val repeatInterval by prefs.repeatInterval
    override val doubleTapTimeout by prefs.doubleTapTimeout
    override val slideStepSize by prefs.slideStepSize
    override val vibrateOnKeyPress by prefs.vibrateOnKeyPress
    override val vibrateOnKeyRelease by prefs.vibrateOnKeyRelease
    override val vibrateOnKeyRepeat by prefs.vibrateOnKeyRepeat
}
