/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class KeyActionToken : Parcelable {
    data class Plain(val token: String) : KeyActionToken()
    data class Inline(val token: Token) : KeyActionToken() {
        @Parcelize
        data class Token(
            val commit: String?,
            val text: String?,
            val label: String?,
        ) : Parcelable
    }
}

/** A [KeyActionToken.Plain]: a preset key name (`ios_shift`), a keysym (`Escape`, `q`) or plain text. */
fun key(token: String): KeyActionToken = KeyActionToken.Plain(token)

/** Commits [commit] straight to the editor, bypassing Rime; the key shows [label]. */
fun commit(
    commit: String,
    label: String? = commit,
): KeyActionToken = KeyActionToken.Inline(KeyActionToken.Inline.Token(commit = commit, text = null, label = label))

/** Sends [text] through Rime key by key (`{Left}` style keysyms allowed); the key shows [label]. */
fun text(
    text: String,
    label: String? = text,
): KeyActionToken = KeyActionToken.Inline(KeyActionToken.Inline.Token(commit = null, text = text, label = label))
