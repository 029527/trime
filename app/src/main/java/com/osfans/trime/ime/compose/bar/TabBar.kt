/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.bar

import android.view.View
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.osfans.trime.ime.compose.theme.LocalImeColors
import com.osfans.trime.ime.compose.theme.LocalImeFonts
import com.osfans.trime.ime.compose.theme.LocalImeTokens

/**
 * What a [com.osfans.trime.ime.window.BoardWindow.BarBoardWindow] puts into the bar while it is
 * attached: its [title] and the view from `onCreateBarView`. [showTitle] false gives the whole
 * bar to that view, without back button or title.
 */
@Immutable
internal data class TabContent(
    val title: String,
    val showTitle: Boolean,
    val barView: View?,
)

/**
 * The bar of a board window: back button, title, then the window's own bar view. The view
 * stays a View; it is shown through an [EmbeddedView], so the windows keep their contract.
 */
@Composable
internal fun TabBar(
    content: TabContent,
    back: BarButtonSpec,
    buttonSizeDp: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalImeColors.current
    val fonts = LocalImeFonts.current
    val tokens = LocalImeTokens.current
    Row(modifier = modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (content.showTitle) {
            BarButton(spec = back, onClick = onBack, modifier = Modifier.size(buttonSizeDp.dp))
            BasicText(
                text = content.title,
                modifier = Modifier.padding(start = BarTokens.tabSpacing),
                style = TextStyle(fontFamily = fonts.candidate, fontSize = tokens.candidateTextSize, fontWeight = FontWeight.Bold),
                color = { colors.keyText },
                maxLines = 1,
                softWrap = false,
            )
        }
        content.barView?.let { view ->
            key(view) {
                EmbeddedView(
                    view = view,
                    modifier =
                    if (content.showTitle) {
                        Modifier.weight(1f).padding(start = BarTokens.tabSpacing).height(buttonSizeDp.dp)
                    } else {
                        Modifier.fillMaxWidth().height(buttonSizeDp.dp)
                    },
                    matchWidth = true,
                )
            }
        }
    }
}
