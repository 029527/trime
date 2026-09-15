/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.userdict

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.osfans.trime.R
import com.osfans.trime.ui.main.settings.list.AddFloatingActionButton
import com.osfans.trime.ui.main.settings.list.ListEmptyHint
import com.osfans.trime.ui.main.settings.list.ListEntryAction
import com.osfans.trime.ui.main.settings.list.ListEntryMenuButton
import com.osfans.trime.ui.main.settings.list.ListEntryRow
import com.osfans.trime.ui.main.settings.list.ListScreen
import com.osfans.trime.ui.main.settings.list.plusBottom

/**
 * The user dictionaries Rime keeps for the deployed schemata. Each row carries the
 * same three actions as the old `PopupMenu` (back up to the sync dir, import a text
 * file into it, export it), and the floating action button restores a snapshot.
 *
 * All the file handling stays in [UserDictionaryFragment]; nothing about the on-disk
 * formats changes.
 */
@Composable
fun UserDictListScreen(
    entries: List<String>,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onRestore: () -> Unit,
    onBackup: (String) -> Unit,
    onImport: (String) -> Unit,
    onExport: (String) -> Unit,
) {
    ListScreen(
        title = stringResource(R.string.user_dictionary),
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        fab = {
            AddFloatingActionButton(
                label = stringResource(R.string.restore_from_file),
                onClick = onRestore,
                icon = null,
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding.plusBottom(88.dp)) {
            if (entries.isEmpty()) {
                item { ListEmptyHint(stringResource(R.string.user_dictionary_empty)) }
            }
            items(entries, key = { it }) { name ->
                ListEntryRow(
                    title = name,
                    trailing = {
                        ListEntryMenuButton(
                            listOf(
                                ListEntryAction(R.string.backup) { onBackup(name) },
                                ListEntryAction(R.string.import_) { onImport(name) },
                                ListEntryAction(R.string.export) { onExport(name) },
                            ),
                        )
                    },
                )
            }
        }
    }
}
