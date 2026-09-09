/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.list

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.osfans.trime.R
import com.osfans.trime.ui.compose.TrimeScreen

/**
 * Chrome shared by the three list management pages (schemata, user dictionary, hot
 * words). It is deliberately local to those pages rather than part of `ui/compose`:
 * [TrimeScreen] has no floating action button or snackbar slot, and only these pages
 * need one.
 *
 * The floating action button and the snackbar host sit in a bottom-aligned column, so
 * a showing snackbar pushes the button up on its own — no measuring of the snackbar,
 * which is what the old `CoordinatorLayout` behaviour had to do by hand.
 */
@Composable
fun ListScreen(
    title: String,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onNavigateUp: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    fab: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    TrimeScreen(
        title = title,
        modifier = modifier,
        onNavigateUp = onNavigateUp,
        actions = actions,
    ) { padding ->
        val direction = LocalLayoutDirection.current
        Box(Modifier.fillMaxSize()) {
            content(padding)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = padding.calculateStartPadding(direction),
                        end = padding.calculateEndPadding(direction),
                        bottom = padding.calculateBottomPadding(),
                    ),
            ) {
                if (fab != null) {
                    Box(
                        Modifier
                            .align(Alignment.End)
                            .padding(end = 16.dp, bottom = 16.dp),
                    ) {
                        fab()
                    }
                }
                SnackbarHost(snackbarHostState)
            }
        }
    }
}

/** The list's own "add" button, shared by all three pages. */
@Composable
fun AddFloatingActionButton(
    contentDescription: String,
    onClick: () -> Unit,
) {
    FloatingActionButton(onClick = onClick) {
        Icon(
            painter = painterResource(R.drawable.ic_baseline_add_24),
            contentDescription = contentDescription,
        )
    }
}

/**
 * Extra room below the last item so it can scroll clear of the floating action
 * button, keeping the window insets the scaffold handed down.
 */
@Composable
fun PaddingValues.plusBottom(extra: Dp): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        top = calculateTopPadding(),
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding() + extra,
    )
}

/** One row of a management list: title, optional summary, optional trailing control. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListEntryRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    selected: Boolean? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                },
            ).padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
            .heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected != null) {
            Checkbox(checked = selected, onCheckedChange = null)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!summary.isNullOrEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        } else {
            Spacer(Modifier.width(16.dp))
        }
    }
}

/** One entry of an item's overflow menu. */
class ListEntryAction(
    @StringRes val title: Int,
    val onClick: () -> Unit,
)

/** The per-row overflow button, the Material 3 stand-in for the old `PopupMenu`. */
@Composable
fun ListEntryMenuButton(actions: List<ListEntryAction>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_more_horiz_24),
                contentDescription = stringResource(
                    androidx.appcompat.R.string.abc_action_menu_overflow_description,
                ),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(action.title)) },
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

/** Shown in place of the list while the page is still loading its content. */
@Composable
fun ListLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Shown in place of the list when there is nothing in it yet. */
@Composable
fun ListEmptyHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
