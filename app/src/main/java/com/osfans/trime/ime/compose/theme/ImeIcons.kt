/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.KeyboardTab
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.FirstPage
import androidx.compose.material.icons.outlined.HighlightAlt
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LastPage
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Every glyph the keyboard draws, in one place: Material Icons, outlined style.
 *
 * Keys and toolbar buttons name their glyph as `ic@<name>` (the name is a Community Material
 * Design Icons name, kept so the key layouts stay unchanged). [vector] maps those names to the
 * Material glyph drawn in their place; a name missing here still draws its Community Material
 * glyph, so an unmapped name is ugly, never blank.
 *
 * | `ic@` name | Glyph | Used by |
 * |---|---|---|
 * | `backspace-outline` | Backspace (auto-mirrored) | backspace key |
 * | `apple-keyboard-shift` | [Shift], an outlined arrow drawn here | shift key |
 * | `keyboard-outline` | Keyboard | bottom row: schema menu |
 * | `emoticon-outline` | EmojiEmotions | emoji key |
 * | `microphone-outline` | Mic | voice key |
 * | `select-all` | SelectAll | hint on `a` |
 * | `content-cut` / `content-copy` / `content-paste` | ContentCut / ContentCopy / ContentPaste | hints on `x` `c` `v` |
 * | `menu-down` | KeyboardArrowDown | toolbar: hide keyboard |
 * | `cursor-text` | Edit | toolbar and bottom row: edit panel |
 * | `clipboard-outline` | ContentPaste | toolbar and bottom row: clipboard |
 * | `arrow-left` | ArrowBack (auto-mirrored) | back button of a panel's bar |
 */
object ImeIcons {
    /**
     * Shift as an outlined up arrow on a stem, stroked like the Material outlined set. Material
     * Icons has no keyboard shift glyph; `KeyboardCapslock` carries an underline meaning locked.
     */
    val Shift: ImageVector by lazy {
        ImageVector
            .Builder(name = "Shift", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                path(
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(12f, 4f)
                    lineTo(19.5f, 12.25f)
                    horizontalLineTo(15.25f)
                    verticalLineTo(19.5f)
                    horizontalLineTo(8.75f)
                    verticalLineTo(12.25f)
                    horizontalLineTo(4.5f)
                    close()
                }
            }.build()
    }

    // edit panel
    val EditUp get() = Icons.Outlined.KeyboardArrowUp
    val EditDown get() = Icons.Outlined.KeyboardArrowDown
    val EditLeft get() = Icons.AutoMirrored.Outlined.KeyboardArrowLeft
    val EditRight get() = Icons.AutoMirrored.Outlined.KeyboardArrowRight
    val EditSelect get() = Icons.Outlined.HighlightAlt
    val EditLineStart get() = Icons.Outlined.FirstPage
    val EditLineEnd get() = Icons.Outlined.LastPage
    val EditTab get() = Icons.AutoMirrored.Outlined.KeyboardTab
    val SelectAll get() = Icons.Outlined.SelectAll
    val Cut get() = Icons.Outlined.ContentCut
    val Copy get() = Icons.Outlined.ContentCopy
    val Paste get() = Icons.Outlined.ContentPaste

    /** The glyph for an `ic@` name (with or without the prefix, `-` or `_` alike), or null if unmapped. */
    fun vector(name: String): ImageVector? = when (name.removePrefix(PREFIX).replace('_', '-')) {
        "backspace-outline" -> Icons.AutoMirrored.Outlined.Backspace
        "apple-keyboard-shift" -> Shift
        "keyboard-outline" -> Icons.Outlined.Keyboard
        "emoticon-outline" -> Icons.Outlined.EmojiEmotions
        "microphone-outline" -> Icons.Outlined.Mic
        "select-all" -> SelectAll
        "content-cut" -> Cut
        "content-copy" -> Copy
        "content-paste", "clipboard-outline" -> Paste
        "menu-down" -> Icons.Outlined.KeyboardArrowDown
        "cursor-text" -> Icons.Outlined.Edit
        "arrow-left" -> Icons.AutoMirrored.Outlined.ArrowBack
        else -> null
    }

    private const val PREFIX = "ic@"
}
