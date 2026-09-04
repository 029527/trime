/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.content.Context
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.InlinePreeditMode
import com.osfans.trime.util.isLandscape

object KeyboardPrefs {
    private val prefs = AppPrefs.defaultInstance()

    private const val WIDE_SCREEN_WIDTH_DP = 600

    fun Context.isLandscapeMode(): Boolean = when (prefs.keyboard.landscapeMode.getValue()) {
        AppPrefs.Keyboard.LandscapeMode.WIDE -> resources.configuration.isLandscape() || isWideScreen()
        AppPrefs.Keyboard.LandscapeMode.LANDSCAPE -> resources.configuration.isLandscape()
        AppPrefs.Keyboard.LandscapeMode.ALWAYS -> true
        else -> false
    }

    /** The floating window is landscape only; this is independent of the landscape-mode setting. */
    fun Context.isFloatingKeyboard(): Boolean = prefs.keyboard.landscapeFloating.getValue() && resources.configuration.isLandscape()

    /** Height of one candidate item (dp): the landscape value of the theme when set. */
    fun Context.candidateViewHeight(theme: Theme): Int {
        val land = theme.generalStyle.candidateViewHeightLand
        return if (isLandscapeMode() && land > 0) land else theme.generalStyle.candidateViewHeight
    }

    /** Space above the candidates in the landscape bar for the small preedit line (dp). */
    const val LANDSCAPE_PREEDIT_LINE_DP = 14

    /**
     * Height of the candidate bar (dp). In landscape the theme's land value is the candidate
     * height and a small line above it holds the preedit of a floating keyboard.
     */
    fun Context.inputBarHeight(theme: Theme): Int {
        val land = theme.generalStyle.candidateViewHeightLand
        if (!isLandscapeMode() || land <= 0) return theme.generalStyle.run { candidateViewHeight + commentHeight }
        // the preedit line is only needed when the preedit is not shown inline in the app
        val inlinePreedit = prefs.general.inlinePreeditMode.getValue() != InlinePreeditMode.DISABLE
        return if (inlinePreedit) land else land + LANDSCAPE_PREEDIT_LINE_DP
    }

    /** Scale applied to keyboard height and key text while floating (1 otherwise). */
    fun Context.floatingScale(): Float = if (isFloatingKeyboard()) prefs.keyboard.floatingScale() else 1f

    private fun Context.isWideScreen(): Boolean {
        val metrics = resources.displayMetrics
        return metrics.widthPixels / metrics.density > WIDE_SCREEN_WIDTH_DP
    }
}
