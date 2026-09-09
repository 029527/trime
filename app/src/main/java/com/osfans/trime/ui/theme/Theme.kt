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

/**
 * The one and only theme wrapper for Trime's *app* UI (the settings activity and
 * friends). It is **not** used by the keyboard itself, which keeps its own
 * yaml-driven theming in `data/theme/`.
 *
 * The palette is the shadcn/ui-style neutral one in [TrimeLightColorScheme] /
 * [TrimeDarkColorScheme]. Material You dynamic colour is deliberately **off**: a
 * wallpaper-derived scheme drags purples and blues into a UI whose whole point is to
 * be colourless. Pass `dynamicColor = true` to opt a screen back into it.
 *
 * Light/dark follows the activity configuration, which `MainActivity` already drives
 * from the `uiMode` preference through `AppCompatDelegate.setDefaultNightMode`, so
 * the AUTO / LIGHT / DARK preference keeps working without extra plumbing here.
 */
@Composable
fun TrimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
