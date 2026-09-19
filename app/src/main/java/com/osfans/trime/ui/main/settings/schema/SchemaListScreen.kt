/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.schema

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.osfans.trime.R
import com.osfans.trime.core.SchemaItem
import com.osfans.trime.data.schema.FixedSchemata
import com.osfans.trime.ui.compose.TopBarIconButton
import com.osfans.trime.ui.main.settings.list.AddFloatingActionButton
import com.osfans.trime.ui.main.settings.list.ListEmptyHint
import com.osfans.trime.ui.main.settings.list.ListEntryRow
import com.osfans.trime.ui.main.settings.list.ListLoading
import com.osfans.trime.ui.main.settings.list.ListScreen
import com.osfans.trime.ui.main.settings.list.plusBottom

/**
 * Picks which of the deployed schemata are offered by the keyboard.
 *
 * Selecting entries used to be a mode toggled from an "edit" button on the activity
 * toolbar; it is now a Material 3 contextual top bar — long pressing a row (or the
 * edit action) enters it, the title turns into the selection count, the up arrow and
 * the system back gesture leave it, and the only action left is "delete".
 *
 * The list is a plain in-memory list here; writing it back to Rime stays in
 * [SchemaListFragment] and is unchanged.
 */
@Composable
fun SchemaListScreen(
    loading: Boolean,
    entries: List<SchemaItem>,
    addable: List<SchemaItem>,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onAdd: (List<SchemaItem>) -> Unit,
    onRemove: (List<SchemaItem>) -> Unit,
) {
    val selected = remember { mutableStateListOf<String>() }
    var selecting by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun exitSelection() {
        selecting = false
        selected.clear()
    }

    // Entries can disappear while selecting (undo of an add), so never keep a stale id.
    val liveSelection = selected.filter { id -> entries.any { it.id == id } }

    BackHandler(enabled = selecting) { exitSelection() }

    ListScreen(
        title = if (selecting) {
            stringResource(R.string.n_selected, liveSelection.size)
        } else {
            stringResource(R.string.schemata)
        },
        snackbarHostState = snackbarHostState,
        onNavigateUp = if (selecting) ({ exitSelection() }) else onNavigateUp,
        contextual = selecting,
        actions = {
            if (selecting) {
                TopBarIconButton(
                    icon = R.drawable.ic_baseline_delete_24,
                    contentDescription = stringResource(R.string.delete),
                    enabled = liveSelection.isNotEmpty(),
                    onClick = {
                        onRemove(entries.filter { it.id in liveSelection })
                        exitSelection()
                    },
                )
            } else if (entries.isNotEmpty()) {
                // enters the multi-select mode whose only action is removing, so say that
                TopBarIconButton(
                    icon = R.drawable.ic_baseline_delete_sweep_24,
                    contentDescription = stringResource(R.string.remove_schemata),
                    onClick = { selecting = true },
                )
            }
        },
        fab = if (!selecting && addable.isNotEmpty()) {
            {
                AddFloatingActionButton(
                    label = stringResource(R.string.enable_schemata),
                    onClick = { showAddDialog = true },
                )
            }
        } else {
            null
        },
    ) { padding ->
        when {
            loading -> ListLoading(Modifier.padding(padding))
            entries.isEmpty() -> LazyColumn(contentPadding = padding.plusBottom(88.dp)) {
                item { ListEmptyHint(stringResource(R.string.schemata_empty)) }
            }
            else -> LazyColumn(contentPadding = padding.plusBottom(88.dp)) {
                items(entries, key = { it.id }) { item ->
                    ListEntryRow(
                        title = FixedSchemata.getDisplayName(item.id, item.name),
                        selected = if (selecting) item.id in liveSelection else null,
                        onClick = {
                            if (selecting) {
                                if (item.id in selected) selected.remove(item.id) else selected.add(item.id)
                            }
                        },
                        onLongClick = {
                            if (!selecting) {
                                selecting = true
                                selected.clear()
                            }
                            if (item.id !in selected) selected.add(item.id)
                        },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        SchemaPickerDialog(
            candidates = addable,
            onDismiss = { showAddDialog = false },
            onConfirm = {
                showAddDialog = false
                onAdd(it)
            },
        )
    }
}

/** The Material 3 replacement for the old multi-choice `AlertDialog`. */
@Composable
private fun SchemaPickerDialog(
    candidates: List<SchemaItem>,
    onDismiss: () -> Unit,
    onConfirm: (List<SchemaItem>) -> Unit,
) {
    val checked = remember(candidates) { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enable_schemata)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                candidates.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = item.id in checked,
                                onValueChange = { on ->
                                    if (on) checked.add(item.id) else checked.remove(item.id)
                                },
                            ).padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = item.id in checked, onCheckedChange = null)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = FixedSchemata.getDisplayName(item.id, item.name),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(candidates.filter { it.id in checked }) },
                enabled = checked.isNotEmpty(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
