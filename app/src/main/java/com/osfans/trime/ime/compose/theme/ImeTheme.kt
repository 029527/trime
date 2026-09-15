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
 *
 * Panels (symbols, clipboard, switches, toolbar, popups) take their colours from here too, so
 * they recolour with the tint like the keys do. Keys that a theme may set to an image instead of
 * a colour fall back to a plain colour here; draw the image yourself if you need it.
 */
@Immutable
data class ImeColors(
    // candidate bar
    val candidateText: Color,
    val candidateComment: Color,
    val highlightedCandidateText: Color,
    val highlightedCandidateComment: Color,
    val highlightedCandidateBack: Color,
    val candidateSeparator: Color,
    // preedit
    val preeditText: Color,
    val preeditHighlightedText: Color,
    val preeditBack: Color,
    // keyboard and keys
    val keyboardBack: Color,
    val keyBack: Color,
    val keyText: Color,
    val keySymbol: Color,
    val highlightedKeyBack: Color,
    val highlightedKeyText: Color,
    /** Function keys (shift, backspace, 123...), `off_key_*` in the theme. */
    val functionKeyBack: Color,
    val functionKeyText: Color,
    /** Toggled-on keys (shift lock), `on_key_*` in the theme. */
    val onKeyBack: Color,
    val onKeyText: Color,
    /** The one accent: the return key when the editor asks to go / search / send, and panel toggles that are on. */
    val accentBack: Color,
    val accentText: Color,
    val border: Color,
    // key preview bubble and long-press popup keyboard
    val popupBack: Color,
    val popupText: Color,
    val highlightedPopupBack: Color,
    val highlightedPopupText: Color,
    // panels
    /** Background of full-keyboard panels: symbols, clipboard, switches. */
    val panelBack: Color,
    /** Cells holding long text, e.g. clipboard entries and phrases. */
    val longTextBack: Color,
    val longText: Color,
    /** The bar above the keyboard when it is not showing candidates. */
    val barBack: Color,
) {
    companion object {
        fun fromColorManager(): ImeColors {
            val keyboardBack = color("keyboard_back_color")
            return ImeColors(
                candidateText = color("candidate_text_color"),
                candidateComment = color("comment_text_color"),
                highlightedCandidateText = color("hilited_candidate_text_color"),
                highlightedCandidateComment = color("hilited_comment_text_color"),
                highlightedCandidateBack = color("hilited_candidate_back_color"),
                candidateSeparator = color("candidate_separator_color"),
                preeditText = color("text_color"),
                preeditHighlightedText = color("hilited_text_color"),
                preeditBack = color("text_back_color"),
                keyboardBack = keyboardBack,
                keyBack = color("key_back_color"),
                keyText = color("key_text_color"),
                keySymbol = color("key_symbol_color"),
                highlightedKeyBack = color("hilited_key_back_color"),
                highlightedKeyText = color("hilited_key_text_color"),
                functionKeyBack = color("off_key_back_color"),
                functionKeyText = color("off_key_text_color"),
                onKeyBack = color("on_key_back_color"),
                onKeyText = color("on_key_text_color"),
                accentBack = color("enter_key_action_back_color"),
                accentText = color("enter_key_action_text_color"),
                border = color("border_color"),
                popupBack = color("popup_back_color"),
                popupText = color("popup_text_color"),
                highlightedPopupBack = color("hilited_popup_back_color"),
                highlightedPopupText = color("hilited_popup_text_color"),
                panelBack = colorOr("liquid_keyboard_background", keyboardBack),
                longTextBack = color("long_text_back_color"),
                longText = color("long_text_color"),
                barBack = colorOr("candidate_background", Color.Transparent),
            )
        }

        private fun color(key: String) = Color(ColorManager.getColor(key))

        /** For keys a theme may point at an image, which has no single colour. */
        private fun colorOr(
            key: String,
            fallback: Color,
        ) = runCatching { color(key) }.getOrDefault(fallback)
    }
}

/** Fonts stay theme-driven: the user's `fonts/` directory decides them. */
@Immutable
data class ImeFonts(
    val candidate: FontFamily,
    val comment: FontFamily,
    val preedit: FontFamily,
    val key: FontFamily,
    val symbol: FontFamily,
    val popup: FontFamily,
) {
    companion object {
        fun fromFontManager() = ImeFonts(
            candidate = FontFamily(FontManager.getTypeface("candidate_font")),
            comment = FontFamily(FontManager.getTypeface("comment_font")),
            preedit = FontFamily(FontManager.getTypeface("text_font")),
            key = FontFamily(FontManager.getTypeface("key_font")),
            symbol = FontFamily(FontManager.getTypeface("symbol_font")),
            popup = FontFamily(FontManager.getTypeface("popup_font")),
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
