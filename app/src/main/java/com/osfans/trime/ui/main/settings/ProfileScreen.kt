/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.osfans.trime.R
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.sync.DataStorageMode
import com.osfans.trime.ui.compose.TrimeScreen
import com.osfans.trime.ui.compose.preference.DialogPreferenceItem
import com.osfans.trime.ui.compose.preference.LoadingDialog
import com.osfans.trime.ui.compose.preference.PreferenceCategoryHeader
import com.osfans.trime.ui.compose.preference.PreferenceRow
import com.osfans.trime.ui.compose.preference.SingleChoiceDialog
import com.osfans.trime.ui.compose.preference.SwitchPreferenceItem
import com.osfans.trime.ui.compose.preference.TextInputDialog

/**
 * Everything the profile page shows. The fragment owns it and re-reads it from the
 * `AppPrefs.Profile` delegates through [ProfileActions.refresh]-style updates, so the
 * preference storage stays the single source of truth (no value is ever cached across
 * a write).
 */
@Stable
class ProfileScreenState {
    var storageMode by mutableStateOf(DataStorageMode.EXTERNAL_SYNC)
    var dataPathSummary by mutableStateOf("")

    /** The data directory only matters in external sync mode; greyed out otherwise. */
    var dataPathEnabled by mutableStateOf(false)
    var backgroundSync by mutableStateOf(false)
    var backgroundSyncSummary by mutableStateOf<String?>(null)
    var backgroundSyncInterval by mutableStateOf(30)
    var gitEnabled by mutableStateOf(false)
    var gitRepoUrl by mutableStateOf("")
    var gitBranch by mutableStateOf("")
    var gitUsername by mutableStateOf("")
    var gitToken by mutableStateOf("")
    var gitSyncSummary by mutableStateOf("")

    /** Shown as a blocking indeterminate dialog; see `withLoadingState`. */
    var loading by mutableStateOf(false)
    var dialog by mutableStateOf<ProfileDialog?>(null)
}

/**
 * The confirmations this page can put up. Their *cancel* behaviour is not uniform —
 * see each one — so it is spelled out where the dialog is drawn.
 */
sealed interface ProfileDialog {
    /** Tapping the data directory row: offers picking a different one. */
    data object SelectAnotherDirectory : ProfileDialog

    /** The reset-data-path picker came back empty: pick again, or fall back. */
    data object ResetDataPathCancelled : ProfileDialog

    /** Switching to external sync without a directory granted yet. */
    data object ExternalSyncFolderSelection : ProfileDialog

    /** Notice that the profile fell back to app-specific storage. */
    data object FallbackNotice : ProfileDialog

    /** Restore assets from `assets/shared` over the shared data directory. */
    data class Reset(
        val items: List<String>,
    ) : ProfileDialog
}

interface ProfileActions {
    fun onStorageModeSelected(mode: DataStorageMode)

    fun onDataPathClick()

    fun onSyncNow()

    fun onBackgroundSyncChange(enabled: Boolean)

    fun onBackgroundSyncIntervalChange(minutes: Int)

    fun onGitEnabledChange(enabled: Boolean)

    fun onGitRepoUrlChange(value: String)

    fun onGitBranchChange(value: String)

    fun onGitUsernameChange(value: String)

    fun onGitTokenChange(value: String)

    fun onGitSyncNow()

    fun onBrowseAppDataDir()

    fun onResetClick()

    fun onResetConfirmed(selected: List<String>)

    /** The dialog was dismissed without choosing anything (back press / outside tap). */
    fun onDialogDismissed()

    fun onSelectAnotherDirectoryConfirmed()

    fun onPickDataPathAgain()

    fun onUseAppStorage()

    fun onExternalSyncSelectFolder()
}

/** Lower bound of the background sync interval, as in the old `EditTextIntPreference`. */
private const val MIN_SYNC_INTERVAL = 15

