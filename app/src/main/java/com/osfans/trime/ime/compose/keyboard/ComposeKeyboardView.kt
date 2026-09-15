/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.broadcast.EnterKeyDisplayDelegate
import com.osfans.trime.ime.compose.imeComposeView
import com.osfans.trime.ime.compose.keyboard.gesture.keyGestures
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.Key
import com.osfans.trime.ime.keyboard.Keyboard
import com.osfans.trime.ime.keyboard.KeyboardActionListener
import com.osfans.trime.ime.keyboard.KeyboardPrefs.floatingScale
import com.osfans.trime.ime.popup.PopupDelegate

/**
 * One keyboard layout, drawn and touched in Compose. Takes the place of the old
 * `KeyboardView` + one `KeyView` per key, with the same surface towards `KeyboardWindow`.
 *
 * The three moving parts live apart so they can change independently:
 * [KeyboardCanvas] draws, [com.osfans.trime.ime.compose.keyboard.gesture.keyGestures] turns
 * pointers into per-key gestures, [KeyInteraction] decides what a gesture does.
 */
@SuppressLint("ViewConstructor")
class ComposeKeyboardView(
    context: Context,
    theme: Theme,
    val keyboard: Keyboard,
    val popup: PopupDelegate,
    val service: TrimeInputMethodService,
    keyboardActionListener: KeyboardActionListener,
    enterKeyDisplay: EnterKeyDisplayDelegate,
) : FrameLayout(context) {
    val renderState =
        KeyboardRenderState(
            theme,
            context.floatingScale(),
            enterLabel = { enterKeyDisplay.keyLabel },
            enterPrimaryAction = { enterKeyDisplay.isPrimaryAction },
            voice = service.voiceInput.indicator,
        )

    private val interaction = KeyInteraction(this, keyboard, keyboardActionListener)

    private val windowLocation = IntArray(2)

    init {
        addView(
            context.imeComposeView {
                KeyboardCanvas(
                    keyboard,
                    renderState,
                    Modifier.fillMaxSize().keyGestures(interaction, interaction),
                )
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val width = minOf(MeasureSpec.getSize(widthMeasureSpec), keyboard.minWidth + paddingLeft + paddingRight)
        val height = keyboard.height + paddingTop + paddingBottom
        measureChildren(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        setMeasuredDimension(width, height)
    }

    /** The key's own rectangle (no extra touch width) in window coordinates, for popups. */
    fun keyBoundsInWindow(key: Key): Rect {
        getLocationInWindow(windowLocation)
        val (x, y) = windowLocation
        return Rect(x + key.x, y + key.y, x + key.x + key.width, y + key.y + key.height)
    }

    fun invalidateAllKeys() = renderState.invalidate()

    fun invalidateKeyByIndex(
        @Suppress("UNUSED_PARAMETER") index: Int,
    ) = renderState.invalidate()

    val isCapsOn: Boolean
        get() = keyboard.mShiftKey?.isOn == true

    fun onDetach() {
        popup.dismissAll()
    }
}
