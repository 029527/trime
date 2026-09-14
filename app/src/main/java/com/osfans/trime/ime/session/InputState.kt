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

/**
 * Everything the keyboard UI renders, as one immutable snapshot.
 *
 * Before this existed, the same facts lived in a dozen places: `Rime`'s cached fields,
 * `InputView`, and every [com.osfans.trime.ime.broadcast.InputBroadcastReceiver] that kept
 * its own copy of the last composition / candidate list. New UI (Compose) reads this
 * and nothing else; old receivers keep working until they are migrated.
 */
@Immutable
data class InputState(
    val composition: CompositionProto = CompositionProto(),
    /** The candidate bar's list. [Candidates.Bulk.total] is -1 when librime does not know. */
    val candidates: Candidates.Bulk = Candidates.Bulk(),
    val status: StatusProto = StatusProto(),
    val schema: SchemaItem? = null,
    /** Runtime options seen so far (`ascii_mode`, `_liquid_keyboard`, ...). */
    val options: Map<String, Boolean> = emptyMap(),
    val editor: EditorState = EditorState(),
) {
    val isComposing: Boolean get() = composition.length > 0
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
