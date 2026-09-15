/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.osfans.trime.ime.keyboard.KeyBehavior
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class TextKeyboard(
    val name: String = "",
    val author: String = "",
    val width: Float = 0f,
    val height: Float = 0f,
    val keyboardHeight: Int = 0,
    val keyboardHeightLand: Int = 0,
    val autoHeightIndex: Int = -1,
    val horizontalGap: Int = 0,
    val verticalGap: Int = 0,
    val roundCorner: Float = -1f,
    val keyBorder: Int = -1,
    val columns: Int = 30,
    val asciiMode: Boolean = true,
    val resetAsciiMode: Boolean = false,
    val labelTransform: LabelTransform = LabelTransform.NONE,
    val lock: Boolean = false,
    val asciiKeyboard: String = "",
    val landscapeKeyboard: String = "",
    val landscapeSplitPercent: Int = 0,
    val keyTextOffsetX: Float = 0f,
    val keyTextOffsetY: Float = 0f,
    val keySymbolOffsetX: Float = 0f,
    val keySymbolOffsetY: Float = 0f,
    val keyHintOffsetX: Float = 0f,
    val keyHintOffsetY: Float = 0f,
    val keyPressOffsetX: Float = 0f,
    val keyPressOffsetY: Float = 0f,
    val importPreset: String = "",
    val keys: List<TextKey> = emptyList(),
) : Parcelable {
    enum class LabelTransform {
        NONE,
        UPPERCASE,
    }

    /**
     * One key of a [TextKeyboard]. Every action slot (`click`, `longClick`, `swipeUp`, ...)
     * maps to the [KeyBehavior] of the same name; an empty slot is `null`.
     */
    @Parcelize
    data class TextKey(
        val width: Float = 0f,
        val height: Float = 0f,
        val roundCorner: Float = -1f,
        val keyBorder: Int = -1,
        val label: String = "",
        val labelSymbol: String = "",
        val hint: String = "",
        val sendBindings: Boolean = true,
        val keyTextSize: Float = 0f,
        val symbolTextSize: Float = 0f,
        val keyTextOffsetX: Float = 0f,
        val keyTextOffsetY: Float = 0f,
        val keySymbolOffsetX: Float = 0f,
        val keySymbolOffsetY: Float = 0f,
        val keyHintOffsetX: Float = 0f,
        val keyHintOffsetY: Float = 0f,
        val keyPressOffsetX: Float = 0f,
        val keyPressOffsetY: Float = 0f,
        val keyTextColor: String = "",
        val keyBackColor: String = "",
        val keyBorderColor: String = "",
        val keySymbolColor: String = "",
        val hlKeyTextColor: String = "",
        val hlKeyBackColor: String = "",
        val hlKeyBorderColor: String = "",
        val hlKeySymbolColor: String = "",
        val popup: List<String> = emptyList(),
        /** Drop this key (and its row, if every key in the row is dropped) when the device is in landscape. */
        val hideInLandscape: Boolean = false,
        /** Drop this key (and its row, if every key in the row is dropped) in portrait; the counterpart of [hideInLandscape]. */
        val hideInPortrait: Boolean = false,
        /** Width in landscape, `0` to keep [width]; lets a landscape-only key take its room from a neighbour. */
        val widthLand: Float = 0f,
        val composing: KeyActionToken? = null,
        val hasMenu: KeyActionToken? = null,
        val paging: KeyActionToken? = null,
        val combo: KeyActionToken? = null,
        val ascii: KeyActionToken? = null,
        val click: KeyActionToken? = null,
        val doubleClick: KeyActionToken? = null,
        val lazyDoubleClick: KeyActionToken? = null,
        val swipeUp: KeyActionToken? = null,
        val longClick: KeyActionToken? = null,
        val swipeDown: KeyActionToken? = null,
        val swipeLeft: KeyActionToken? = null,
        val swipeRight: KeyActionToken? = null,
        val extra: KeyActionToken? = null,
    ) : Parcelable {
        /** The action slots by [KeyBehavior]; [KeyBehavior.CLICK] is always present, the rest only when set. */
        @IgnoredOnParcel
        val behaviors: Map<KeyBehavior, KeyActionToken?> =
            buildMap {
                KeyBehavior.entries.forEach { behavior ->
                    val token = tokenOf(behavior)
                    if (token != null || behavior == KeyBehavior.CLICK) put(behavior, token)
                }
            }

        val hasClickAction: Boolean get() = click != null

        private fun tokenOf(behavior: KeyBehavior): KeyActionToken? = when (behavior) {
            KeyBehavior.COMPOSING -> composing
            KeyBehavior.HAS_MENU -> hasMenu
            KeyBehavior.PAGING -> paging
            KeyBehavior.COMBO -> combo
            KeyBehavior.ASCII -> ascii
            KeyBehavior.CLICK -> click
            KeyBehavior.DOUBLE_CLICK -> doubleClick
            KeyBehavior.LAZY_DOUBLE_CLICK -> lazyDoubleClick
            KeyBehavior.SWIPE_UP -> swipeUp
            KeyBehavior.LONG_CLICK -> longClick
            KeyBehavior.SWIPE_DOWN -> swipeDown
            KeyBehavior.SWIPE_LEFT -> swipeLeft
            KeyBehavior.SWIPE_RIGHT -> swipeRight
            KeyBehavior.EXTRA -> extra
        }
    }
}
