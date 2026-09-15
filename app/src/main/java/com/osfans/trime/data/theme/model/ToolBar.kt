/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ToolBar(
    val primaryButton: Button? = null,
    val buttons: List<Button> = emptyList(),
    val buttonSpacing: Int = 18,
    /**
     * Where the buttons after the first go: [ButtonsAlignment.END] lays them from the end
     * towards the start, as the View bar did; [ButtonsAlignment.START] lays them in order
     * after the primary button. The first button always takes the end slot.
     */
    val buttonsAlignment: ButtonsAlignment = ButtonsAlignment.END,
    val buttonFont: List<String> = emptyList(),
    val backStyle: String = "ic@arrow-left",
) : Parcelable {
    enum class ButtonsAlignment {
        START,
        END,
    }

    @Parcelize
    data class Button(
        val background: Background = Background(),
        val foreground: Foreground = Foreground(),
        val action: String = "",
        val longPressAction: String = "",
        val size: List<Int> = emptyList(),
    ) : Parcelable {

        @Parcelize
        data class Background(
            val type: Type = Type.RECTANGLE,
            val cornerRadius: Float = 10f,
            val normal: String = "",
            val highlight: String = "",
            val verticalInset: Int = 4,
            val horizontalInset: Int = 4,
        ) : Parcelable {
            enum class Type {
                RECTANGLE,
                CIRCLE,
            }
        }

        @Parcelize
        data class Foreground(
            val style: String = "",
            val optionStyles: List<String> = emptyList(),
            val normal: String = "",
            val highlight: String = "",
            val fontSize: Float = 18f,
            val padding: Int = 4,
        ) : Parcelable
    }
}
