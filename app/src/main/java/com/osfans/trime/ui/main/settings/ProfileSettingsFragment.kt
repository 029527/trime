/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.data.sync.DataStorageMode
import com.osfans.trime.data.sync.GitConfigSync
import com.osfans.trime.data.sync.RimeDataSync
import com.osfans.trime.data.sync.SafDisplayPath
import com.osfans.trime.data.sync.UserDbMigration
import com.osfans.trime.ui.compose.ComposeFragment
import com.osfans.trime.ui.compose.preference.withLoadingState
import com.osfans.trime.util.ResourceUtils
import com.osfans.trime.util.buildDocumentsProviderIntent
import com.osfans.trime.util.customFormatTimeInDefault
import com.osfans.trime.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The user profile page: where the Rime data lives, how it is synchronised, and the
 * restore-from-assets escape hatch.
 *
 * The UI is Compose, but every bit of behaviour — the SAF directory grant, what a
 * cancelled picker means, which failure falls back to app storage — is unchanged from
 * the preference-screen version; getting this wrong loses the user's dictionaries.
 * State lives in [ProfileScreenState] and is always re-read from the preference
 * delegates by [refresh], never cached across a write.
 */
class ProfileSettingsFragment :
    ComposeFragment(),
    ProfileActions {
    private val prefs = AppPrefs.defaultInstance().profile
    private val state = ProfileScreenState()

    private var pendingPickerCancelToAppStorage = false
    private var pendingResetDataPath = false

    // Keep hard references: the delegates hold their listeners weakly.
    private val onBackgroundSyncEnable = PreferenceDelegate.OnChangeListener<Boolean> { _, _ ->
        refresh()
    }

    private val onSyncIntervalChange = PreferenceDelegate.OnChangeListener<Int> { _, _ ->
        if (prefs.periodicBackgroundSync.getValue()) {
            mainViewModel.restartBackgroundSyncWork.value = true
        }
        refresh()
    }

    private val onDataPathChange = PreferenceDelegate.OnChangeListener<String> { _, _ ->
        refresh()
    }

    private val onStorageModeChange = PreferenceDelegate.OnChangeListener<DataStorageMode> { _, _ ->
        refresh()
    }

    private val dataPathPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) {
                when {
                    pendingResetDataPath -> state.dialog = ProfileDialog.ResetDataPathCancelled
                    pendingPickerCancelToAppStorage -> fallbackToAppStorage()
                }
                return@registerForActivityResult
            }
            handleTreePicked(uri, pendingPickerCancelToAppStorage)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs.periodicBackgroundSync.registerOnChangeListener(onBackgroundSyncEnable)
        prefs.periodicBackgroundSyncInterval.registerOnChangeListener(onSyncIntervalChange)
        prefs.externalRimeTreeUri.registerOnChangeListener(onDataPathChange)
        prefs.externalRimeDisplayName.registerOnChangeListener(onDataPathChange)
        prefs.dataStorageMode.registerOnChangeListener(onStorageModeChange)
        refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.periodicBackgroundSync.unregisterOnChangeListener(onBackgroundSyncEnable)
        prefs.periodicBackgroundSyncInterval.unregisterOnChangeListener(onSyncIntervalChange)
        prefs.externalRimeTreeUri.unregisterOnChangeListener(onDataPathChange)
        prefs.externalRimeDisplayName.unregisterOnChangeListener(onDataPathChange)
        prefs.dataStorageMode.unregisterOnChangeListener(onStorageModeChange)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        val ctx = requireContext()
        if (
            RimeDataSync.usesExternalSync(ctx) &&
            prefs.externalRimeTreeUri.getValue().isNotEmpty() &&
            !RimeDataSync.hasExternalAccess(ctx)
        ) {
            ctx.toast(R.string.data_path_permission_revoked)
        }
    }

    @Composable
    override fun Content() {
        ProfileScreen(
            state = state,
            actions = this,
            onNavigateUp = ::navigateUp,
        )
    }

    // region state

    private fun refresh() {
        if (context == null) return
        state.storageMode = prefs.dataStorageMode.getValue()
        state.dataPathEnabled = RimeDataSync.usesExternalSync()
        state.dataPathSummary = dataPathSummary()
        state.backgroundSync = prefs.periodicBackgroundSync.getValue()
        state.backgroundSyncSummary = backgroundSyncSummary()
        state.backgroundSyncInterval = prefs.periodicBackgroundSyncInterval.getValue()
        state.gitEnabled = prefs.gitSyncEnabled.getValue()
        state.gitRepoUrl = prefs.gitRepoUrl.getValue()
        state.gitBranch = prefs.gitBranch.getValue()
        state.gitUsername = prefs.gitUsername.getValue()
        state.gitToken = prefs.gitToken.getValue()
        state.gitSyncSummary = gitSyncSummary()
    }

    private fun dataPathSummary(): String {
        val uri = prefs.externalRimeTreeUri.getValue()
        if (uri.isEmpty()) return getString(R.string.data_path_not_selected)
        return SafDisplayPath.fromTreeUri(Uri.parse(uri))
            ?: prefs.externalRimeDisplayName.getValue().takeIf { it.isNotEmpty() }
            ?: uri
    }

    private fun backgroundSyncSummary(): String? {
        if (!prefs.periodicBackgroundSync.getValue()) return null
        val time = prefs.lastBackgroundSyncTime.getValue()
        val lastTime: String
        val lastStatus: String
        if (time != 0L) {
            lastTime = customFormatTimeInDefault("yyyy-MM-dd HH:mm", time)
            lastStatus = getString(
                if (prefs.lastBackgroundSyncStatus.getValue()) R.string.success else R.string.failure,
            )
        } else {
            lastTime = "N/A"
            lastStatus = "N/A"
        }
        return getString(R.string.periodic_background_sync_status, lastTime, lastStatus)
    }

    private fun gitSyncSummary(): String {
        val commit = prefs.gitLastCommit.getValue()
        val time = prefs.gitLastSyncTime.getValue()
        val text =
            if (commit.isEmpty() || time == 0L) {
                "N/A"
            } else {
                "$commit @ ${customFormatTimeInDefault("yyyy-MM-dd HH:mm", time)}"
            }
        return getString(R.string.git_sync_last, text)
    }

    /** Blocks the screen while [action] runs, unless it is quick; see `withLoadingState`. */
    private suspend fun withLoading(action: suspend () -> Unit) = withLoadingState({ state.loading = it }, action = action)

    // endregion

    // region data directory

    private fun launchDataPathPicker(cancelToAppStorage: Boolean) {
        pendingPickerCancelToAppStorage = cancelToAppStorage
        pendingResetDataPath = false
        dataPathPicker.launch(null as Uri?)
    }

    private fun launchResetDataPathPicker() {
        pendingPickerCancelToAppStorage = false
        pendingResetDataPath = true
        dataPathPicker.launch(null as Uri?)
    }

    private fun handleTreePicked(
        uri: Uri,
        onCancelToAppStorage: Boolean,
    ) {
        val ctx = requireContext()
        lifecycleScope.launch {
            withLoading {
                runCatching {
                    withContext(Dispatchers.IO) {
                        RimeDataSync.persistTreeUri(ctx, uri)
                        RimeDataSync.importToLocal(ctx).getOrThrow()
                        mainViewModel.rime.runOnReady { deploy(skipImport = true) }
                    }
                }.onSuccess {
                    refresh()
                    ctx.toast(R.string.setup__data_path_imported)
                }.onFailure {
                    if (onCancelToAppStorage) {
                        fallbackToAppStorage()
                    } else {
                        withContext(Dispatchers.IO) {
                            RimeDataSync.clearExternalTree(ctx)
                        }
                        refresh()
                        ctx.toast(R.string.setup__data_path_import_failed)
                    }
                }
            }
        }
    }

    private fun fallbackToAppStorage() {
        RimeDataSync.clearExternalTree(requireContext())
        UserDbMigration.onStorageModeChanged(
            DataStorageMode.EXTERNAL_SYNC,
            DataStorageMode.APP_STORAGE,
        )
        prefs.dataStorageMode.setValue(DataStorageMode.APP_STORAGE)
        refresh()
        state.dialog = ProfileDialog.FallbackNotice
    }

    // endregion

    // region actions

    override fun onStorageModeSelected(mode: DataStorageMode) {
        val ctx = requireContext()
        val oldMode = prefs.dataStorageMode.getValue()
        UserDbMigration.onStorageModeChanged(oldMode, mode)
        prefs.dataStorageMode.setValue(mode)
        if (
            oldMode == DataStorageMode.APP_STORAGE &&
            mode == DataStorageMode.EXTERNAL_SYNC &&
            !RimeDataSync.hasExternalAccess()
        ) {
            state.dialog = ProfileDialog.ExternalSyncFolderSelection
        } else if (
            oldMode == DataStorageMode.EXTERNAL_SYNC &&
            mode == DataStorageMode.APP_STORAGE
        ) {
            RimeDataSync.clearExternalTree(ctx)
        }
        refresh()
    }

    override fun onDataPathClick() {
        state.dialog = ProfileDialog.SelectAnotherDirectory
    }

    override fun onSelectAnotherDirectoryConfirmed() {
        state.dialog = null
        RimeDataSync.clearExternalTree(requireContext())
        refresh()
        launchResetDataPathPicker()
    }

    override fun onPickDataPathAgain() {
        state.dialog = null
        launchResetDataPathPicker()
    }

    override fun onUseAppStorage() {
        fallbackToAppStorage()
    }

    override fun onExternalSyncSelectFolder() {
        state.dialog = null
        launchDataPathPicker(cancelToAppStorage = true)
    }

    override fun onDialogDismissed() {
        state.dialog = null
    }

    override fun onSyncNow() {
        val ctx = requireContext()
        lifecycleScope.launch {
            withLoading {
                runCatching {
                    mainViewModel.rime.runOnReady { syncUserData() }
                }.onSuccess { success ->
                    ctx.toast(
                        when {
                            !success -> R.string.sync_user_data_failure
                            RimeDataSync.usesExternalSync(ctx) ->
                                R.string.sync_user_data_success_external
                            else -> R.string.sync_user_data_success
                        },
                    )
                }.onFailure {
                    ctx.toast(R.string.sync_user_data_failure)
                }
            }
        }
    }

    override fun onBackgroundSyncChange(enabled: Boolean) {
        prefs.periodicBackgroundSync.setValue(enabled)
        refresh()
    }

    override fun onBackgroundSyncIntervalChange(minutes: Int) {
        prefs.periodicBackgroundSyncInterval.setValue(minutes)
        refresh()
    }

    override fun onGitEnabledChange(enabled: Boolean) {
        prefs.gitSyncEnabled.setValue(enabled)
        refresh()
    }

    override fun onGitRepoUrlChange(value: String) {
        prefs.gitRepoUrl.setValue(value)
        refresh()
    }

    override fun onGitBranchChange(value: String) {
        prefs.gitBranch.setValue(value)
        refresh()
    }

    override fun onGitUsernameChange(value: String) {
        prefs.gitUsername.setValue(value)
        refresh()
    }

    override fun onGitTokenChange(value: String) {
        prefs.gitToken.setValue(value)
        refresh()
    }

    override fun onGitSyncNow() {
        val ctx = requireContext()
        lifecycleScope.launch {
            withLoading {
                GitConfigSync
                    .pullAndImport()
                    .mapCatching { head ->
                        mainViewModel.rime.runOnReady { deploy(skipImport = true) }
                        head
                    }.onSuccess { head ->
                        ctx.toast(getString(R.string.git_sync_success, head.take(12)))
                    }.onFailure {
                        ctx.toast(
                            getString(R.string.git_sync_failure, it.message ?: it.javaClass.simpleName),
                        )
                    }
                refresh()
            }
        }
    }

    override fun onBrowseAppDataDir() {
        val ctx = requireContext()
        runCatching {
            ctx.startActivity(buildDocumentsProviderIntent())
        }.onFailure {
            ctx.toast(R.string.browse_app_data_dir_failed)
        }
    }

    override fun onResetClick() {
        val items = requireContext().assets.list("shared") ?: return
        state.dialog = ProfileDialog.Reset(items.toList())
    }

    override fun onResetConfirmed(selected: List<String>) {
        state.dialog = null
        val ctx = requireContext()
        lifecycleScope.launch {
            var res = true
            withLoading {
                withContext(Dispatchers.IO) {
                    res = selected.fold(true) { acc, asset ->
                        val destPath = DataManager.sharedDataDir.resolve(asset).absolutePath
                        ResourceUtils
                            .copyFile("shared/$asset", destPath)
                            .fold({ acc and true }, { acc and false })
                    }
                }
            }
            ctx.toast(if (res) R.string.reset_success else R.string.reset_failure)
        }
    }

    // endregion
}
