/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.osfans.trime.R

/**
 * The standard chrome for every Compose screen in the app: a Material 3 large top app
 * bar that collapses as the content scrolls, drawn edge to edge under the status bar.
 *
 * The content lambda receives the scaffold padding; pass it straight to the scrolling
 * container's `contentPadding` (not as a `padding` modifier) so the content really
 * scrolls under the bars.
 *
 * ### Contextual mode
 *
 * Setting [contextual] switches to the Material 3 *contextual* top app bar a multi
 * select mode is supposed to wear: a pinned small bar in a tinted container whose
 * navigation icon is a ✕ that leaves the mode. Pages keep passing their "exit
 * selection" callback as [onNavigateUp]; one that needs something else entirely can
 * hand over a whole [navigationIcon].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimeScreen(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateUp: (() -> Unit)? = null,
    contextual: Boolean = false,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    // Both behaviours are created unconditionally, so switching in and out of
    // contextual mode never drops a remembered state.
    val collapsingBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val pinnedBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehavior = if (contextual) pinnedBehavior else collapsingBehavior

    // Leaving contextual mode brings the large title back expanded; otherwise it would
    // reappear stuck in whatever collapsed state it had when the mode was entered.
    LaunchedEffect(contextual) {
        if (!contextual) {
            collapsingBehavior.state.heightOffset = 0f
            collapsingBehavior.state.contentOffset = 0f
        }
    }

    val resolvedNavigationIcon: @Composable () -> Unit = navigationIcon ?: {
        if (onNavigateUp != null) {
            TopBarIconButton(
                icon = if (contextual) {
                    R.drawable.ic_baseline_close_24
                } else {
                    R.drawable.ic_baseline_arrow_back_24
                },
                contentDescription = if (contextual) {
                    stringResource(R.string.exit_selection)
                } else {
                    stringResource(androidx.appcompat.R.string.abc_action_bar_up_description)
                },
                onClick = onNavigateUp,
            )
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (contextual) {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = resolvedNavigationIcon,
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        scrolledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    scrollBehavior = scrollBehavior,
                )
            } else {
                LargeTopAppBar(
                    title = {
                        Text(
                            text = title,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = resolvedNavigationIcon,
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        content = content,
    )
}

/** An icon button for [TrimeScreen]'s `actions` slot, drawn from an app drawable. */
@Composable
fun TopBarIconButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
        )
    }
}
