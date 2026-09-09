/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.osfans.trime.R
import com.osfans.trime.ui.compose.TrimeScreen
import com.osfans.trime.ui.compose.preference.PreferenceRow

/**
 * The developer page. Unlike the other settings pages it has no preference model
 * behind it — the two rows are plain actions on the logcat reader.
 */
@Composable
fun DeveloperScreen(
    onNavigateUp: () -> Unit,
    onOpenLogs: () -> Unit,
    onClearLogs: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    TrimeScreen(
        title = stringResource(R.string.developer),
        onNavigateUp = onNavigateUp,
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                PreferenceRow(
                    title = stringResource(R.string.real_time_logs),
                    onClick = onOpenLogs,
                )
                PreferenceRow(
                    title = stringResource(R.string.real_time_logs_clear),
                    onClick = { confirmClear = true },
                )
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            text = { Text(stringResource(R.string.real_time_logs_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClearLogs()
                    },
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
