/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Trime's app palette, modelled on shadcn/ui's default (zinc) theme.
 *
 * The idea there is that a settings surface carries **no brand colour at all**: paper
 * white or near-black ground, a neutral grey ramp for everything structural, hairline
 * borders instead of tinted containers, and the accent is simply the inverse of the
 * background — near-black controls on white, near-white controls on black. The only
 * saturated colour in the whole set is the destructive red.
 *
 * That is also why Material You dynamic colour is off by default (see [TrimeTheme]):
 * wallpaper-derived purples and blues are exactly what this palette exists to avoid.
 *
 * Values are shadcn's zinc scale:
 * 50 `#FAFAFA` · 100 `#F4F4F5` · 200 `#E4E4E7` · 300 `#D4D4D8` · 400 `#A1A1AA` ·
 * 500 `#71717A` · 700 `#3F3F46` · 800 `#27272A` · 900 `#18181B` · 950 `#09090B`
 */

private val Zinc50 = Color(0xFFFAFAFA)
private val Zinc100 = Color(0xFFF4F4F5)
private val Zinc200 = Color(0xFFE4E4E7)
private val Zinc300 = Color(0xFFD4D4D8)
private val Zinc400 = Color(0xFFA1A1AA)
private val Zinc500 = Color(0xFF71717A)
private val Zinc700 = Color(0xFF3F3F46)
private val Zinc800 = Color(0xFF27272A)
private val Zinc900 = Color(0xFF18181B)
private val Zinc950 = Color(0xFF09090B)
private val White = Color(0xFFFFFFFF)

// Two steps between 950 and 900, for the "raised" levels the dark scheme needs.
private val Zinc925 = Color(0xFF131316)
private val Zinc850 = Color(0xFF1F1F23)

private val DestructiveLight = Color(0xFFEF4444) // red-500
private val DestructiveDark = Color(0xFFF87171) // red-400, readable on near-black

internal val TrimeLightColorScheme = lightColorScheme(
    // primary == foreground: shadcn's default button is near-black with white text
    primary = Zinc900,
    onPrimary = Zinc50,
    // "Container" spots (FAB, icon tiles) take the muted grey instead, so nothing
    // turns into a big black blob and dark glyphs drawn on them stay readable.
    primaryContainer = Zinc100,
    onPrimaryContainer = Zinc900,
    inversePrimary = Zinc50,
    secondary = Zinc700,
    onSecondary = Zinc50,
    // secondaryContainer is what the contextual (multi select) top bar wears.
    secondaryContainer = Zinc100,
    onSecondaryContainer = Zinc900,
    tertiary = Zinc700,
    onTertiary = Zinc50,
    tertiaryContainer = Zinc100,
    onTertiaryContainer = Zinc900,
    background = White,
    onBackground = Zinc950,
    surface = White,
    onSurface = Zinc950,
    surfaceVariant = Zinc100,
    // muted-foreground: every piece of supporting text in the app
    onSurfaceVariant = Zinc500,
    // Elevation tint equal to the surface itself, i.e. no coloured wash on raised things.
    surfaceTint = White,
    inverseSurface = Zinc900,
    inverseOnSurface = Zinc50,
    error = DestructiveLight,
    onError = Zinc50,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    // border / input
    outline = Zinc200,
    outlineVariant = Zinc100,
    scrim = Color(0xFF000000),
    surfaceBright = White,
    surfaceContainerLowest = White,
    surfaceContainerLow = Zinc50,
    surfaceContainer = Zinc100,
    surfaceContainerHigh = Color(0xFFEFEFF1),
    surfaceContainerHighest = Zinc200,
    surfaceDim = Zinc100,
)

internal val TrimeDarkColorScheme = darkColorScheme(
    // Inverted: near-white controls on a near-black ground
    primary = Zinc50,
    onPrimary = Zinc900,
    primaryContainer = Zinc800,
    onPrimaryContainer = Zinc50,
    inversePrimary = Zinc900,
    secondary = Zinc300,
    onSecondary = Zinc900,
    secondaryContainer = Zinc800,
    onSecondaryContainer = Zinc50,
    tertiary = Zinc300,
    onTertiary = Zinc900,
    tertiaryContainer = Zinc800,
    onTertiaryContainer = Zinc50,
    background = Zinc950,
    onBackground = Zinc50,
    surface = Zinc950,
    onSurface = Zinc50,
    surfaceVariant = Zinc800,
    onSurfaceVariant = Zinc400,
    surfaceTint = Zinc950,
    inverseSurface = Zinc50,
    inverseOnSurface = Zinc900,
    error = DestructiveDark,
    onError = Zinc900,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
    outline = Zinc800,
    outlineVariant = Zinc850,
    scrim = Color(0xFF000000),
    surfaceBright = Zinc800,
    surfaceContainerLowest = Zinc950,
    surfaceContainerLow = Zinc925,
    surfaceContainer = Zinc900,
    surfaceContainerHigh = Zinc850,
    surfaceContainerHighest = Zinc800,
    surfaceDim = Zinc950,
)
