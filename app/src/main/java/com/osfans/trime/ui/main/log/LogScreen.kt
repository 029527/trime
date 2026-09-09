/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.log

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osfans.trime.R
import com.osfans.trime.ui.compose.TopBarIconButton
import com.osfans.trime.ui.compose.TrimeScreen
import kotlinx.coroutines.launch

/**
 * One line of the log. [level] is the logcat severity letter (`V`/`D`/`I`/`W`/`E`/`F`)
 * that decides its colour; [PLAIN_LEVEL] means "text we appended ourselves", such as a
 * crash stack trace, which is drawn in the normal foreground colour.
 */
data class LogLine(
    val text: String,
    val level: Char = PLAIN_LEVEL,
) {
    companion object {
        const val PLAIN_LEVEL = ' '

        /**
         * `logcat -v time` puts the severity letter right after the timestamp. Short or
         * malformed lines simply have no level (the old view crashed on them).
         */
        fun ofLogcat(line: String) = LogLine(line, line.getOrNull(19) ?: PLAIN_LEVEL)
    }
}

/**
 * The log viewer, in all three of its flavours (real-time logcat, a crash report, a
 * deploy failure). Lines never wrap: the whole list scrolls sideways instead, which is
 * what the old `HorizontalScrollView` wrapping a `RecyclerView` did.
 */
@Composable
fun LogScreen(
    title: String,
    lines: List<LogLine>,
    onNavigateUp: () -> Unit,
    onClear: (() -> Unit)?,
    onCopy: (() -> Unit)?,
    onExport: () -> Unit,
) {
    val listState = rememberLazyListState()
    val horizontalScroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    // "Follow the tail" only while the tail is actually on screen, so scrolling back to
    // read something is not yanked away by the next line that arrives.
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 1
        }
    }
    LaunchedEffect(lines.size) {
        if (atBottom && lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    TrimeScreen(
        title = title,
        onNavigateUp = onNavigateUp,
        actions = {
            if (onClear != null) {
                TopBarIconButton(
                    icon = R.drawable.ic_baseline_delete_sweep_24,
                    contentDescription = stringResource(R.string.clear),
                    onClick = onClear,
                )
            }
            if (onCopy != null) {
                TopBarIconButton(
                    icon = R.drawable.ic_baseline_content_copy_24,
                    contentDescription = stringResource(android.R.string.copy),
                    onClick = onCopy,
                )
            }
            TopBarIconButton(
                icon = R.drawable.ic_baseline_share_24,
                contentDescription = stringResource(R.string.export),
                onClick = onExport,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = padding,
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScroll),
            ) {
                itemsIndexed(lines) { _, line ->
                    Text(
                        text = line.text,
                        color = line.color(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            AnimatedVisibility(
                visible = !atBottom,
                enter = scaleIn(),
                exit = scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(padding.withoutTop())
                    .padding(16.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        // Jumping is instant, matching the old `scrollToPosition`.
                        scope.launch {
                            if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_expand_more_24),
                        contentDescription = stringResource(R.string.scroll_to_bottom),
                    )
                }
            }
        }
    }
}

/** The scaffold insets minus the top one, which the list already consumes. */
@Composable
private fun PaddingValues.withoutTop(): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding(),
    )
}

@Composable
private fun LogLine.color(): Color = when (level) {
    'V', 'D' -> colorResource(R.color.grey_700)
    'I' -> colorResource(R.color.blue_500)
    'W' -> colorResource(R.color.yellow_800)
    'E' -> colorResource(R.color.red_400)
    'F' -> colorResource(R.color.red_A700)
    LogLine.PLAIN_LEVEL -> MaterialTheme.colorScheme.onSurface
    else -> colorResource(R.color.colorPrimary)
}
