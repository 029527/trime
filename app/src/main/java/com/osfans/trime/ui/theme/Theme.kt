/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.osfans.trime.data.theme.ThemeManager

/**
 * The one and only theme wrapper for Trime's *app* UI (the settings activity and
 * friends). It is **not** used by the keyboard itself, which keeps its own
 * yaml-driven theming in `data/theme/`.
 *
 * The palette is the shadcn/ui-style neutral one in [TrimeLightColorScheme] /
 * [TrimeDarkColorScheme], or the wallpaper's Material You scheme when the user turned on
 * "follow wallpaper colours" (`ThemePrefs.followWallpaper`), the same switch the keyboard uses.
 *
 * Light/dark follows the activity configuration, which `MainActivity` drives from
 * `ThemePrefs.appNightMode` through `AppCompatDelegate.setDefaultNightMode`: the keyboard's
 * light / dark preference, or the system's while following the wallpaper.
 */
@Composable
fun TrimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = ThemeManager.prefs.isFollowingWallpaper,
    applySystemBarAppearance: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = rememberTrimeColorScheme(context, darkTheme, dynamicColor)

    val view = LocalView.current
    if (applySystemBarAppearance && !view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TrimeTypography,
        shapes = TrimeShapes,
        content = content,
    )
}

@Composable
private fun rememberTrimeColorScheme(
    context: Context,
    darkTheme: Boolean,
    dynamicColor: Boolean,
): ColorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    darkTheme -> TrimeDarkColorScheme
    else -> TrimeLightColorScheme
}

internal fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
