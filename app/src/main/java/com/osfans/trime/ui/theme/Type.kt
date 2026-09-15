/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.osfans.trime.data.theme.SourceHanSans

/**
 * Every Material 3 text style on the app font ([SourceHanSans]), with a weight scale that is a
 * little heavier than Material's Roboto defaults: the CJK strokes of a 400 read thinner than
 * Latin ones at the same size.
 *
 * | Role | Styles | Weight |
 * |---|---|---|
 * | Page titles, top app bar | display*, headline* | 600 |
 * | Group titles, dialog titles | title* | 600 |
 * | List item primary text | bodyLarge | 500 |
 * | Supporting text, summaries | bodyMedium, bodySmall | 400 |
 * | Buttons, section headers | labelLarge | 600 |
 * | Chips, small labels | labelMedium, labelSmall | 500 |
 *
 * Sizes and line heights stay Material's, except headlineMedium (the large top app bar), which is
 * nudged down so a long Chinese page title still fits on one line.
 */
private val Default = Typography()

private val Title = FontWeight(600)
private val Primary = FontWeight(500)
private val Supporting = FontWeight(400)

private fun TextStyle.on(weight: FontWeight) = copy(fontFamily = SourceHanSans.family, fontWeight = weight)

internal val TrimeTypography = Typography(
    displayLarge = Default.displayLarge.on(Title),
    displayMedium = Default.displayMedium.on(Title),
    displaySmall = Default.displaySmall.on(Title),
    headlineLarge = Default.headlineLarge.on(Title),
    headlineMedium = Default.headlineMedium.copy(fontSize = 26.sp, lineHeight = 34.sp).on(Title),
    headlineSmall = Default.headlineSmall.on(Title),
    titleLarge = Default.titleLarge.on(Title),
    titleMedium = Default.titleMedium.on(Title),
    titleSmall = Default.titleSmall.on(Title),
    bodyLarge = Default.bodyLarge.on(Primary),
    bodyMedium = Default.bodyMedium.on(Supporting),
    bodySmall = Default.bodySmall.on(Supporting),
    labelLarge = Default.labelLarge.on(Title),
    labelMedium = Default.labelMedium.on(Primary),
    labelSmall = Default.labelSmall.on(Primary),
)

/** Style used for the value bubble next to a slider. */
internal val SliderValueTextStyle: TextStyle get() = TrimeTypography.labelLarge
