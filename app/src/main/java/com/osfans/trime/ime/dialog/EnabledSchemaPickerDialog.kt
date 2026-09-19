/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.dialog

import android.app.AlertDialog
import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.SchemaItem
import com.osfans.trime.data.schema.FixedSchemata
import kotlinx.coroutines.launch
import splitties.systemservices.inputMethodManager

/**
 * 方案选单对话框。
 *
 * 参照 iOS 键盘长按切换键弹出的键盘菜单：
 * 方案固定为：
 * 1. English
 * 2. 简体中文-拼音-双拼
 * 3. 简体中文-拼音-九宫格
 * 勾选当前处于激活状态的方案，点击任一项可直接完成中英文及对应键盘布局的切换。
 */
object EnabledSchemaPickerDialog {
    suspend fun build(
        rime: RimeApi,
        scope: LifecycleCoroutineScope,
        context: Context,
        isAsciiMode: Boolean = false,
        activeKeyboardId: String = "",
        onSelectSchema: ((schemaId: String) -> Unit)? = null,
        extensions: (AlertDialog.Builder.() -> AlertDialog.Builder)? = null,
    ): AlertDialog {
        val enabledRimeSchemata = rime.selectedSchemata().toList()
        val hasT9 = enabledRimeSchemata.any { FixedSchemata.isT9(it.id) }
        val hasDoublePinyin = enabledRimeSchemata.any { FixedSchemata.isDoublePinyin(it.id) } || enabledRimeSchemata.isEmpty()

        // 构造固定方案列表（对齐 iOS：English、双拼、九宫格）
        val items = mutableListOf<SchemaItem>()
        items.add(SchemaItem(FixedSchemata.ID_ENGLISH, FixedSchemata.NAME_ENGLISH))
        if (hasDoublePinyin) {
            val dpId = enabledRimeSchemata.firstOrNull { FixedSchemata.isDoublePinyin(it.id) }?.id ?: FixedSchemata.ID_DOUBLE_PINYIN
            items.add(SchemaItem(dpId, FixedSchemata.NAME_DOUBLE_PINYIN))
        }
        if (hasT9) {
            val t9Id = enabledRimeSchemata.firstOrNull { FixedSchemata.isT9(it.id) }?.id ?: FixedSchemata.ID_T9
            items.add(SchemaItem(t9Id, FixedSchemata.NAME_T9))
        }

        // 计算当前选中的方案项索引
        val currentRimeSchemaId = rime.selectedSchemaId()
        val selectedIndex = when {
            isAsciiMode || FixedSchemata.isEnglish(activeKeyboardId) -> {
                items.indexOfFirst { FixedSchemata.isEnglish(it.id) }
            }
            FixedSchemata.isT9(currentRimeSchemaId) || FixedSchemata.isT9(activeKeyboardId) -> {
                items.indexOfFirst { FixedSchemata.isT9(it.id) }
            }
            else -> {
                items.indexOfFirst { FixedSchemata.isDoublePinyin(it.id) }
            }
        }.coerceAtLeast(0)

        val itemNames = items.map { it.name }.toTypedArray()
        val itemIds = items.map { it.id }

        return AlertDialog
            .Builder(context)
            .apply {
                setTitle(R.string.select_current_schema)
                setSingleChoiceItems(itemNames, selectedIndex) { dialog, which ->
                    val chosenId = itemIds[which]
                    if (onSelectSchema != null) {
                        onSelectSchema.invoke(chosenId)
                    } else {
                        scope.launch {
                            if (FixedSchemata.isEnglish(chosenId)) {
                                rime.setRuntimeOption("ascii_mode", true)
                            } else {
                                rime.setRuntimeOption("ascii_mode", false)
                                rime.selectSchema(chosenId)
                            }
                        }
                    }
                    dialog.dismiss()
                }
                setNeutralButton(R.string.other_ime) { _, _ ->
                    inputMethodManager.showInputMethodPicker()
                }
                extensions?.invoke(this)
            }.create()
    }
}
