/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.builtin

import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.ToolBar

/**
 * The only theme the app ships: a modern Android (Material) look over the key layouts that began
 * as `ios.trime.yaml` in the config repo. Edit the `Builtin*` objects next to this file to change it.
 */
object BuiltinTheme {
    /**
     * The bar above the keyboard while nothing is being typed: the switches button ("…", the built-in
     * primary button) at the start, hide keyboard at the far end. Clipboard and the edit panel live
     * only on the keyboard's bottom row. `buttons[0]` always takes the end slot; any others follow
     * the primary button in order.
     */
    private val toolBar =
        ToolBar(
            buttons =
            listOf(
                toolBarButton("ic@menu-down", "Hide"),
            ),
            // the buttons are already as wide as the bar is tall; the glyphs need no extra gap
            buttonSpacing = 0,
            buttonsAlignment = ToolBar.ButtonsAlignment.START,
        )

    val theme =
        Theme(
            name = "Material",
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
     * Looks like the built-in "…" button: a 24dp Material glyph (see `ImeIcons`) in the candidate
     * colour, a round block of the highlight colour while pressed. The tab bar's back arrow copies
     * the first button.
     */
    private fun toolBarButton(
        icon: String,
        action: String,
    ) = ToolBar.Button(
        background =
        ToolBar.Button.Background(
            type = ToolBar.Button.Background.Type.CIRCLE,
            highlight = "hilited_candidate_back_color",
            verticalInset = 4,
            horizontalInset = 4,
        ),
        foreground = ToolBar.Button.Foreground(style = icon, highlight = "candidate_text_color", fontSize = 24f),
        action = action,
    )
}
