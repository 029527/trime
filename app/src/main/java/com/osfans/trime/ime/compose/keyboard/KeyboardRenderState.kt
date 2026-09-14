/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.keyboard

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme

/**
 * What the key canvas needs besides the [com.osfans.trime.ime.keyboard.Keyboard] model.
 *
 * Key state (pressed, on, modifiers) lives in the mutable `Key` / `Keyboard` objects, which
 * Compose cannot observe; whoever changes it calls [invalidate], and the canvas reads
 * [observe] inside its draw lambda, so a key press costs a redraw and never a recomposition.
 */
@Stable
class KeyboardRenderState(
    theme: Theme,
    /** Key text scales with the floating keyboard size preset; 1 when not floating. */
    val textScale: Float,
    private val enterLabel: () -> String,
    private val enterPrimaryAction: () -> Boolean,
) {
    val keyTextSize = theme.generalStyle.keyTextSize * textScale
    val keyLongTextSize = (theme.generalStyle.keyLongTextSize.takeIf { it > 0 } ?: theme.generalStyle.keyTextSize) * textScale
    val symbolTextSize = (theme.generalStyle.symbolTextSize.takeIf { it > 0 } ?: theme.generalStyle.keyTextSize) * textScale

    val hideKeySymbol by AppPrefs.defaultInstance().keyboard.hideKeySymbol
    val hideKeyHint by AppPrefs.defaultInstance().keyboard.hideKeyHint

    val labelEnter: String get() = enterLabel()
    val isEnterPrimaryAction: Boolean get() = enterPrimaryAction()

    // Optional colors for the enter key when the editor asks for a primary action
    // (go / search / send / done), like the blue return key on iOS. Only used when the
    // color scheme defines them.
    val actionKeyBackground: Drawable? by lazy {
        runCatching { ColorManager.getDrawable("enter_key_action_back_color") }.getOrNull()
    }
    val hlActionKeyBackground: Drawable? by lazy {
        runCatching { ColorManager.getDrawable("hilited_enter_key_action_back_color") }.getOrNull()
            ?: actionKeyBackground
    }
    val actionKeyTextColor: Int? by lazy {
        runCatching { ColorManager.getColor("enter_key_action_text_color") }.getOrNull()
    }

    private val revision = mutableIntStateOf(0)

    fun invalidate() {
        revision.intValue++
    }

    /** Read this in a draw lambda to be redrawn on [invalidate]. */
    fun observe(): Int = revision.intValue
}
