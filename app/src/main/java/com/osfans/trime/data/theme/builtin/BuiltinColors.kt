/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.builtin

import com.osfans.trime.data.theme.model.ColorScheme

/**
 * Colour schemes of the built-in theme. Values are `0xRRGGBB` / `0xAARRGGBB` or the name of
 * another colour key; keys a scheme leaves out resolve through [fallbackColors] and then
 * the fallback chain in `ColorManager`.
 */
object BuiltinColors {
    /** Scheme for light mode; which one is active follows `ThemePrefs.dayNightMode`. */
    const val LIGHT_SCHEME = "ios_light"

    /** Scheme for dark mode. */
    const val DARK_SCHEME = "ios_dark"

    val schemes =
        listOf(
            ColorScheme(
                id = "ios_dark",
                colors =
                mapOf(
                    "author" to "generated",
                    "back_color" to "0x2A2A2C",
                    "border_color" to "0x2A2A2C",
                    "candidate_background" to "0x00000000",
                    "candidate_separator_color" to "0x002A2A2C",
                    "candidate_text_color" to "0xFFFFFF",
                    "comment_text_color" to "0xAEAEB2",
                    "enter_key_action_back_color" to "0x0A84FF",
                    "enter_key_action_text_color" to "0xFFFFFF",
                    "func_key_back_color" to "0x464649",
                    "func_key_hilited_back_color" to "0x6B6B6E",
                    "hilited_back_color" to "0x636366",
                    "hilited_candidate_back_color" to "0x48484C",
                    "hilited_candidate_text_color" to "0xFFFFFF",
                    "hilited_comment_text_color" to "0xD1D1D6",
                    "hilited_enter_key_action_back_color" to "0x409CFF",
                    "hilited_key_back_color" to "0x8E8E93",
                    "hilited_key_symbol_color" to "0xCFCFD3",
                    "hilited_key_text_color" to "0xFFFFFF",
                    "hilited_off_key_back_color" to "0x6B6B6E",
                    "hilited_off_key_text_color" to "0xFFFFFF",
                    "hilited_on_key_back_color" to "0xFFFFFF",
                    "hilited_on_key_text_color" to "0x000000",
                    "hilited_text_color" to "0xFFFFFF",
                    "key_back_color" to "0x6B6B6E",
                    "key_border_color" to "0x006B6B6E",
                    "key_symbol_color" to "0xB5B5B9",
                    "key_text_color" to "0xFFFFFF",
                    "keyboard_back_color" to "0x2A2A2C",
                    "keyboard_background" to "0x2A2A2C",
                    "label_color" to "0x8E8E93",
                    "liquid_keyboard_background" to "0x2A2A2C",
                    "long_text_back_color" to "0x6B6B6E",
                    "name" to "iOS 深色",
                    "off_key_back_color" to "0x464649",
                    "off_key_text_color" to "0xFFFFFF",
                    "on_key_back_color" to "0xFFFFFF",
                    "on_key_text_color" to "0x000000",
                    "preview_back_color" to "0x6B6B6E",
                    "preview_text_color" to "0xFFFFFF",
                    "root_background" to "0x2A2A2C",
                    "shadow_color" to "0x00000000",
                    "text_back_color" to "0x3A3A3C",
                    "text_color" to "0xFFFFFF",
                ),
            ),
            ColorScheme(
                id = "ios_light",
                colors =
                mapOf(
                    "author" to "generated",
                    "back_color" to "0xD1D3D9",
                    "border_color" to "0xD1D3D9",
                    "candidate_background" to "0x00000000",
                    "candidate_separator_color" to "0x00D1D3D9",
                    "candidate_text_color" to "0x000000",
                    "comment_text_color" to "0x6C6C70",
                    "enter_key_action_back_color" to "0x007AFF",
                    "enter_key_action_text_color" to "0xFFFFFF",
                    "func_key_back_color" to "0xACB1BA",
                    "func_key_hilited_back_color" to "0xFFFFFF",
                    "hilited_back_color" to "0xFFFFFF",
                    "hilited_candidate_back_color" to "0xBCC0C7",
                    "hilited_candidate_text_color" to "0x000000",
                    "hilited_comment_text_color" to "0x6C6C70",
                    "hilited_enter_key_action_back_color" to "0x2F8FFF",
                    "hilited_key_back_color" to "0xE1E3E8",
                    "hilited_key_symbol_color" to "0x86868B",
                    "hilited_key_text_color" to "0x000000",
                    "hilited_off_key_back_color" to "0xFFFFFF",
                    "hilited_off_key_text_color" to "0x000000",
                    "hilited_on_key_back_color" to "0xFFFFFF",
                    "hilited_on_key_text_color" to "0x000000",
                    "hilited_text_color" to "0x000000",
                    "key_back_color" to "0xFFFFFF",
                    "key_border_color" to "0x00FFFFFF",
                    "key_symbol_color" to "0x86868B",
                    "key_text_color" to "0x000000",
                    "keyboard_back_color" to "0xD1D3D9",
                    "keyboard_background" to "0xD1D3D9",
                    "label_color" to "0x8E8E93",
                    "liquid_keyboard_background" to "0xD1D3D9",
                    "long_text_back_color" to "0xFFFFFF",
                    "name" to "iOS 浅色",
                    "off_key_back_color" to "0xACB1BA",
                    "off_key_text_color" to "0x000000",
                    "on_key_back_color" to "0xFFFFFF",
                    "on_key_text_color" to "0x000000",
                    "preview_back_color" to "0xFFFFFF",
                    "preview_text_color" to "0x000000",
                    "root_background" to "0xD1D3D9",
                    "shadow_color" to "0x00000000",
                    "text_back_color" to "0xF2F2F7",
                    "text_color" to "0x000000",
                ),
            ),
        )

    val fallbackColors =
        mapOf(
            "candidate_text_color" to "text_color",
        )
}
