/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.composition

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.osfans.trime.ime.compose.theme.LocalImeColors
import com.osfans.trime.ime.compose.theme.LocalImeFonts
import com.osfans.trime.ime.compose.theme.LocalImeTokens

/** Where the preedit is shown; decides its look, never its behaviour. */
enum class PreeditStyle {
    /** A pill above the keyboard, its top corners rounded. */
    Floating,

    /** A small line of text at the top of a floating keyboard's candidate bar. */
    Embedded,
}

/**
 * The preedit: rime's composition with the selected segment highlighted and, when the
 * caret is not at the end, a caret. Tapping moves the caret there; dragging moves it to
 * where the finger is lifted, with the caret following the finger meanwhile.
 *
 * Emits nothing when [preedit] is `null`, so the host view collapses to zero size.
 *
 * @param onMoveCursor receives the librime caret position, see [PreeditText.caretPositionFor].
 */
@Composable
fun PreeditBar(
    preedit: PreeditText?,
    style: PreeditStyle,
    onMoveCursor: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (preedit == null) return
    val colors = LocalImeColors.current
    val tokens = LocalImeTokens.current
    val fonts = LocalImeFonts.current

    val baseColor: Color
    val textSize: TextUnit
    val container: Modifier
    when (style) {
        PreeditStyle.Floating -> {
            baseColor = colors.preeditText
            textSize = if (preedit.isT9) tokens.preeditT9TextSize else tokens.preeditTextSize
            val radius = tokens.preeditCornerRadius
            container =
                Modifier
                    .background(colors.preeditBack, RoundedCornerShape(topStart = radius, topEnd = radius))
                    .padding(horizontal = tokens.preeditHorizontalPadding, vertical = tokens.preeditVerticalPadding)
        }
        PreeditStyle.Embedded -> {
            // like iOS: plain small pinyin above the candidates, no pill
            baseColor = colors.candidateComment
            textSize = tokens.candidateCommentTextSize
            container = Modifier.padding(horizontal = 4.dp)
        }
    }

    val text = remember(preedit, colors.preeditHighlightedText) {
        preedit.annotated(colors.preeditHighlightedText)
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // offset under the finger while pressed, -1 otherwise
    var pressedOffset by remember { mutableIntStateOf(-1) }
    val currentPreedit by rememberUpdatedState(preedit)
    val currentOnMoveCursor by rememberUpdatedState(onMoveCursor)
    val caretColor = colors.preeditHighlightedText

    BasicText(
        text = text,
        style = TextStyle(color = baseColor, fontSize = textSize, fontFamily = fonts.preedit),
        // the embedded line has a fixed height; above the keyboard a long preedit wraps
        maxLines = if (style == PreeditStyle.Embedded) 1 else Int.MAX_VALUE,
        onTextLayout = { layout = it },
        modifier =
        modifier
            .then(container)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun offsetAt(position: Offset) = layout?.getOffsetForPosition(position) ?: -1
                    pressedOffset = offsetAt(down.position)
                    down.consume()
                    var lifted = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            lifted = true
                            break
                        }
                        pressedOffset = offsetAt(change.position)
                        change.consume()
                    }
                    val target = pressedOffset
                    pressedOffset = -1
                    if (lifted && target >= 0) {
                        currentOnMoveCursor(PreeditText.caretPositionFor(currentPreedit.text, target))
                    }
                }
            }.drawWithContent {
                drawContent()
                val result = layout ?: return@drawWithContent
                val at = if (pressedOffset >= 0) pressedOffset else preedit.caret
                if (at < 0 || at > result.layoutInput.text.length) return@drawWithContent
                val rect = result.getCursorRect(at)
                val width = 1.5.dp.toPx()
                drawRect(
                    color = caretColor,
                    topLeft = Offset(rect.left - width / 2, rect.top),
                    size = Size(width, rect.height),
                )
            },
    )
}

private fun PreeditText.annotated(highlight: Color): AnnotatedString = buildAnnotatedString {
    if (!hasSelection) {
        append(text)
        return@buildAnnotatedString
    }
    append(text, 0, selectionStart)
    withStyle(SpanStyle(color = highlight)) { append(text, selectionStart, selectionEnd) }
    append(text, selectionEnd, text.length)
}
