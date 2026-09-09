/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Trime's brand palette, used when Material You dynamic color is unavailable
 * (Android 11 and below, or when the user turns dynamic color off).
 *
 * The seed is Rime's accent cyan-blue (`#009BD1`, see `res/values/colors.xml`),
 * expanded into a Material 3 tonal palette. Keep it cool and low-chroma: this is a
 * keyboard app, the settings UI should stay out of the way.
 */
private val BrandPrimaryLight = Color(0xFF00658F)
private val BrandPrimaryDark = Color(0xFF87CFFF)

internal val TrimeLightColorScheme = lightColorScheme(
    primary = BrandPrimaryLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC7E7FF),
    onPrimaryContainer = Color(0xFF001E2F),
    inversePrimary = BrandPrimaryDark,
    secondary = Color(0xFF4E616C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1E6F3),
    onSecondaryContainer = Color(0xFF091E28),
    tertiary = Color(0xFF605A7C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE6DEFF),
    onTertiaryContainer = Color(0xFF1C1735),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FAFE),
    onBackground = Color(0xFF171C1F),
    surface = Color(0xFFF6FAFE),
    onSurface = Color(0xFF171C1F),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484D),
    surfaceTint = BrandPrimaryLight,
    inverseSurface = Color(0xFF2C3134),
    inverseOnSurface = Color(0xFFEDF1F5),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF6FAFE),
    surfaceDim = Color(0xFFD6DBDF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F4F8),
    surfaceContainer = Color(0xFFEAEEF2),
    surfaceContainerHigh = Color(0xFFE4E9ED),
    surfaceContainerHighest = Color(0xFFDEE3E7),
)

internal val TrimeDarkColorScheme = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = Color(0xFF00344C),
    primaryContainer = Color(0xFF004C6D),
    onPrimaryContainer = Color(0xFFC7E7FF),
    inversePrimary = BrandPrimaryLight,
    secondary = Color(0xFFB5CAD6),
    onSecondary = Color(0xFF20333D),
    secondaryContainer = Color(0xFF374A54),
    onSecondaryContainer = Color(0xFFD1E6F3),
    tertiary = Color(0xFFC9C1E9),
    onTertiary = Color(0xFF322C4C),
    tertiaryContainer = Color(0xFF484264),
    onTertiaryContainer = Color(0xFFE6DEFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1417),
    onBackground = Color(0xFFDEE3E7),
    surface = Color(0xFF0F1417),
    onSurface = Color(0xFFDEE3E7),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    surfaceTint = BrandPrimaryDark,
    inverseSurface = Color(0xFFDEE3E7),
    inverseOnSurface = Color(0xFF2C3134),
    outline = Color(0xFF8B9297),
    outlineVariant = Color(0xFF41484D),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF353A3D),
    surfaceDim = Color(0xFF0F1417),
    surfaceContainerLowest = Color(0xFF0A0F12),
    surfaceContainerLow = Color(0xFF171C1F),
    surfaceContainer = Color(0xFF1B2023),
    surfaceContainerHigh = Color(0xFF262B2E),
    surfaceContainerHighest = Color(0xFF313539),
)
