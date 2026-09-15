/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.builtin

import com.osfans.trime.data.theme.model.ColorScheme

/**
 * Colour schemes of the built-in theme, both generated from [KeyboardColorRoles]. Values are
 * `0xRRGGBB` / `0xAARRGGBB`; keys a scheme leaves out resolve through [fallbackColors] and then
 * the fallback chain in `ColorManager`.
 */
object BuiltinColors {
    /** Scheme for light mode; which one is active follows `ThemePrefs.dayNightMode`. */
    const val LIGHT_SCHEME = "light"

    /** Scheme for dark mode. */
    const val DARK_SCHEME = "dark"

    /** Ids the schemes had before, still understood wherever a stored scheme id is read. */
    val legacyIds = mapOf("ios_light" to LIGHT_SCHEME, "ios_dark" to DARK_SCHEME)

    val schemes =
        listOf(
            ColorScheme(id = LIGHT_SCHEME, colors = KeyboardColorRoles.NeutralLight.colors("浅色")),
            ColorScheme(id = DARK_SCHEME, colors = KeyboardColorRoles.NeutralDark.colors("深色")),
        )

    val fallbackColors =
        mapOf(
            "candidate_text_color" to "text_color",
        )
}
