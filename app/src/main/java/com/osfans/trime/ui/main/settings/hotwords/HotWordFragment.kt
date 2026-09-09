/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.hotwords

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.data.hotwords.HotWord
import com.osfans.trime.data.hotwords.HotWordManager
import com.osfans.trime.ui.common.withLoadingDialog
import com.osfans.trime.ui.compose.ComposeFragment
import com.osfans.trime.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lets the user keep a list of words that should beat the dictionaries (`okok`
 * instead of `哦可哦可`). Every change is written to the app's private storage and
 * followed by a deploy, which regenerates the Rime tables for both keyboards.
 */
class HotWordFragment : ComposeFragment() {
    private val words = mutableStateListOf<HotWord>().apply { addAll(HotWordManager.getAll()) }

    private val snackbarHostState = SnackbarHostState()

    @Composable
    override fun Content() {
        HotWordListScreen(
            entries = words,
            snackbarHostState = snackbarHostState,
            onNavigateUp = ::navigateUp,
            onSave = ::save,
            onDelete = ::delete,
        )
    }

    private fun save(existing: HotWord?, word: HotWord) {
        val index = existing?.let { words.indexOf(it) } ?: -1
        if (index >= 0) words[index] = word else words.add(0, word)
        commit()
    }

    private fun delete(word: HotWord) {
        if (words.remove(word)) commit()
    }

    /** Persist the list, then redeploy so the regenerated tables take effect. */
    private fun commit() {
        val ctx = requireContext()
        val snapshot = words.toList()
        lifecycleScope.launch {
            var outcome: Result<Unit> = Result.success(Unit)
            // The snackbar is shown *after* the loading dialog is gone: showSnackbar
            // suspends until it is dismissed, which would otherwise hold the dialog up.
            withLoadingDialog(ctx, R.string.hot_word_deploying) {
                outcome = runCatching {
                    withContext(Dispatchers.IO) { HotWordManager.save(snapshot) }
                    mainViewModel.rime.runOnReady { deploy(skipImport = true) }
                    Unit
                }
            }
            outcome
                .onSuccess {
                    snackbarHostState.showSnackbar(
                        message = getString(R.string.hot_word_deployed),
                        duration = SnackbarDuration.Short,
                    )
                }.onFailure {
                    ctx.toast(getString(R.string.hot_word_deploy_failed, it.message ?: it.javaClass.simpleName))
                }
        }
    }
}
