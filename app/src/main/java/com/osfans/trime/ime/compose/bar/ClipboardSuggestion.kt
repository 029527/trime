/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.bar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.osfans.trime.R
import com.osfans.trime.ime.compose.theme.LocalImeColors
import com.osfans.trime.ime.compose.theme.LocalImeFonts
import com.osfans.trime.ime.compose.theme.LocalImeTokens

/**
 * The just-copied text offered in place of the toolbar: tap commits it, long press opens it
 * for editing, the cross dismisses it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ClipboardSuggestion(
    text: String,
    onCommit: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalImeColors.current
    val fonts = LocalImeFonts.current
    val tokens = LocalImeTokens.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(BarTokens.clipboardCornerRadius)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Row(
            modifier =
            Modifier
                .fillMaxHeight()
                .padding(vertical = BarTokens.clipboardVerticalMargin)
                .background(if (pressed) colors.highlightedCandidateBack else Color.Transparent, shape)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    hapticFeedbackEnabled = false,
                    onLongClick = onEdit,
                    onClick = onCommit,
                ).padding(horizontal = BarTokens.clipboardSpacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BarTokens.clipboardSpacing),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_clipboard_24),
                contentDescription = null,
                modifier = Modifier.size(BarTokens.clipboardIconSize),
                colorFilter = ColorFilter.tint(colors.candidateText),
            )
            BasicText(
                text = text,
                modifier = Modifier.weight(1f, fill = false).widthIn(max = BarTokens.clipboardMaxTextWidth),
                style = TextStyle(fontFamily = fonts.candidate, fontSize = tokens.preeditTextSize),
                color = { colors.candidateText },
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Image(
                painter = painterResource(R.drawable.ic_outline_cancel_24),
                contentDescription = null,
                modifier =
                Modifier
                    .size(BarTokens.clipboardIconSize)
                    .clickable(interactionSource = null, indication = null, onClick = onDismiss),
                colorFilter = ColorFilter.tint(colors.candidateText),
            )
        }
    }
}
