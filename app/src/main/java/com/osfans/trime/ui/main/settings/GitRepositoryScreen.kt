/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ui.compose.ComposeFragment
import com.osfans.trime.ui.compose.TrimeScreen
import com.osfans.trime.ui.compose.preference.DialogPreferenceItem
import com.osfans.trime.ui.compose.preference.TextInputDialog

/**
 * 配置 › 仓库与账号: the Git repository the profile pulls its configuration from. Only
 * configuration lives here; turning the pull on and "pull and deploy now" stay on the profile page.
 * The values are the same `AppPrefs.Profile` delegates as before, read fresh on every change.
 */
@Composable
fun GitRepositoryScreen(onNavigateUp: () -> Unit) {
    val prefs = AppPrefs.defaultInstance().profile

    // the delegates are not Compose state: bump after a write so the rows re-read
    var revision by remember { mutableIntStateOf(0) }

    @Suppress("UNUSED_EXPRESSION")
    revision

    val repoUrl = prefs.gitRepoUrl.getValue()
    val branch = prefs.gitBranch.getValue()
    val username = prefs.gitUsername.getValue()
    val token = prefs.gitToken.getValue()

    TrimeScreen(
        title = stringResource(R.string.git_repository),
        onNavigateUp = onNavigateUp,
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                TextPreferenceItem(R.string.git_repo_url, repoUrl) {
                    prefs.gitRepoUrl.setValue(it)
                    revision++
                }
                TextPreferenceItem(R.string.git_branch, branch) {
                    prefs.gitBranch.setValue(it)
                    revision++
                }
                TextPreferenceItem(R.string.git_username, username) {
                    prefs.gitUsername.setValue(it)
                    revision++
                }
                SecretPreferenceItem(R.string.git_token, token) {
                    prefs.gitToken.setValue(it)
                    revision++
                }
            }
        }
    }
}

class GitRepositoryFragment : ComposeFragment() {
    @Composable
    override fun Content() {
        GitRepositoryScreen(onNavigateUp = ::navigateUp)
    }
}

@Composable
private fun TextPreferenceItem(
    @StringRes title: Int,
    value: String,
    onConfirm: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val text = stringResource(title)
    DialogPreferenceItem(
        title = text,
        value = value.ifEmpty { stringResource(R.string.not_set) },
        onClick = { showDialog = true },
    )
    if (showDialog) {
        TextInputDialog(
            title = text,
            initialValue = value,
            onDismiss = { showDialog = false },
            onConfirm = {
                showDialog = false
                onConfirm(it)
            },
        )
    }
}

/** Like [TextPreferenceItem], but the value is masked and never shown as a summary. */
@Composable
private fun SecretPreferenceItem(
    @StringRes title: Int,
    value: String,
    onConfirm: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val text = stringResource(title)
    DialogPreferenceItem(
        title = text,
        value = if (value.isEmpty()) "N/A" else stringResource(R.string.git_token_set),
        onClick = { showDialog = true },
    )
    if (showDialog) {
        var input by remember { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        onConfirm(input)
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
