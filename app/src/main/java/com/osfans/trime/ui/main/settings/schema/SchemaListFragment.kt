/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.schema

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.TrimeApplication
import com.osfans.trime.core.SchemaItem
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.schema.FixedSchemata
import com.osfans.trime.data.sync.RimeDataSync
import com.osfans.trime.ui.compose.ComposeFragment
import com.osfans.trime.util.NaiveDustman
import com.osfans.trime.util.appContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class SchemaListFragment : ComposeFragment() {
    private val dustman = NaiveDustman<SchemaItem>()

    /** The enabled schemata, in the order they will be written back. */
    private val entries = mutableStateListOf<SchemaItem>()

    private var available by mutableStateOf<List<SchemaItem>>(emptyList())

    private var loading by mutableStateOf(true)

    private val snackbarHostState = SnackbarHostState()

    /** Guards against an undo raising an undo of its own. */
    private var suspendUndo = false

    @Composable
    override fun Content() {
        LaunchedEffect(Unit) {
            if (loading) load()
        }
        SchemaListScreen(
            loading = loading,
            entries = entries,
            addable = available.filter { candidate -> entries.none { it.id == candidate.id } },
            snackbarHostState = snackbarHostState,
            onNavigateUp = ::navigateUp,
            onAdd = ::addSchemata,
            onRemove = ::removeSchemata,
        )
    }

    private suspend fun load() {
        val rime = mainViewModel.rime
        val all = rime.runOnReady { availableSchemata().toList() }
        val enabled = rime.runOnReady { enabledSchemata().map { it.id } }
        entries.clear()

        val doublePinyinId = all.firstOrNull { FixedSchemata.isDoublePinyin(it.id) }?.id
            ?: enabled.firstOrNull { FixedSchemata.isDoublePinyin(it) }
            ?: FixedSchemata.ID_DOUBLE_PINYIN
        val t9Id = all.firstOrNull { FixedSchemata.isT9(it.id) }?.id
            ?: enabled.firstOrNull { FixedSchemata.isT9(it) }
            ?: FixedSchemata.ID_T9

        val fixedList = listOf(
            SchemaItem(FixedSchemata.ID_ENGLISH, FixedSchemata.NAME_ENGLISH),
            SchemaItem(doublePinyinId, FixedSchemata.NAME_DOUBLE_PINYIN),
            SchemaItem(t9Id, FixedSchemata.NAME_T9),
        )

        entries.addAll(fixedList)
        available = fixedList
        resetDustman()
        loading = false
    }

    private fun addSchemata(items: List<SchemaItem>) {
        if (items.isEmpty()) return
        entries.addAll(items)
        items.forEach { dustman.addOrUpdate(it.toString(), it) }
        showUndoSnackBar(
            if (items.size == 1) {
                getString(R.string.added_x, FixedSchemata.getDisplayName(items.first().id, items.first().name))
            } else {
                getString(R.string.added_n_items, items.size)
            },
        ) { removeSchemata(items) }
    }

    private fun removeSchemata(items: List<SchemaItem>) {
        if (items.isEmpty()) return
        val removed = items
            .mapNotNull { item ->
                entries.indexOfFirst { it.id == item.id }.takeIf { it >= 0 }?.let { it to item }
            }.sortedBy { it.first }
        if (removed.isEmpty()) return
        removed.asReversed().forEach { (index, _) -> entries.removeAt(index) }
        removed.forEach { (_, item) -> dustman.remove(item.toString()) }
        showUndoSnackBar(
            if (removed.size == 1) {
                getString(R.string.removed_x, FixedSchemata.getDisplayName(removed.first().second.id, removed.first().second.name))
            } else {
                getString(R.string.removed_n_items, removed.size)
            },
        ) {
            // Put them back where they were, lowest index first.
            removed.forEach { (index, item) ->
                entries.add(index.coerceAtMost(entries.size), item)
                dustman.addOrUpdate(item.toString(), item)
            }
        }
    }

    private fun showUndoSnackBar(
        text: String,
        undo: () -> Unit,
    ) {
        if (suspendUndo) return
        lifecycleScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = text,
                actionLabel = getString(R.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                suspendUndo = true
                try {
                    undo()
                } finally {
                    suspendUndo = false
                }
            }
        }
    }

    override fun onStop() {
        persistSchemaList()
        super.onStop()
    }

    private fun persistSchemaList() {
        if (loading || !dustman.dirty) return
        val schemaIds = entries
            .map { it.id }
            .filter { !FixedSchemata.isEnglish(it) }
            .toTypedArray()
        resetDustman()
        Timber.i("Persisting schema list: ${schemaIds.joinToString()}")
        TrimeApplication.getInstance().coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val sessionName = "schema-list-persist"
                runCatching {
                    val session = RimeDaemon.createSession(sessionName)
                    try {
                        session.runOnReady {
                            setEnabledSchemata(schemaIds)
                            deploy(skipImport = true)
                        }
                        if (RimeDataSync.usesExternalSync(appContext)) {
                            RimeDataSync.exportConfigFilesToExternal(appContext).getOrThrow()
                        }
                    } finally {
                        RimeDaemon.destroySession(sessionName)
                    }
                }.onFailure { Timber.e(it, "Failed to persist schema list") }
            }
        }
    }

    private fun resetDustman() {
        dustman.reset(entries.associateBy { it.toString() })
    }
}
