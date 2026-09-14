/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.keyboard

import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ime.compose.keyboard.gesture.KeyCaps
import com.osfans.trime.ime.compose.keyboard.gesture.KeyGestureListener
import com.osfans.trime.ime.compose.keyboard.gesture.KeyGestureTarget
import com.osfans.trime.ime.compose.keyboard.gesture.KeyHitTester
import com.osfans.trime.ime.compose.keyboard.gesture.KeyTouchBox
import com.osfans.trime.ime.keyboard.Key
import com.osfans.trime.ime.keyboard.KeyAction
import com.osfans.trime.ime.keyboard.KeyBehavior
import com.osfans.trime.ime.keyboard.Keyboard
import com.osfans.trime.ime.keyboard.KeyboardActionListener
import com.osfans.trime.ime.keyboard.isIconFont
import com.osfans.trime.ime.popup.PopupAction
import timber.log.Timber
import kotlin.math.floor

/**
 * What pressing, swiping and holding a key does: `KeyView`'s behavior, keyed by key index
 * instead of living in one view per key. The popup ids are key indices, as the view ids were.
 */
class KeyInteraction(
    private val host: ComposeKeyboardView,
    private val keyboard: Keyboard,
    private val keyboardActionListener: KeyboardActionListener,
) : KeyGestureTarget,
    KeyGestureListener {
    private val popup get() = host.popup
    private val service get() = host.service

    private val popupOnKeyPress by AppPrefs.defaultInstance().keyboard.popupOnKeyPress
    private val hookShiftArrow by AppPrefs.defaultInstance().keyboard.hookShiftArrow

    /** Presses taken over by [KeyboardActionListener.onHoldStart] (hold-to-talk), per key. */
    private val holdActions = HashMap<Int, KeyAction>()

    private val deletedTextBuffer = ArrayDeque<String>()

    private val caps =
        keyboard.keys.map { key ->
            KeyCaps(
                repeatable = key.click?.isRepeatable ?: false,
                slideCursor = key.click?.isSlideCursor ?: false,
                slideDelete = key.click?.isSlideDelete ?: false,
                hasLongPress = key.hasAction(KeyBehavior.LONG_CLICK),
                hasDouble = key.hasAction(KeyBehavior.DOUBLE_CLICK),
                hasLazyDouble = key.hasAction(KeyBehavior.LAZY_DOUBLE_CLICK),
                hasPopup = key.popup.isNotEmpty(),
            )
        }

    /** Touch areas of the keys, extra touch width included; the layout is fixed once [keyboard] is built. */
    private val touchBoxes by lazy {
        keyboard.keys.map { key ->
            KeyTouchBox(key.x - key.extraWidthLeft, key.y, key.x + key.width + key.extraWidthRight, key.y + key.height, key.edgeFlags)
        }
    }

    override fun keyAt(
        x: Float,
        y: Float,
    ): Int = KeyHitTester.keyAt(touchBoxes, floor(x).toInt(), floor(y).toInt()) { px, py ->
        keyboard.getNearestKeys(px, py) ?: IntArray(0)
    }

    override fun capsOf(key: Int): KeyCaps = caps[key]

    override fun cellOrigin(key: Int): Offset = keyboard.keys[key].let { Offset((it.x - it.extraWidthLeft).toFloat(), it.y.toFloat()) }

    override fun onPress(key: Int) {
        val k = keyboard.keys[key]
        if (keyboard.firstPressedKeyIndex == -1) keyboard.firstPressedKeyIndex = key
        setPressedState(k, true)
        keyboardActionListener.onPress(k.getCode(KeyBehavior.CLICK))
        // 「按住说话」这类键在按下的那一刻就要开始干活，松手才结束；被接管之后这次按压不会再走 onAction。
        k.getAction(KeyBehavior.CLICK)?.takeIf { keyboardActionListener.onHoldStart(it) }?.let { holdActions[key] = it }
        showPopupPreview(k)
    }

    override fun onRelease(
        key: Int,
        behavior: KeyBehavior,
        fromLongPress: Boolean,
    ) {
        val k = keyboard.keys[key]
        Timber.d("Key release: label=${k.getLabel()}, behavior=$behavior, fromLongPress=$fromLongPress")
        val held = holdActions.remove(key)
        if (held != null) {
            keyboardActionListener.onHoldEnd(held)
            setPressedState(k, false)
            dismissPopupPreview(k)
        } else if (fromLongPress) {
            if (caps[key].hasPopup) {
                val triggerAction = PopupAction.TriggerAction(key)
                popup.listener.onPopupAction(triggerAction)
                triggerAction.outAction?.let { action ->
                    keyboardActionListener.onAction(KeyAction(action))
                    dismissPopupPreview(k)
                }
                setPressedState(k, false)
            } else if (caps[key].repeatable && behavior == KeyBehavior.CLICK) {
                // each repeat arrives as CLICK; the LONG_CLICK release that ends the hold must not type once more
                k.getAction(KeyBehavior.CLICK)?.let { processKeyAction(it, KeyBehavior.CLICK) }
            }
        } else {
            when (behavior) {
                KeyBehavior.CLICK -> {
                    val pressedIdx = keyboard.firstPressedKeyIndex
                    val actionBehavior = if (pressedIdx != -1 && pressedIdx != key) KeyBehavior.COMBO else behavior
                    k.getAction(actionBehavior)?.let { processKeyAction(it, actionBehavior) }
                }
                KeyBehavior.DOUBLE_CLICK, KeyBehavior.LAZY_DOUBLE_CLICK,
                KeyBehavior.SWIPE_UP, KeyBehavior.SWIPE_DOWN, KeyBehavior.SWIPE_LEFT, KeyBehavior.SWIPE_RIGHT,
                -> k.getAction(behavior)?.let { processKeyAction(it, behavior) }
                else -> {}
            }
            setPressedState(k, false)
            dismissPopupPreview(k)
        }
        if (keyboard.firstPressedKeyIndex == key) keyboard.firstPressedKeyIndex = -1
    }

    override fun onSwipe(
        key: Int,
        behavior: KeyBehavior,
    ) {
        val k = keyboard.keys[key]
        setPressedState(k, true)
        showPopupPreview(k, behavior)
    }

    override fun onSlide(
        key: Int,
        delta: Int,
    ) {
        val c = caps[key]
        if (c.slideCursor) {
            when {
                delta > 0 -> keyboardActionListener.onAction(KeyAction("Right"))
                delta < 0 -> keyboardActionListener.onAction(KeyAction("Left"))
            }
        } else if (c.slideDelete) {
            val ic = service.currentInputConnection ?: return
            when {
                delta < 0 -> {
                    val beforeText = ic.getTextBeforeCursor(1, 0) ?: ""
                    if (beforeText.isNotEmpty()) {
                        deletedTextBuffer.addFirst(beforeText.toString())
                        ic.deleteSurroundingText(1, 0)
                    }
                }
                delta > 0 -> {
                    if (deletedTextBuffer.isNotEmpty()) {
                        ic.commitText(deletedTextBuffer.removeFirst(), 1)
                    }
                }
            }
        }
    }

    override fun onLongPress(key: Int) {
        val k = keyboard.keys[key]
        if (holdActions.containsKey(key)) {
            // 这次按压已经被「按住说话」接管了：长按不能再触发这个键原本的长按动作，
            // 否则按住麦克风一秒就会顺手切走键盘。
        } else if (k.popup.isNotEmpty()) {
            dismissPopupPreview(k)
            popup.listener.onPopupAction(PopupAction.ShowKeyboardAction(key, k.popup, host.keyBoundsInWindow(k)))
        } else if (caps[key].hasLongPress) {
            k.getAction(KeyBehavior.LONG_CLICK)?.let {
                processKeyAction(it, KeyBehavior.LONG_CLICK)
                setPressedState(k, false)
                dismissPopupPreview(k)
            }
        }
    }

    override fun onMove(
        key: Int,
        x: Float,
        y: Float,
        longPressed: Boolean,
    ) {
        if (longPressed && caps[key].hasPopup && !holdActions.containsKey(key)) {
            popup.listener.onPopupAction(PopupAction.ChangeFocusAction(key, x, y))
        }
    }

    override fun onCancel(key: Int) {
        val k = keyboard.keys[key]
        holdActions.remove(key)?.let { keyboardActionListener.onHoldEnd(it) }
        deletedTextBuffer.clear()
        setPressedState(k, false)
        dismissPopupPreview(k)
    }

    private fun setPressedState(
        key: Key,
        pressed: Boolean,
    ) {
        if (key.isPressed == pressed) return
        if (pressed) key.onPressed() else key.onReleased()
        host.renderState.invalidate()
    }

    private fun processKeyAction(
        action: KeyAction,
        behavior: KeyBehavior,
    ) {
        if (action.isModifierKey) {
            keyboard.clickModifierKey(action.isShiftLock xor (behavior == KeyBehavior.LONG_CLICK), action.modifierKeyOnMask)
            host.renderState.invalidate()
            return
        }
        keyboardActionListener.onAction(action)
        val hookArrow =
            hookShiftArrow &&
                when (action.code) {
                    in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT -> true
                    KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END -> true
                    else -> false
                }
        if (!hookArrow && keyboard.refreshModifier()) host.renderState.invalidate()
    }

    private fun showPopupPreview(
        key: Key,
        behavior: KeyBehavior = KeyBehavior.CLICK,
    ) {
        if (!popupOnKeyPress) return
        val previewText = key.getPreviewText(behavior).takeIf { it.isNotEmpty() } ?: return
        val content = if (previewText.isIconFont) previewText else String(Character.toChars(previewText.codePointAt(0)))
        popup.listener.onPopupAction(PopupAction.PreviewAction(key.index, content, host.keyBoundsInWindow(key)))
    }

    private fun dismissPopupPreview(key: Key) {
        popup.listener.onPopupAction(PopupAction.DismissAction(key.index))
    }
}
