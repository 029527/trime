/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.userdict

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.data.userdict.UserDictManager
import com.osfans.trime.ui.compose.ComposeFragment
import com.osfans.trime.util.importErrorDialog
import com.osfans.trime.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserDictionaryFragment : ComposeFragment() {
    private lateinit var restoreLauncher: ActivityResultLauncher<String>

    private lateinit var importLauncher: ActivityResultLauncher<String>

    private lateinit var exportLauncher: ActivityResultLauncher<String>

    private var beingImported: String? = null

    private var beingExported: String? = null

    private val entries = mutableStateListOf<String>()

    private val snackbarHostState = SnackbarHostState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerLauncher()
        refreshEntries()
    }

    @Composable
    override fun Content() {
        UserDictListScreen(
            entries = entries,
            snackbarHostState = snackbarHostState,
            onNavigateUp = ::navigateUp,
            onRestore = { restoreLauncher.launch("text/plain") },
            onBackup = ::backup,
            onImport = { dictName ->
                beingImported = dictName
                importLauncher.launch("text/plain")
            },
            onExport = { dictName ->
                beingExported = dictName
                exportLauncher.launch("$dictName.txt")
            },
        )
    }

    private fun refreshEntries() {
        val list = UserDictManager.getUserDictList().toList()
        entries.clear()
        entries.addAll(list)
    }

    private fun showSnackBar(text: String) {
        lifecycleScope.launch {
            snackbarHostState.showSnackbar(message = text, duration = SnackbarDuration.Short)
        }
    }

    private fun backup(dictName: String) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                UserDictManager.backupUserDict(dictName)
            }
            if (success) {
                showSnackBar(getString(R.string.backed_up_x_to_sync_dir, dictName))
            }
        }
    }

    private fun registerLauncher() {
        restoreLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            importFromUri(uri, merge = true)
        }
        importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            importFromUri(uri)
        }
        exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri == null) return@registerForActivityResult
            val ctx = requireContext()
            val cr = ctx.contentResolver
            val dictName = beingExported ?: return@registerForActivityResult
            beingExported = null
            lifecycleScope.launch {
                val fileName = cr.queryFileName(uri) ?: return@launch
                try {
                    val count = withContext(Dispatchers.IO) {
                        val outputStream = cr.openOutputStream(uri)!!
                        UserDictManager.exportUserDict(
                            outputStream,
                            dictName,
                            fileName,
                        ).getOrThrow()
                    }
                    showSnackBar(ctx.getString(R.string.exported_n_entries, count))
                } catch (e: Exception) {
                    ctx.toast(e)
                }
            }
        }
    }

    private fun importFromUri(uri: Uri, merge: Boolean = false) {
        val ctx = requireContext()
        val cr = ctx.contentResolver
        lifecycleScope.launch {
            val fileName = cr.queryFileName(uri) ?: return@launch
            try {
                if (merge) {
                    val result = withContext(Dispatchers.IO) {
                        cr.openInputStream(uri)!!.use { inputStream ->
                            UserDictManager.restoreUserDict(inputStream, fileName)
                        }
                    }
                    if (result.isSuccess) {
                        showSnackBar(ctx.getString(R.string.restored_from_x, fileName))
                        refreshEntries()
                    }
                } else {
                    val dictName = beingImported ?: return@launch
                    beingImported = null
                    val count = withContext(Dispatchers.IO) {
                        cr.openInputStream(uri)!!.use { inputStream ->
                            UserDictManager.importUserDict(
                                inputStream,
                                dictName,
                                fileName,
                            ).getOrThrow()
                        }
                    }
                    showSnackBar(ctx.getString(R.string.import_n_entries, count))
                }
            } catch (e: Exception) {
                ctx.importErrorDialog(e)
            }
        }
    }

    fun ContentResolver.queryFileName(uri: Uri): String? = query(uri, null, null, null, null)?.use {
        val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        it.moveToFirst()
        it.getString(index)
    }
}
