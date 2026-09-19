/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.schema

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.TrimeApplication
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.schema.FixedSchemata
import com.osfans.trime.data.sync.RimeDataSync
import com.osfans.trime.ui.compose.ComposeFragment
import com.osfans.trime.util.appContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 预制输入方案设置页面 Fragment。
 *
 * 作为预制方案的展示与启用入口：
 * 预制方案固定，禁止外部动态增删；用户可在此开启或关闭各个方案的启用状态。
 */
class SchemaListFragment : ComposeFragment() {
    private var enabledIds by mutableStateOf(FixedSchemata.getEnabledSchemaIds())

    private val snackbarHostState = SnackbarHostState()

    @Composable
    override fun Content() {
        LaunchedEffect(Unit) {
            enabledIds = FixedSchemata.getEnabledSchemaIds()
        }
        SchemaListScreen(
            schemata = FixedSchemata.FIXED_SCHEMATA,
            enabledIds = enabledIds,
            snackbarHostState = snackbarHostState,
            onNavigateUp = ::navigateUp,
            onToggleEnable = ::toggleSchemaEnable,
        )
    }

    private fun toggleSchemaEnable(id: String, enable: Boolean) {
        val current = enabledIds.toMutableSet()
        if (enable) {
            current.add(id)
        } else {
            // 至少保留一个启用的输入方案
            if (current.size <= 1) {
                lifecycleScope.launch {
                    snackbarHostState.showSnackbar(getString(R.string.no_schema_to_select))
                }
                return
            }
            current.remove(id)
        }
        enabledIds = current
        FixedSchemata.setEnabledSchemaIds(current)
        syncRimeSchemata(current)
    }

    private fun syncRimeSchemata(enabledSet: Set<String>) {
        val rimeSchemaIds = enabledSet
            .filter { !FixedSchemata.isEnglish(it) }
            .toTypedArray()
        Timber.i("Persisting rime schema list: ${rimeSchemaIds.joinToString()}")
        TrimeApplication.getInstance().coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val sessionName = "schema-list-persist"
                runCatching {
                    val session = RimeDaemon.createSession(sessionName)
                    try {
                        session.runOnReady {
                            setEnabledSchemata(rimeSchemaIds)
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
}
