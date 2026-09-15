/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.osfans.trime.ui.compose.preference.PreferenceCategoryHeader
import com.osfans.trime.ui.compose.preference.PreferenceRow

private class HomeEntry(
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    val route: NavigationRoute,
)

private class HomeGroup(
    @StringRes val title: Int,
    val entries: List<HomeEntry>,
)

/**
 * Every page belongs to exactly one group: what typing does, what the keyboard looks like,
 * or the user's data. Which setting sits on which page is in `settings/SettingsPages.kt`.
 */
private val HomeGroups = listOf(
    HomeGroup(
        R.string.settings_group_input,
        listOf(
            HomeEntry(R.string.general, R.drawable.ic_baseline_tune_24, NavigationRoute.General),
            HomeEntry(R.string.keys_and_gestures, R.drawable.ic_baseline_keyboard_24, NavigationRoute.VirtualKeyboard),
            HomeEntry(R.string.key_feedback, R.drawable.ic_baseline_vibration_24, NavigationRoute.KeyFeedback),
            HomeEntry(R.string.clipboard, R.drawable.ic_clipboard_24, NavigationRoute.Clipboard),
            HomeEntry(R.string.voice_input, R.drawable.ic_baseline_mic_24, NavigationRoute.VoiceInput),
        ),
    ),
    HomeGroup(
        R.string.settings_group_appearance,
        listOf(
            HomeEntry(R.string.theme, R.drawable.ic_baseline_color_lens_24, NavigationRoute.Theme),
            HomeEntry(R.string.keyboard_ui, R.drawable.ic_baseline_view_comfy_24, NavigationRoute.KeyboardUi),
        ),
    ),
    HomeGroup(
        R.string.settings_group_data,
        listOf(
            HomeEntry(R.string.schemata, R.drawable.ic_round_view_list_24, NavigationRoute.SchemaList),
            HomeEntry(R.string.user_dictionary, R.drawable.ic_baseline_book_24, NavigationRoute.UserDict),
            HomeEntry(R.string.hot_words, R.drawable.ic_baseline_star_24, NavigationRoute.HotWords),
            HomeEntry(R.string.profile, R.drawable.ic_baseline_snippet_folder_24, NavigationRoute.Profile),
        ),
    ),
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
            items(HomeGroups) { group ->
                EntryCard(group, onNavigate)
            }
            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EntryCard(
    group: HomeGroup,
    onNavigate: (NavigationRoute) -> Unit,
) {
    // the header's own start padding lines its text up with the row titles inside the card
    PreferenceCategoryHeader(stringResource(group.title), Modifier.padding(start = 16.dp))
    PreferenceCard {
        group.entries.forEach { entry ->
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