@Composable
fun ProfileScreen(
    state: ProfileScreenState,
    actions: ProfileActions,
    onNavigateUp: () -> Unit,
) {
    TrimeScreen(
        title = stringResource(R.string.profile),
        onNavigateUp = onNavigateUp,
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item("storage") { StorageSection(state, actions) }
            item("sync") { SyncSection(state, actions) }
            item("git") { GitSection(state, actions) }
            item("maintenance") { MaintenanceSection(actions) }
        }
    }

    if (state.loading) {
        LoadingDialog()
    }
    ProfileDialogHost(state, actions)
}

@Composable
private fun StorageSection(
    state: ProfileScreenState,
    actions: ProfileActions,
) {
    val modes = remember { DataStorageMode.entries }
    var showModePicker by remember { mutableStateOf(false) }
    val labels = modes.map { stringResource(it.stringRes) }
    val title = stringResource(R.string.data_storage_mode)

    Column {
        PreferenceCategoryHeader(stringResource(R.string.storage))
        DialogPreferenceItem(
            title = title,
            value = labels[modes.indexOf(state.storageMode)],
            onClick = { showModePicker = true },
        )
        PreferenceRow(
            title = stringResource(R.string.user_data_dir),
            summary = state.dataPathSummary,
            enabled = state.dataPathEnabled,
            onClick = actions::onDataPathClick,
        )
    }
    if (showModePicker) {
        SingleChoiceDialog(
            title = title,
            entries = labels,
            selectedIndex = modes.indexOf(state.storageMode),
            onDismiss = { showModePicker = false },
            onSelect = {
                showModePicker = false
                actions.onStorageModeSelected(modes[it])
            },
        )
    }
}

@Composable
private fun SyncSection(
    state: ProfileScreenState,
    actions: ProfileActions,
) {
    var showIntervalInput by remember { mutableStateOf(false) }
    val intervalTitle = stringResource(R.string.periodic_background_sync_interval)

    Column {
        PreferenceCategoryHeader(stringResource(R.string.synchronization))
        PreferenceRow(
            title = stringResource(R.string.sync_user_data_immediately),
            onClick = actions::onSyncNow,
        )
        SwitchPreferenceItem(
            title = stringResource(R.string.periodic_background_sync),
            summary = state.backgroundSyncSummary,
            checked = state.backgroundSync,
            onCheckedChange = actions::onBackgroundSyncChange,
        )
        DialogPreferenceItem(
            title = intervalTitle,
            value = state.backgroundSyncInterval.toString(),
            enabled = state.backgroundSync,
            onClick = { showIntervalInput = true },
        )
    }
    if (showIntervalInput) {
        // The old preference only had a lower bound, so a plain min/max dialog would
        // have to claim a made-up maximum.
        LowerBoundedIntInputDialog(
            title = intervalTitle,
            initialValue = state.backgroundSyncInterval,
            min = MIN_SYNC_INTERVAL,
            onDismiss = { showIntervalInput = false },
            onConfirm = {
                showIntervalInput = false
                actions.onBackgroundSyncIntervalChange(it)
            },
        )
    }
}

@Composable
private fun GitSection(
    state: ProfileScreenState,
    actions: ProfileActions,
) {
    Column {
        PreferenceCategoryHeader(stringResource(R.string.git_config_sync))
        SwitchPreferenceItem(
            title = stringResource(R.string.git_sync_enabled),
            summary = stringResource(R.string.git_sync_enabled_summary),
            checked = state.gitEnabled,
            onCheckedChange = actions::onGitEnabledChange,
        )
        TextPreferenceItem(R.string.git_repo_url, state.gitRepoUrl, actions::onGitRepoUrlChange)
        TextPreferenceItem(R.string.git_branch, state.gitBranch, actions::onGitBranchChange)
        TextPreferenceItem(R.string.git_username, state.gitUsername, actions::onGitUsernameChange)
        SecretPreferenceItem(R.string.git_token, state.gitToken, actions::onGitTokenChange)
        PreferenceRow(
            title = stringResource(R.string.git_sync_now),
            summary = state.gitSyncSummary,
            onClick = actions::onGitSyncNow,
        )
    }
}

