/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.builtin

import androidx.annotation.ColorInt
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt

/**
 * The handful of colour roles the keyboard is painted with, and the one table ([colors]) that
 * turns them into the theme's colour keys.
 *
 * Both the fixed neutral schemes ([NeutralLight], [NeutralDark]) and the wallpaper schemes are
 * built from roles, so every scheme keeps the same hierarchy: letter keys stand off the keyboard
 * surface, function keys sit a tone apart, and the accent is kept for the enter key, toggles that
 * are on, and the focused cell of a long-press keyboard.
 *
 * [fromMaterial] is the wallpaper (Material You) side of the mapping: which Material colour role
 * fills each keyboard role, so both halves of the mapping live in this file.
 *
 * Pressed colours are not a role of their own: they are the matching "on" colour laid over the
 * base at [PRESSED_ALPHA], Material's pressed state layer.
 */
data class KeyboardColorRoles(
    /** Keyboard window, candidate bar and panels. */
    @ColorInt val surface: Int,
    /** Letter keys, space, key preview bubbles, clipboard entries. */
    @ColorInt val key: Int,
    /** Function keys, and the highlighted candidate's block. */
    @ColorInt val functionKey: Int,
    /** The preedit strip above the keyboard. */
    @ColorInt val preedit: Int,
    /** Text and glyphs on [surface] and [key]. */
    @ColorInt val onSurface: Int,
    /** Comments, hints and other secondary text. */
    @ColorInt val onSurfaceVariant: Int,
    /** Text and glyphs on [functionKey]. */
    @ColorInt val onFunctionKey: Int,
    /** Enter key, toggled-on keys, the focused cell of a long-press keyboard. */
    @ColorInt val accent: Int,
    @ColorInt val onAccent: Int,
) {
    /**
     * The colour keys of a scheme. Every key a built-in scheme has is written here, so schemes
     * built from different roles have identical key sets.
     *
     * | Keys | Role |
     * |---|---|
     * | `back_color` `border_color` `keyboard_back_color` `keyboard_background` `root_background` `liquid_keyboard_background` | surface |
     * | `key_back_color` `long_text_back_color` `popup_back_color` `preview_back_color` | key |
     * | `hilited_key_back_color` | key, pressed |
     * | `off_key_back_color` `func_key_back_color` `hilited_candidate_back_color` `hilited_back_color` | functionKey |
     * | `hilited_off_key_back_color` `func_key_hilited_back_color` | functionKey, pressed |
     * | `text_back_color` | preedit |
     * | `key_text_color` `hilited_key_text_color` `candidate_text_color` `text_color` `long_text_color` `popup_text_color` `preview_text_color` | onSurface |
     * | `comment_text_color` `hilited_comment_text_color` `key_symbol_color` `hilited_key_symbol_color` `label_color` | onSurfaceVariant |
     * | `off_key_text_color` `hilited_off_key_text_color` `hilited_candidate_text_color` `hilited_label_color` | onFunctionKey |
     * | `enter_key_action_back_color` `on_key_back_color` `hilited_popup_back_color` | accent |
     * | `hilited_enter_key_action_back_color` `hilited_on_key_back_color` | accent, pressed |
     * | `enter_key_action_text_color` `on_key_text_color` `hilited_on_key_text_color` `hilited_popup_text_color` `hilited_text_color`* | onAccent / accent* |
     * | `candidate_background` `candidate_separator_color` `key_border_color` `shadow_color` | transparent |
     *
     * \* `hilited_text_color` is the selected segment of the preedit, drawn on [preedit]: [accent].
     */
    fun colors(name: String): Map<String, String> {
        val surface = hex(surface)
        val key = hex(key)
        val keyPressed = hex(pressed(this.key, onSurface))
        val functionKey = hex(functionKey)
        val functionKeyPressed = hex(pressed(this.functionKey, onFunctionKey))
        val accent = hex(accent)
        val accentPressed = hex(pressed(this.accent, onAccent))
        val onSurface = hex(onSurface)
        val onSurfaceVariant = hex(onSurfaceVariant)
        val onFunctionKey = hex(onFunctionKey)
        val onAccent = hex(onAccent)
        return sortedMapOf(
            "author" to "builtin",
            "name" to name,
            "back_color" to surface,
            "border_color" to surface,
            "keyboard_back_color" to surface,
            "keyboard_background" to surface,
            "root_background" to surface,
            "liquid_keyboard_background" to surface,
            "key_back_color" to key,
            "long_text_back_color" to key,
            "popup_back_color" to key,
            "preview_back_color" to key,
            "hilited_key_back_color" to keyPressed,
            "off_key_back_color" to functionKey,
            "func_key_back_color" to functionKey,
            "hilited_candidate_back_color" to functionKey,
            "hilited_back_color" to functionKey,
            "hilited_off_key_back_color" to functionKeyPressed,
            "func_key_hilited_back_color" to functionKeyPressed,
            "text_back_color" to hex(preedit),
            "key_text_color" to onSurface,
            "hilited_key_text_color" to onSurface,
            "candidate_text_color" to onSurface,
            "text_color" to onSurface,
            "long_text_color" to onSurface,
            "popup_text_color" to onSurface,
            "preview_text_color" to onSurface,
            "comment_text_color" to onSurfaceVariant,
            "hilited_comment_text_color" to onSurfaceVariant,
            "key_symbol_color" to onSurfaceVariant,
            "hilited_key_symbol_color" to onSurfaceVariant,
            "label_color" to onSurfaceVariant,
            "off_key_text_color" to onFunctionKey,
            "hilited_off_key_text_color" to onFunctionKey,
            "hilited_candidate_text_color" to onFunctionKey,
            "hilited_label_color" to onFunctionKey,
            "enter_key_action_back_color" to accent,
            "on_key_back_color" to accent,
            "hilited_popup_back_color" to accent,
            "hilited_enter_key_action_back_color" to accentPressed,
            "hilited_on_key_back_color" to accentPressed,
            "enter_key_action_text_color" to onAccent,
            "on_key_text_color" to onAccent,
            "hilited_on_key_text_color" to onAccent,
            "hilited_popup_text_color" to onAccent,
            "hilited_text_color" to accent,
            "candidate_background" to TRANSPARENT,
            "candidate_separator_color" to TRANSPARENT,
            "key_border_color" to TRANSPARENT,
            "shadow_color" to TRANSPARENT,
        )
    }

    companion object {
        /** Material 3's pressed state layer. */
        const val PRESSED_ALPHA = 0.12f

        const val TRANSPARENT = "0x00000000"

        /**
         * Light neutral, on shadcn's zinc ramp like the settings app: white letter keys on a pale
         * grey surface, zinc-300 function keys, near-black accent.
         */
        val NeutralLight =
            KeyboardColorRoles(
                surface = 0xFFE8E8EC.toInt(),
                key = 0xFFFFFFFF.toInt(),
                functionKey = 0xFFD4D4D8.toInt(),
                preedit = 0xFFF4F4F5.toInt(),
                onSurface = 0xFF18181B.toInt(),
                onSurfaceVariant = 0xFF71717A.toInt(),
                onFunctionKey = 0xFF18181B.toInt(),
                accent = 0xFF18181B.toInt(),
                onAccent = 0xFFFAFAFA.toInt(),
            )

        /**
         * Dark neutral: zinc-700 letter keys on a zinc-900 surface, function keys halfway between
         * the two (dark enough to read as the lower level, light enough to still read as keys),
         * near-white accent.
         */
        val NeutralDark =
            KeyboardColorRoles(
                surface = 0xFF18181B.toInt(),
                key = 0xFF3F3F46.toInt(),
                functionKey = 0xFF323238.toInt(),
                preedit = 0xFF27272A.toInt(),
                onSurface = 0xFFFAFAFA.toInt(),
                onSurfaceVariant = 0xFFA1A1AA.toInt(),
                onFunctionKey = 0xFFFAFAFA.toInt(),
                accent = 0xFFFAFAFA.toInt(),
                onAccent = 0xFF18181B.toInt(),
            )

        /**
         * Roles from a Material colour scheme, i.e. the wallpaper palette from `dynamicLightColorScheme` /
         * `dynamicDarkColorScheme`. Light and dark pick different surface roles, because Material's
         * surface ramp runs the other way in dark mode: the letter keys must stay the lightest surface
         * in light mode and the brightest in dark mode, with function keys between them and the keyboard.
         *
         * | Keyboard role | Light scheme | Dark scheme |
         * |---|---|---|
         * | surface | surfaceContainerHigh (T92) | surfaceContainerLowest (T4) |
         * | key | surfaceContainerLowest (T100) | surfaceBright (T24) |
         * | functionKey | secondaryContainer (T90, tinted) | surfaceContainerHigh (T17) |
         * | preedit | surfaceContainerLow | surfaceContainerHigh |
         * | onSurface | onSurface | onSurface |
         * | onSurfaceVariant | onSurfaceVariant | onSurfaceVariant |
         * | onFunctionKey | onSecondaryContainer | onSurface |
         * | accent | primary | primary |
         * | onAccent | onPrimary | onPrimary |
         */
        fun fromMaterial(
            scheme: ColorScheme,
            dark: Boolean,
        ): KeyboardColorRoles = if (dark) {
            KeyboardColorRoles(
                // T4 / T17 / T24: the same spacing as the neutral dark scheme, so function keys
                // still read as keys; on surfaceContainer (T12) they vanished into the keyboard
                surface = scheme.surfaceContainerLowest.toArgb(),
                key = scheme.surfaceBright.toArgb(),
                functionKey = scheme.surfaceContainerHigh.toArgb(),
                preedit = scheme.surfaceContainerHigh.toArgb(),
                onSurface = scheme.onSurface.toArgb(),
                onSurfaceVariant = scheme.onSurfaceVariant.toArgb(),
                onFunctionKey = scheme.onSurface.toArgb(),
                accent = scheme.primary.toArgb(),
                onAccent = scheme.onPrimary.toArgb(),
            )
        } else {
            KeyboardColorRoles(
                surface = scheme.surfaceContainerHigh.toArgb(),
                key = scheme.surfaceContainerLowest.toArgb(),
                functionKey = scheme.secondaryContainer.toArgb(),
                preedit = scheme.surfaceContainerLow.toArgb(),
                onSurface = scheme.onSurface.toArgb(),
                onSurfaceVariant = scheme.onSurfaceVariant.toArgb(),
                onFunctionKey = scheme.onSecondaryContainer.toArgb(),
                accent = scheme.primary.toArgb(),
                onAccent = scheme.onPrimary.toArgb(),
            )
        }

        /** [on] at [PRESSED_ALPHA] over [base]; opaque when [base] is. */
        @ColorInt
        fun pressed(
            @ColorInt base: Int,
            @ColorInt on: Int,
        ): Int {
            fun mix(shift: Int) = (((on ushr shift) and 0xFF) * PRESSED_ALPHA + ((base ushr shift) and 0xFF) * (1 - PRESSED_ALPHA)).roundToInt()
            return (base and 0xFF000000.toInt()) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }

        /**
         * `0xRRGGBB` for an opaque colour, so the opacity slider may still apply to it, `0xAARRGGBB`
         * otherwise. See `ColorTint.hasExplicitAlpha`.
         */
        fun hex(
            @ColorInt color: Int,
        ): String = if (color ushr 24 == 0xFF) {
            "0x%06X".format(color and 0xFFFFFF)
        } else {
            "0x%08X".format(color)
        }
    }
}
