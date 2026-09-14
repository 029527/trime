/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.session

import androidx.compose.runtime.Immutable
import com.osfans.trime.core.Candidates
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.core.SchemaItem
import com.osfans.trime.core.StatusProto
import com.osfans.trime.ime.broadcast.EnterKeyDisplayDelegate

/**
 * Everything the keyboard UI renders, as one immutable snapshot.
 *
 * Before this existed, the same facts lived in a dozen places: `Rime`'s cached fields,
 * `InputView`, and every [com.osfans.trime.ime.broadcast.InputBroadcastReceiver] that kept
 * its own copy of the last composition / candidate list. New UI (Compose) reads this
 * and nothing else; old receivers keep working until they are migrated.
 *
 * The engine fields ([composition], [candidates], [status], [schema], [options], [hasMenu],
 * [paging]) change together, once per engine response, see [DefaultInputSession.onRimeMessage].
 */
@Immutable
data class InputState(
    /**
     * What the composition bar shows. It is blank while the user is composing in two cases:
     * the preedit goes inline into the app's text field, or the floating candidates window
     * owns the composition (`PopupCandidatesMode.ALWAYS_SHOW`). Use [isComposing] to ask
     * whether there is input at all. For a second after switching ascii mode it holds the
     * mode tip ("En", the schema name) instead of real input.
     */
    val composition: CompositionProto = CompositionProto(),
    /**
     * The candidate bar's list: the head of the whole candidate list (at most 16);
     * [Candidates.Bulk.total] is -1 when there may be more, fetch those with
     * [InputSession.loadCandidates]. Always empty in paging mode, where the floating
     * candidates window renders the menu instead.
     */
    val candidates: Candidates.Bulk = Candidates.Bulk(),
    val status: StatusProto = StatusProto(),
    /** The active schema, null until the engine reports one. */
    val schema: SchemaItem? = null,
    /**
     * Runtime options seen so far (`ascii_mode`, `_liquid_keyboard`, ...). Starts with the
     * options [StatusProto] reflects; others appear once librime notifies a change.
     */
    val options: Map<String, Boolean> = emptyMap(),
    val editor: EditorState = EditorState(),
    /**
     * Whether the engine has candidates, in either mode. Unlike [candidates] this is also
     * true in paging mode; keys with a `has_menu` behavior switch on it.
     */
    val hasMenu: Boolean = false,
    /** Paging mode only: the menu is past its first page. Always false in bulk mode. */
    val paging: Boolean = false,
    val enterKey: EnterKeyState = EnterKeyState(),
) {
    /**
     * Whether librime holds unfinished input. Taken from [status], not [composition],
     * which is blank whenever the preedit is displayed elsewhere.
     */
    val isComposing: Boolean get() = status.isComposing
}

/**
 * What we know about the app's text field. A copy of the interesting parts of
 * [android.view.inputmethod.EditorInfo], never the object itself: the framework
 * reuses and mutates it.
 */
@Immutable
data class EditorState(
    val packageName: String = "",
    val inputType: Int = 0,
    val imeOptions: Int = 0,
    val selectionStart: Int = -1,
    val selectionEnd: Int = -1,
)

/**
 * How the enter key looks for the current text field, as resolved by
 * [EnterKeyDisplayDelegate] from the editor's IME action and the theme's enter labels.
 */
@Immutable
data class EnterKeyState(
    val label: String = EnterKeyDisplayDelegate.DEFAULT_LABEL,
    /** The field asks for a go / search / send / done action, which themes may highlight. */
    val isPrimaryAction: Boolean = false,
)
