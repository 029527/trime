/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.edit

/** A cursor key of the edit panel, named by the keysym (and preset key) it sends. */
enum class EditKey(
    val keysym: String,
) {
    Up("Up"),
    Down("Down"),
    Left("Left"),
    Right("Right"),
    LineStart("Home"),
    LineEnd("End"),

    /** Completion in terminals, shells and remote desktops, where arrows and digits may not get through but Tab does. */
    Tab("Tab"),
    ;

    /** Held arrows repeat like backspace; jumping to a line end twice goes nowhere new. */
    val repeatable: Boolean get() = this != LineStart && this != LineEnd && this != Tab

    /**
     * The key action token to send. With [selecting] on it carries Shift, which is how editors
     * extend a selection from the keyboard (TextView, Compose text fields, WebView all read it).
     *
     * While a composition is in progress the key stays plain, so it moves the caret inside the
     * preedit exactly as the keyboard's arrow keys do, instead of selecting app text behind it.
     * Tab never carries Shift: Shift+Tab goes back a completion or a field instead of selecting.
     */
    fun token(
        selecting: Boolean,
        composing: Boolean,
    ): String = if (selecting && !composing && this != Tab) "Shift+$keysym" else keysym
}

/** A clipboard command of the edit panel, sent as the preset key of the same meaning. */
enum class EditCommand(
    val presetKey: String,
) {
    SelectAll("select_all"),
    Cut("cut"),
    Copy("copy"),
    Paste("paste"),
    ;

    /**
     * Select mode once the command ran: on after select all, so the arrows go on to adjust that
     * selection; off after the text has been cut, copied or pasted, so the next arrow just moves.
     */
    val selectingAfter: Boolean get() = this == SelectAll
}
