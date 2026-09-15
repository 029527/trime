/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.hotwords

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.osfans.trime.R
import com.osfans.trime.data.hotwords.HotWord
import com.osfans.trime.data.hotwords.HotWordManager
import com.osfans.trime.ui.compose.preference.LoadingDialog
import com.osfans.trime.ui.main.settings.list.AddFloatingActionButton
import com.osfans.trime.ui.main.settings.list.ListEmptyHint
import com.osfans.trime.ui.main.settings.list.ListEntryAction
import com.osfans.trime.ui.main.settings.list.ListEntryMenuButton
import com.osfans.trime.ui.main.settings.list.ListEntryRow
import com.osfans.trime.ui.main.settings.list.ListScreen
import com.osfans.trime.ui.main.settings.list.plusBottom

/**
 * The words the user wants ahead of the dictionaries. Tapping a row edits it, the
 * row's overflow menu edits or deletes it, and the floating action button adds one.
 *
 * Persisting and redeploying stays in [HotWordFragment]; the store format is
 * untouched.
 */
@Composable
fun HotWordListScreen(
    entries: List<HotWord>,
    loading: Boolean,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onSave: (existing: HotWord?, word: HotWord) -> Unit,
    onDelete: (HotWord) -> Unit,
) {
    // `null` = closed; an EditTarget holding `null` = "add a new one".
    var editing by remember { mutableStateOf<EditTarget?>(null) }

    ListScreen(
        title = stringResource(R.string.hot_words),
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        fab = {
            AddFloatingActionButton(
                label = stringResource(R.string.hot_word_add),
                onClick = { editing = EditTarget(null) },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding.plusBottom(88.dp)) {
            if (entries.isEmpty()) {
                item { ListEmptyHint(stringResource(R.string.hot_words_empty)) }
            }
            items(entries) { word ->
                val code = HotWordManager.sanitizeCode(word.effectiveCode)
                ListEntryRow(
                    title = word.text,
                    summary = stringResource(
                        R.string.hot_word_entry_summary,
                        code.ifEmpty { "-" },
                        HotWordManager.t9Code(code).ifEmpty { "-" },
                        word.weight,
                    ),
                    onClick = { editing = EditTarget(word) },
                    trailing = {
                        ListEntryMenuButton(
                            listOf(
                                ListEntryAction(R.string.hot_word_edit) { editing = EditTarget(word) },
                                ListEntryAction(R.string.hot_word_delete) { onDelete(word) },
                            ),
                        )
                    },
                )
            }
        }
    }

    if (loading) {
        LoadingDialog(R.string.hot_word_deploying)
    }

    editing?.let { target ->
        HotWordEditDialog(
            existing = target.value,
            onDismiss = { editing = null },
            onConfirm = { word ->
                editing = null
                onSave(target.value, word)
            },
        )
    }
}

/** Distinguishes "no dialog" from "a dialog for a new word". */
private class EditTarget(
    val value: HotWord?,
)

@Composable
private fun HotWordEditDialog(
    existing: HotWord?,
    onDismiss: () -> Unit,
    onConfirm: (HotWord) -> Unit,
) {
    var text by remember { mutableStateOf(existing?.text.orEmpty()) }
    var code by remember { mutableStateOf(existing?.code.orEmpty()) }
    var weight by remember { mutableStateOf(existing?.weight?.toString() ?: "1") }
    var error by remember { mutableStateOf<Int?>(null) }

    val cleanText = HotWordManager.sanitizeText(text)
    val cleanCode = HotWordManager.sanitizeCode(code)
    val textInvalid = cleanText.isEmpty()
    val codeInvalid = !textInvalid && cleanCode.isEmpty() && HotWordManager.defaultCode(cleanText).isEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (existing == null) R.string.hot_word_add else R.string.hot_word_edit))
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.hot_word_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.hot_word_text_hint)) },
                    singleLine = true,
                    isError = error == R.string.hot_word_text_empty,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.hot_word_code_hint)) },
                    singleLine = true,
                    isError = error == R.string.hot_word_code_empty,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { new -> weight = new.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.hot_word_weight_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                error?.let {
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = when {
                        textInvalid -> R.string.hot_word_text_empty
                        codeInvalid -> R.string.hot_word_code_empty
                        else -> null
                    }
                    if (error == null) {
                        onConfirm(
                            HotWord(
                                text = cleanText,
                                code = cleanCode,
                                weight = (weight.toIntOrNull() ?: 1).coerceIn(0, 1000),
                            ),
                        )
                    }
                },
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
