/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.schema

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class FixedSchemataTest :
    StringSpec({
        "固定方案列表包含且仅包含三个规范方案" {
            FixedSchemata.FIXED_SCHEMATA shouldHaveSize 3
            FixedSchemata.FIXED_SCHEMATA[0].id shouldBe "english"
            FixedSchemata.FIXED_SCHEMATA[0].name shouldBe "English"
            FixedSchemata.FIXED_SCHEMATA[1].id shouldBe "rime_mint_flypy"
            FixedSchemata.FIXED_SCHEMATA[1].name shouldBe "简体中文-拼音-双拼"
            FixedSchemata.FIXED_SCHEMATA[2].id shouldBe "t9"
            FixedSchemata.FIXED_SCHEMATA[2].name shouldBe "简体中文-拼音-九宫格"
        }

        "方案名称统一映射为规范固定名称，不再保留旧名称" {
            FixedSchemata.getDisplayName("english") shouldBe "English"
            FixedSchemata.getDisplayName("english", "Easy English") shouldBe "English"

            FixedSchemata.getDisplayName("rime_mint_flypy", "薄荷拼音-小鹤双拼") shouldBe "简体中文-拼音-双拼"
            FixedSchemata.getDisplayName("double_pinyin_flypy", "小鹤双拼") shouldBe "简体中文-拼音-双拼"
            FixedSchemata.getDisplayName("luna_pinyin", "朙月拼音") shouldBe "简体中文-拼音-双拼"

            FixedSchemata.getDisplayName("t9", "薄荷九宫格") shouldBe "简体中文-拼音-九宫格"
            FixedSchemata.getDisplayName("t9", "九宫格") shouldBe "简体中文-拼音-九宫格"
        }

        "简短提示文本与空格键文字映射" {
            FixedSchemata.getShortName("english", isAsciiMode = true) shouldBe "En"
            FixedSchemata.getShortName("rime_mint_flypy", isAsciiMode = true) shouldBe "En"
            FixedSchemata.getShortName("rime_mint_flypy", isAsciiMode = false) shouldBe "双拼"
            FixedSchemata.getShortName("t9", isAsciiMode = false) shouldBe "九宫格"
        }

        "方案类型识别准确" {
            FixedSchemata.isEnglish("english") shouldBe true
            FixedSchemata.isEnglish("rime_mint_flypy") shouldBe false

            FixedSchemata.isT9("t9") shouldBe true
            FixedSchemata.isT9("rime_mint_flypy") shouldBe false

            FixedSchemata.isDoublePinyin("rime_mint_flypy") shouldBe true
            FixedSchemata.isDoublePinyin("t9") shouldBe false
            FixedSchemata.isDoublePinyin("english") shouldBe false
        }
    })
