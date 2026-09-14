/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager

/**
 * The variable half of the keyboard's look, taken from the active colour scheme.
 *
 * Goes through [ColorManager], so the in-app tint (warmth / brightness / opacity) is
 * already applied, and a tint change arrives as a recomposition.
 */
@Immutable
data class ImeColors(
    val candidateText: Color,
    val candidateComment: Color,
    val highlightedCandidateText: Color,
    val highlightedCandidateComment: Color,
    val highlightedCandidateBack: Color,
    val candidateSeparator: Color,
    val preeditText: Color,
    val preeditHighlightedText: Color,
    val preeditBack: Color,
) {
    companion object {
        fun fromColorManager() = ImeColors(
            candidateText = color("candidate_text_color"),
            candidateComment = color("comment_text_color"),
            highlightedCandidateText = color("hilited_candidate_text_color"),
            highlightedCandidateComment = color("hilited_comment_text_color"),
            highlightedCandidateBack = color("hilited_candidate_back_color"),
            candidateSeparator = color("candidate_separator_color"),
            preeditText = color("text_color"),
            preeditHighlightedText = color("hilited_text_color"),
            preeditBack = color("text_back_color"),
        )

        private fun color(key: String) = Color(ColorManager.getColor(key))
    }
}

/** Fonts stay theme-driven: the user's `fonts/` directory decides them. */
@Immutable
data class ImeFonts(
    val candidate: FontFamily,
    val comment: FontFamily,
    val preedit: FontFamily,
) {
    companion object {
        fun fromFontManager() = ImeFonts(
            candidate = FontFamily(FontManager.getTypeface("candidate_font")),
            comment = FontFamily(FontManager.getTypeface("comment_font")),
            preedit = FontFamily(FontManager.getTypeface("text_font")),
        )
    }
}

val LocalImeColors = staticCompositionLocalOf<ImeColors> { error("ImeColors not provided, wrap the content in ImeTheme") }
val LocalImeFonts = staticCompositionLocalOf<ImeFonts> { error("ImeFonts not provided, wrap the content in ImeTheme") }

/**
 * Root of every Compose surface inside the keyboard window. Not [com.osfans.trime.ui.theme.TrimeTheme]:
 * that one is the settings app's Material palette and has nothing to do with the keyboard.
 */
@Composable
fun ImeTheme(
    tokens: ImeTokens = ImeTokens.Portrait,
    content: @Composable () -> Unit,
) {
    var colors by remember { mutableStateOf(ImeColors.fromColorManager()) }
    DisposableEffect(Unit) {
        val listener = ColorManager.OnColorChangeListener { colors = ImeColors.fromColorManager() }
        val tintListener = ColorManager.OnTintChangeListener { colors = ImeColors.fromColorManager() }
        ColorManager.addOnChangedListener(listener)
        ColorManager.addOnTintChangedListener(tintListener)
        onDispose {
            ColorManager.removeOnChangedListener(listener)
            ColorManager.removeOnTintChangedListener(tintListener)
        }
    }
    val fonts = remember { ImeFonts.fromFontManager() }
    CompositionLocalProvider(
        LocalImeColors provides colors,
        LocalImeFonts provides fonts,
        LocalImeTokens provides tokens,
        content = content,
    )
}
