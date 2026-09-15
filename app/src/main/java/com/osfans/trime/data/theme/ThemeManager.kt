/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.content.res.Configuration
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.builtin.BuiltinTheme
import com.osfans.trime.ime.symbol.LiquidData

/**
 * Holds the one theme the app has. It is compiled in ([BuiltinTheme]); nothing is read from
 * `*.trime.yaml` any more, so there is no theme to pick and no theme change to listen for.
 * Colours still change (day / night, tint) and go through [ColorManager].
 */
object ThemeManager {
    val prefs = AppPrefs.defaultInstance().registerProvider(::ThemePrefs)

    private var prepared = false

    /** Hands the theme to the managers that derive state from it, once, on first use. */
    private fun ensurePrepared() {
        if (prepared) return
        prepared = true
        val theme = BuiltinTheme.theme
        KeyActionManager.resetCache()
        ColorManager.switchTheme(theme)
        LiquidData.init(theme)
    }

    val activeTheme: Theme
        get() {
            ensurePrepared()
            return BuiltinTheme.theme
        }

    fun init(configuration: Configuration) {
        ensurePrepared()
        ColorManager.init(configuration)
    }
}
