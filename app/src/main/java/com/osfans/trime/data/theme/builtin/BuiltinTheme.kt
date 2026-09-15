/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.builtin

import com.osfans.trime.data.theme.Theme

/**
 * The only theme the app ships: the iOS look, formerly `ios.trime.yaml` in the config repo.
 * Edit the `Builtin*` objects next to this file to change it.
 */
object BuiltinTheme {
    val theme =
        Theme(
            name = "iOS",
            generalStyle = BuiltinStyle.style,
            preedit = BuiltinStyle.preedit,
            window = BuiltinStyle.window,
            liquidKeyboard = BuiltinLiquid.liquidKeyboard,
            presetKeys = BuiltinKeys.presetKeys,
            presetKeyboards = BuiltinKeyboards.keyboards,
            colorSchemes = BuiltinColors.schemes,
            fallbackColors = BuiltinColors.fallbackColors,
        )
}
