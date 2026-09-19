/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.schema

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.osfans.trime.R
import com.osfans.trime.core.SchemaItem
import com.osfans.trime.data.schema.FixedSchemata
import com.osfans.trime.ui.compose.preference.SwitchPreferenceItem
import com.osfans.trime.ui.main.settings.list.ListScreen

/**
 * 预制输入方案管理界面。
 *
 * 作为预制方案的展示与启用入口：
 * 禁止外部动态增删方案，提供开关控制每个预制方案是否启用。
 */
@Composable
fun SchemaListScreen(
    schemata: List<SchemaItem>,
    enabledIds: Set<String>,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onToggleEnable: (id: String, enable: Boolean) -> Unit,
) {
    ListScreen(
        title = stringResource(R.string.schemata),
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            items(schemata, key = { it.id }) { item ->
                val checked = FixedSchemata.isSchemaEnabled(item.id, enabledIds)
                SwitchPreferenceItem(
                    title = FixedSchemata.getDisplayName(item.id, item.name),
                    checked = checked,
                    onCheckedChange = { onToggleEnable(item.id, it) },
                )
            }
        }
    }
}
