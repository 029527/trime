/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.schema

import com.osfans.trime.core.SchemaItem

/**
 * 客户端专属优化方案定义与规范名称映射。
 *
 * 方案固定为：
 * 1. English（英文键盘模式）
 * 2. 简体中文-拼音-双拼（针对双拼优化的全键盘）
 * 3. 简体中文-拼音-九宫格（针对九宫格优化的 T9 键盘）
 */
object FixedSchemata {
    const val ID_ENGLISH = "english"
    const val ID_DOUBLE_PINYIN = "rime_mint_flypy"
    const val ID_T9 = "t9"

    const val NAME_ENGLISH = "English"
    const val NAME_DOUBLE_PINYIN = "简体中文-拼音-双拼"
    const val NAME_T9 = "简体中文-拼音-九宫格"

    const val SHORT_ENGLISH = "En"
    const val SHORT_DOUBLE_PINYIN = "双拼"
    const val SHORT_T9 = "九宫格"

    val FIXED_SCHEMATA: List<SchemaItem> =
        listOf(
            SchemaItem(ID_ENGLISH, NAME_ENGLISH),
            SchemaItem(ID_DOUBLE_PINYIN, NAME_DOUBLE_PINYIN),
            SchemaItem(ID_T9, NAME_T9),
        )

    fun isEnglish(id: String): Boolean = id.equals(ID_ENGLISH, ignoreCase = true)

    fun isT9(id: String): Boolean = id.equals(ID_T9, ignoreCase = true) || id.contains("t9", ignoreCase = true)

    fun isDoublePinyin(id: String): Boolean = !isEnglish(id) && !isT9(id)

    /**
     * 将 schemaId 或底层原始名称映射为统一的标准固定名称，不再保留原始名称。
     */
    fun getDisplayName(schemaId: String, rawName: String = ""): String = when {
        isEnglish(schemaId) || rawName.equals(NAME_ENGLISH, ignoreCase = true) -> NAME_ENGLISH
        isT9(schemaId) || rawName.contains("九宫格", ignoreCase = true) -> NAME_T9
        else -> NAME_DOUBLE_PINYIN
    }

    /**
     * 模式切换提示或按键显示的简短文本。
     */
    fun getShortName(schemaId: String, isAsciiMode: Boolean): String = when {
        isAsciiMode || isEnglish(schemaId) -> SHORT_ENGLISH
        isT9(schemaId) -> SHORT_T9
        else -> SHORT_DOUBLE_PINYIN
    }
}
