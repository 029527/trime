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
import com.osfans.trime.data.theme.colorCached
import com.osfans.trime.ime.compose.voice.DictationIndicator

/**
 * What the key canvas needs besides the [com.osfans.trime.ime.keyboard.Keyboard] model.
 *
 * Key state (pressed, on, modifiers) lives in the mutable `Key` / `Keyboard` objects, which
 * Compose cannot observe; whoever changes it calls [invalidate], and the canvas reads
 * [observe] inside its draw lambda, so a key press costs a redraw and never a recomposition.
 *
 * Sizes are not here: they come from [com.osfans.trime.ime.compose.theme.ImeTokens]. The theme
 * only contributes colours, through the keys and the enter-key action colours below.
 */
@Stable
class KeyboardRenderState(
    @Suppress("UNUSED_PARAMETER") theme: Theme,
    /**
     * Scale for key text, icons and hint insets, following the floating keyboard size preset;
     * 1 when not floating. Key bodies, gaps and corners are not scaled.
     */
    val textScale: Float,
    private val enterLabel: () -> String,
    private val enterPrimaryAction: () -> Boolean,
    /** Dictation, for the mic key. Read while drawing, so a change redraws without [invalidate]. */
    val voice: DictationIndicator,
) {
    val hideKeySymbol by AppPrefs.defaultInstance().keyboard.hideKeySymbol
    val hideKeyHint by AppPrefs.defaultInstance().keyboard.hideKeyHint

    val labelEnter: String get() = enterLabel()
    val isEnterPrimaryAction: Boolean get() = enterPrimaryAction()

    // The enter key always wears the accent (enter_key_action_*), whatever the editor asks for;
    // when a scheme does not define them the key keeps its own colours.
    // Re-resolved after a scheme or tint change, like the per-key colours in Key.
    val actionKeyBackground: Drawable? by colorCached {
        runCatching { ColorManager.getDrawable("enter_key_action_back_color") }.getOrNull()
    }
    val hlActionKeyBackground: Drawable? by colorCached {
        runCatching { ColorManager.getDrawable("hilited_enter_key_action_back_color") }.getOrNull()
            ?: actionKeyBackground
    }
    val actionKeyTextColor: Int? by colorCached {
        runCatching { ColorManager.getColor("enter_key_action_text_color") }.getOrNull()
    }

    // The accent as plain colours, for the mic key while dictating (it is never an image there).
    val accentBackColor: Int by colorCached {
        runCatching { ColorManager.getColor("enter_key_action_back_color") }.getOrElse { ColorManager.getColor("key_text_color") }
    }
    val accentTextColor: Int by colorCached {
        runCatching { ColorManager.getColor("enter_key_action_text_color") }.getOrElse { ColorManager.getColor("key_back_color") }
    }

    private val revision = mutableIntStateOf(0)

    fun invalidate() {
        revision.intValue++
    }

    /** Read this in a draw lambda to be redrawn on [invalidate]. */
    fun observe(): Int = revision.intValue
}
