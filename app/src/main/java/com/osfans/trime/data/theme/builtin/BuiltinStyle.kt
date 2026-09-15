/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.builtin

import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.data.theme.model.Preedit
import com.osfans.trime.data.theme.model.Window

/**
 * Sizes, fonts and paddings of the built-in theme. Only values that differ from the model
 * defaults are written here. Fonts are looked up by file name in the user data dir's `fonts/`.
 */
object BuiltinStyle {
    val style =
        GeneralStyle(
            candidateFont = listOf("MiSans-Medium.ttf"),
            candidatePadding = 12,
            candidateTextSize = 20f,
            candidateTextVerticalBias = 0.5f,
            candidateViewHeight = 52,
            candidateViewHeightLand = 32,
            candidateCornerRadius = 6f,
            hilitedCandidatePadding = 4f,
            commentFont = listOf("MiSans-Regular.ttf"),
            commentTextSize = 14f,
            horizontalGap = 6,
            keyboardPadding = 3,
            keyboardPaddingLeft = 3,
            keyboardPaddingRight = 3,
            keyboardPaddingBottom = 4,
            keyboardPaddingLand = 40,
            keyboardPaddingLandBottom = 4,
            keyFont = listOf("MiSans-Medium.ttf"),
            keyHeight = 53,
            keyLongTextSize = 16f,
            keyTextSize = 22f,
            keySymbolOffsetX = 10f,
            keySymbolOffsetY = 1f,
            keyWidth = 10f,
            labelTextSize = 16f,
            labelFont = listOf("MiSans-Medium.ttf"),
            keyboardHeight = 256,
            keyboardHeightLand = 160,
            keyboardCornerRadius = 26f,
            popupBottomMargin = 62,
            popupWidth = 44,
            popupHeight = 56,
            popupKeyHeight = 48,
            popupFont = listOf("MiSans-Medium.ttf"),
            popupTextSize = 30f,
            roundCorner = 6f,
            symbolFont = listOf("MiSans-Regular.ttf"),
            symbolTextSize = 10f,
            textFont = listOf("MiSans-Regular.ttf"),
            verticalGap = 8,
            enterLabel = GeneralStyle.EnterLabel(
                go = "前往",
                done = "完成",
                next = "下一个",
                pre = "上一个",
                search = "搜索",
                send = "发送",
                default = "换行",
            ),
        )

    /** Inline preedit bar above the candidates. */
    val preedit =
        Preedit(
            topStartRadius = 6f,
            topEndRadius = 6f,
            alpha = 1f,
        )

    /** Floating candidate window (used when the keyboard is hidden, e.g. with a hardware keyboard). */
    val window =
        Window(
            insets = Window.Padding(vertical = 6, horizontal = 8),
            itemPadding = Window.Padding(vertical = 4, horizontal = 8),
            cornerRadius = 10f,
            shadow = 4f,
            foreground = Window.Foreground(labelFontSize = 16f, commentFontSize = 14f),
        )
}
