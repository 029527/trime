/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.content.SharedPreferences
import androidx.core.content.edit
import com.osfans.trime.data.theme.ThemePrefs.DayNightMode
import com.osfans.trime.data.theme.builtin.BuiltinColors

/**
 * Turns the preferences of the yaml-theme era into [ThemePrefs.dayNightMode]:
 *
 * - `follow_system_day_night = true` → [DayNightMode.FOLLOW_SYSTEM];
 * - otherwise a stored `normal_mode_color` → [DayNightMode.DARK] or [DayNightMode.LIGHT],
 *   by whether that scheme is a dark one (an unknown id used to fall back to the light
 *   `default` scheme, so it counts as light);
 * - nothing stored → [DayNightMode.FOLLOW_SYSTEM], the default, so nothing is written.
 *
 * The old keys (and the stale `selected_theme`) are removed afterwards, which also makes
 * this a no-op on every later start.
 */
object DayNightMigration {
    const val FOLLOW_SYSTEM_DAY_NIGHT = "follow_system_day_night"
    const val NORMAL_MODE_COLOR = "normal_mode_color"
    const val SELECTED_THEME = "selected_theme"

    private val legacyKeys = listOf(FOLLOW_SYSTEM_DAY_NIGHT, NORMAL_MODE_COLOR, SELECTED_THEME)

    fun resolve(
        followSystem: Boolean?,
        normalModeColor: String?,
        isDarkScheme: (String) -> Boolean = ::isBuiltinDarkScheme,
    ): DayNightMode = when {
        followSystem == true -> DayNightMode.FOLLOW_SYSTEM
        normalModeColor != null -> if (isDarkScheme(normalModeColor)) DayNightMode.DARK else DayNightMode.LIGHT
        else -> DayNightMode.FOLLOW_SYSTEM
    }

    fun migrate(
        prefs: SharedPreferences,
        isDarkScheme: (String) -> Boolean = ::isBuiltinDarkScheme,
    ) {
        if (legacyKeys.none(prefs::contains)) return
        val followSystem = if (prefs.contains(FOLLOW_SYSTEM_DAY_NIGHT)) prefs.getBoolean(FOLLOW_SYSTEM_DAY_NIGHT, false) else null
        val normalModeColor = if (prefs.contains(NORMAL_MODE_COLOR)) prefs.getString(NORMAL_MODE_COLOR, null) else null
        val mode = resolve(followSystem, normalModeColor, isDarkScheme)
        prefs.edit(commit = true) {
            if (!prefs.contains(ThemePrefs.DAY_NIGHT_MODE) && mode != DayNightMode.FOLLOW_SYSTEM) {
                putString(ThemePrefs.DAY_NIGHT_MODE, mode.name)
            }
            legacyKeys.forEach(::remove)
        }
    }

    /** Whether the built-in scheme [id] (a current or [legacy][BuiltinColors.legacyIds] id) has a dark background; unknown ids are light. */
    fun isBuiltinDarkScheme(id: String): Boolean {
        val current = BuiltinColors.legacyIds[id] ?: id
        val colors = BuiltinColors.schemes.find { it.id == current }?.colors ?: return false
        for (key in arrayOf("back_color", "keyboard_back_color", "key_back_color")) {
            val color = parseHexColor(colors[key] ?: continue) ?: continue
            return ColorTint.isDarkColor(color)
        }
        return false
    }

    private fun parseHexColor(raw: String): Int? {
        val hex = raw.removePrefix("0x").removePrefix("#")
        val value = hex.toLongOrNull(16) ?: return null
        return when (hex.length) {
            6 -> (0xFF000000L or value).toInt()
            8 -> value.toInt()
            else -> null
        }
    }
}
