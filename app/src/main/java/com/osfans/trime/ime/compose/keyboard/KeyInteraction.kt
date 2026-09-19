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
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
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

    private val deletedTextBuffer = ArrayDeque<String>()

    private fun isVoiceKey(key: Key): Boolean =
        key.click?.command == CommonKeyboardActionListener.VOICE_INPUT_COMMAND ||
            key.getAction(KeyBehavior.LONG_CLICK)?.command == CommonKeyboardActionListener.VOICE_INPUT_COMMAND

    private val caps =
        keyboard.keys.map { key ->
            KeyCaps(
                repeatable = key.click?.isRepeatable ?: false,
                slideCursor = key.click?.isSlideCursor ?: false,
                slideDelete = key.click?.isSlideDelete ?: false,
                hasLongPress = key.hasAction(KeyBehavior.LONG_CLICK) || isVoiceKey(key),
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
        // touching any key but the mic ends dictation first, keeping what was recognised
        if (!isVoiceKey(k)) service.voiceInput.interrupt()
        showPopupPreview(k)
    }

    override fun onRelease(
        key: Int,
        behavior: KeyBehavior,
        fromLongPress: Boolean,
    ) {
        val k = keyboard.keys[key]
        Timber.d("Key release: label=${k.getLabel()}, behavior=$behavior, fromLongPress=$fromLongPress")
        if (fromLongPress) {
            if (isVoiceKey(k) && service.voiceInput.isHoldMode) {
                service.voiceInput.stopHold()
                setPressedState(k, false)
                dismissPopupPreview(k)
                if (keyboard.firstPressedKeyIndex == key) keyboard.firstPressedKeyIndex = -1
                return
            }
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
        if (isVoiceKey(k)) {
            dismissPopupPreview(k)
            service.voiceInput.startHold()
            return
        }
        if (k.popup.isNotEmpty()) {
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
        if (longPressed && caps[key].hasPopup) {
            popup.listener.onPopupAction(PopupAction.ChangeFocusAction(key, x, y))
        }
    }

    override fun onCancel(key: Int) {
        val k = keyboard.keys[key]
        if (isVoiceKey(k) && service.voiceInput.isHoldMode) {
            service.voiceInput.stopHold()
        }
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
        // Only a key about to type one character gets a bubble. Function keys answer a press by
        // changing shade (docs/ime-design-system.md): icons (shift, backspace), word labels
        // (123, 换行, 空格) and modifiers. Themes rarely mark keys `functional`, so it is not asked.
        if (key.getAction(behavior)?.isModifierKey == true) return
        val previewText = key.getPreviewText(behavior)
        if (previewText.isEmpty() || previewText.isIconFont || previewText.codePointCount(0, previewText.length) != 1) return
        popup.listener.onPopupAction(PopupAction.PreviewAction(key.index, previewText, host.keyBoundsInWindow(key)))
    }

    private fun dismissPopupPreview(key: Key) {
        popup.listener.onPopupAction(PopupAction.DismissAction(key.index))
    }
}
