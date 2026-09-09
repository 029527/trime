/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The system font (Roboto / the OEM's replacement) is deliberately kept: a settings
 * UI should look like the rest of the device. Only the display/headline sizes used by
 * the large top app bar are nudged, so a long Chinese page title still fits on one line.
 */
private val Default = Typography()

internal val TrimeTypography = Typography(
    displayLarge = Default.displayLarge,
    displayMedium = Default.displayMedium,
    displaySmall = Default.displaySmall,
    headlineLarge = Default.headlineLarge,
    headlineMedium = Default.headlineMedium.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = Default.headlineSmall,
    titleLarge = Default.titleLarge,
    titleMedium = Default.titleMedium,
    titleSmall = Default.titleSmall,
    bodyLarge = Default.bodyLarge,
    bodyMedium = Default.bodyMedium,
    bodySmall = Default.bodySmall,
    labelLarge = Default.labelLarge,
    labelMedium = Default.labelMedium,
    labelSmall = Default.labelSmall,
)

/** Style used for the value bubble next to a slider. */
internal val SliderValueTextStyle: TextStyle = Default.labelLarge
