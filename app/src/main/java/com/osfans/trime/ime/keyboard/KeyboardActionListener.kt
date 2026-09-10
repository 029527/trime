/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

interface KeyboardActionListener {
    /**
     * Called when the user presses a key. This is sent before the [.onKey] is called. For
     * keys that repeat, this is only called once.
     *
     * @param keyEventCode the unicode of the key being pressed. If the touch is not on a valid key,
     * the value will be zero.
     */
    fun onPress(keyEventCode: Int)

    fun onAction(action: KeyAction)

    /**
     * 按下时问一句：这个键要不要走"按住不放"的交互（语音输入的麦克风键）。
     *
     * 返回 true 表示这一次按压被接管了 —— 松手时只会收到 [onHoldEnd]，
     * **不会**再触发 [onAction]。返回 false 是默认行为，一切照旧。
     */
    fun onHoldStart(action: KeyAction): Boolean = false

    /** 与 [onHoldStart] 配对：松手、滑走、被取消都会走到这里。 */
    fun onHoldEnd(action: KeyAction) {}

    /**
     * Send a key press to the listener.
     *
     * @param keyEventCode this is the key that was pressed
     * @param metaState the codes for all the possible alternative keys with the primary code being the
     * first. If the primary key code is a single character such as an alphabet or number or
     * symbol, the alternatives will include other characters that may be on the same key or
     * adjacent keys. These codes are useful to correct for accidental presses of a key adjacent
     * to the intended key.
     */
    fun onKey(
        keyEventCode: Int,
        metaState: Int,
    )

    /**
     * Sends a sequence of characters to the listener.
     *
     * @param input the sequence of characters to be displayed.
     */
    fun onText(input: String)
}
