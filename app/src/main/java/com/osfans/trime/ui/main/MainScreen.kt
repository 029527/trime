/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.osfans.trime.R
import com.osfans.trime.ui.compose.TopBarIconButton
import com.osfans.trime.ui.compose.TrimeScreen
import com.osfans.trime.ui.compose.preference.PreferenceCard
import com.osfans.trime.ui.compose.preference.PreferenceRow

private class HomeEntry(
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    val route: NavigationRoute,
)

private val DataEntries = listOf(
    HomeEntry(R.string.schemata, R.drawable.ic_round_view_list_24, NavigationRoute.SchemaList),
    HomeEntry(R.string.user_dictionary, R.drawable.ic_baseline_book_24, NavigationRoute.UserDict),
    HomeEntry(R.string.hot_words, R.drawable.ic_baseline_star_24, NavigationRoute.HotWords),
    HomeEntry(R.string.profile, R.drawable.ic_baseline_snippet_folder_24, NavigationRoute.Profile),
)

private val SettingsEntries = listOf(
    HomeEntry(R.string.general, R.drawable.ic_baseline_tune_24, NavigationRoute.General),
    HomeEntry(R.string.virtual_keyboard, R.drawable.ic_baseline_keyboard_24, NavigationRoute.VirtualKeyboard),
    HomeEntry(R.string.candidates_window, R.drawable.ic_baseline_list_alt_24, NavigationRoute.CandidatesWindow),
    HomeEntry(R.string.theme, R.drawable.ic_baseline_color_lens_24, NavigationRoute.Theme),
    HomeEntry(R.string.clipboard, R.drawable.ic_clipboard_24, NavigationRoute.Clipboard),
    HomeEntry(R.string.advanced, R.drawable.ic_baseline_more_horiz_24, NavigationRoute.Advanced),
)

@Composable
fun MainScreen(
    onNavigate: (NavigationRoute) -> Unit,
    onDeploy: () -> Unit,
    onTestInput: () -> Unit,
) {
    TrimeScreen(
        title = stringResource(R.string.trime_app_name),
        actions = {
            TopBarIconButton(
                icon = R.drawable.ic_baseline_refresh_reversed_24,
                contentDescription = stringResource(R.string.deploy),
                onClick = onDeploy,
            )
            TopBarIconButton(
                icon = R.drawable.ic_baseline_keyboard_24,
                contentDescription = stringResource(R.string.test_input),
                onClick = onTestInput,
            )
            OverflowMenu(onNavigate)
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                EntryCard(DataEntries, onNavigate)
            }
            item {
                Spacer(Modifier.height(16.dp))
                EntryCard(SettingsEntries, onNavigate)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EntryCard(
    entries: List<HomeEntry>,
    onNavigate: (NavigationRoute) -> Unit,
) {
    PreferenceCard {
        entries.forEach { entry ->
            PreferenceRow(
                title = stringResource(entry.title),
                icon = entry.icon,
                horizontalPadding = 20.dp,
                onClick = { onNavigate(entry.route) },
            )
        }
    }
}

@Composable
private fun OverflowMenu(onNavigate: (NavigationRoute) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TopBarIconButton(
        icon = R.drawable.ic_baseline_more_horiz_24,
        contentDescription = stringResource(
            androidx.appcompat.R.string.abc_action_menu_overflow_description,
        ),
        onClick = { expanded = true },
    )
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.developer)) },
            onClick = {
                expanded = false
                onNavigate(NavigationRoute.Developer)
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.about)) },
            onClick = {
                expanded = false
                onNavigate(NavigationRoute.About)
            },
        )
    }
}