@Composable
private fun MaintenanceSection(actions: ProfileActions) {
    Column {
        PreferenceCategoryHeader(stringResource(R.string.maintenance))
        PreferenceRow(
            title = stringResource(R.string.browse_app_data_dir),
            summary = DataManager.userDataDir.absolutePath,
            onClick = actions::onBrowseAppDataDir,
        )
        PreferenceRow(
            title = stringResource(R.string.reset),
            summary = stringResource(R.string.reset_hint),
            onClick = actions::onResetClick,
        )
        Spacer(Modifier.height(24.dp))
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

@Composable
private fun LowerBoundedIntInputDialog(
    title: String,
    initialValue: Int,
    min: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue.toString()) }
    val parsed = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter { it.isDigit() } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = parsed == null || parsed < min,
                supportingText = { Text("≥ $min") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { onConfirm(it.coerceAtLeast(min)) } },
                enabled = parsed != null,
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun ProfileDialogHost(
    state: ProfileScreenState,
    actions: ProfileActions,
) {
    when (val dialog = state.dialog) {
        null -> Unit

        // Cancelling means "keep the current directory": nothing happens.
        ProfileDialog.SelectAnotherDirectory -> ConfirmDialog(
            message = stringResource(R.string.select_another_directory_to_sync),
            confirmText = stringResource(R.string.select_another_directory),
            dismissText = stringResource(android.R.string.cancel),
            onConfirm = actions::onSelectAnotherDirectoryConfirmed,
            onDismissButton = actions::onDialogDismissed,
            onDismissRequest = actions::onDialogDismissed,
        )

        // Backing out of this one re-opens the picker, exactly as before: the profile
        // must not be left pointing at a directory that is no longer granted.
        ProfileDialog.ResetDataPathCancelled -> ConfirmDialog(
            message = stringResource(R.string.reset_data_path_cancelled_message),
            confirmText = stringResource(R.string.reset_data_path_pick_again),
            dismissText = stringResource(R.string.reset_data_path_use_app_storage),
            onConfirm = actions::onPickDataPathAgain,
            onDismissButton = actions::onUseAppStorage,
            onDismissRequest = actions::onPickDataPathAgain,
        )

        // Backing out here falls back to app storage, as before.
        ProfileDialog.ExternalSyncFolderSelection -> ConfirmDialog(
            message = stringResource(R.string.external_sync_select_folder_message),
            confirmText = stringResource(R.string.setup__select_data_path),
            dismissText = stringResource(android.R.string.cancel),
            onConfirm = actions::onExternalSyncSelectFolder,
            onDismissButton = actions::onUseAppStorage,
            onDismissRequest = actions::onUseAppStorage,
        )

        ProfileDialog.FallbackNotice -> AlertDialog(
            onDismissRequest = actions::onDialogDismissed,
            text = { Text(stringResource(R.string.external_sync_fallback_app_storage)) },
            confirmButton = {
                TextButton(onClick = actions::onDialogDismissed) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )

        is ProfileDialog.Reset -> ResetDialog(
            items = dialog.items,
            onConfirm = actions::onResetConfirmed,
            onDismiss = actions::onDialogDismissed,
        )
    }
}

@Composable
private fun ConfirmDialog(
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismissButton: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismissButton) { Text(dismissText) }
        },
    )
}

/** Multi-choice restore dialog, the replacement for `setMultiChoiceItems`. */
@Composable
private fun ResetDialog(
    items: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val checked = remember(items) { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reset)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEach { item ->
                    val isChecked = item in checked
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = isChecked,
                                onValueChange = { on ->
                                    if (on) checked.add(item) else checked.remove(item)
                                },
                            ).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = isChecked, onCheckedChange = null)
                        Spacer(Modifier.width(16.dp))
                        Text(text = item, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(items.filter { it in checked }) }) {
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
