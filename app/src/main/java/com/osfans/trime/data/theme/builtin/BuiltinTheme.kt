/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.builtin

import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.ToolBar

/**
 * The only theme the app ships: the iOS look, formerly `ios.trime.yaml` in the config repo.
 * Edit the `Builtin*` objects next to this file to change it.
 */
object BuiltinTheme {
    /**
     * The bar above the keyboard while nothing is being typed, left to right: the switches button
     * ("…", the built-in primary button), edit panel, clipboard, then hide keyboard at the far end.
     * `buttons[0]` always takes the end slot; the others follow the primary button in order.
     */
    private val toolBar =
        ToolBar(
            buttons =
            listOf(
                toolBarButton("ic@menu-down", "Hide", glyphSize = 16f),
                toolBarButton("ic@cursor-text", "edit_panel", glyphSize = 20f),
                toolBarButton("ic@clipboard-outline", "clipboard_window", glyphSize = 20f),
            ),
            // the buttons are already as wide as the bar is tall; the glyphs need no extra gap
            buttonSpacing = 0,
            buttonsAlignment = ToolBar.ButtonsAlignment.START,
        )

    val theme =
        Theme(
            name = "iOS",
            generalStyle = BuiltinStyle.style,
            preedit = BuiltinStyle.preedit,
            window = BuiltinStyle.window,
            liquidKeyboard = BuiltinLiquid.liquidKeyboard,
            presetKeys = BuiltinKeys.presetKeys,
            presetKeyboards = BuiltinKeyboards.keyboards,
            colorSchemes = BuiltinColors.schemes,
            fallbackColors = BuiltinColors.fallbackColors,
            toolBar = toolBar,
        )

    /**
     * Looks like the built-in "…" button: the glyph in the candidate colour, a rounded block while
     * pressed. An `ic@` glyph fills its whole [glyphSize] box, where a Material icon keeps padding
     * inside its 24dp, so the sizes are picked to match the visible size of those icons. The tab
     * bar's back arrow copies the first button's size.
     */
    private fun toolBarButton(
        icon: String,
        action: String,
        glyphSize: Float,
    ) = ToolBar.Button(
        background =
        ToolBar.Button.Background(
            highlight = "hilited_candidate_back_color",
            cornerRadius = 8f,
            verticalInset = 0,
            horizontalInset = 0,
        ),
        foreground = ToolBar.Button.Foreground(style = icon, highlight = "candidate_text_color", fontSize = glyphSize),
        action = action,
    )
}
