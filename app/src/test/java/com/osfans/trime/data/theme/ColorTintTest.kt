/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * 基准值取自 trime-config 的 `tools/gen_ios_theme.py` 在 WARM=0.26 / DIM=0.92 / ALPHA=0.90
 * 下烘焙出来的 `ios.trime.yaml`：运行时滤镜必须和生成脚本算出一样的颜色，
 * 否则换成「未烘焙」的主题后观感会变。
 */
class ColorTintTest :
    StringSpec({

        fun light(
            color: Int,
            applyAlpha: Boolean = false,
        ) = ColorTint.tint(color, ColorTint.Baked, dark = false, applyAlpha = applyAlpha)

        fun dark(
            color: Int,
            applyAlpha: Boolean = false,
        ) = ColorTint.tint(color, ColorTint.Baked, dark = true, applyAlpha = applyAlpha)

        "浅色：白键 FFFFFF 变暖白 EAE7E4" {
            light(0xFFFFFFFF.toInt()) shouldBe 0xFFEAE7E4.toInt()
        }

        "浅色：键盘底 D1D3D9 变暖灰 C0BFC2" {
            light(0xFFD1D3D9.toInt()) shouldBe 0xFFC0BFC2.toInt()
        }

        "浅色：功能键 ACB1BA 变 9EA1A6" {
            light(0xFFACB1BA.toInt()) shouldBe 0xFF9EA1A6.toInt()
        }

        "深色：底色 2A2A2C 变暖炭灰 292727" {
            dark(0xFF2A2A2C.toInt()) shouldBe 0xFF292727.toInt()
        }

        "纯黑不动（免得文字被补偿成脏黑）" {
            light(0xFF000000.toInt()) shouldBe 0xFF000000.toInt()
            dark(0xFF000000.toInt()) shouldBe 0xFF000000.toInt()
        }

        "全透明色不动" {
            light(0x00000000) shouldBe 0x00000000
        }

        "底色套不透明度：0.90 × 255 = E6" {
            light(0xFFD1D3D9.toInt(), applyAlpha = true) shouldBe 0xE6C0BFC2.toInt()
        }

        "不套不透明度时 alpha 位原样保留" {
            light(0x80D1D3D9.toInt()) shouldBe 0x80C0BFC2.toInt()
        }

        "中性参数 = 什么都不做" {
            ColorTint.Neutral.isNeutral shouldBe true
            for (color in listOf(0xFFFFFFFF.toInt(), 0xFFD1D3D9.toInt(), 0xFF2A2A2C.toInt(), 0x66ABCDEF)) {
                ColorTint.tint(color, ColorTint.Neutral, dark = false, applyAlpha = true) shouldBe color
                ColorTint.tint(color, ColorTint.Neutral, dark = true, applyAlpha = true) shouldBe color
            }
        }

        "of() 把整数百分比换算成烘焙用的那组倍数" {
            ColorTint.of(26, 92, 90) shouldBe ColorTint.Baked
            ColorTint.of(0, 100, 100) shouldBe ColorTint.Neutral
        }

        "只有不带 alpha 的六位颜色才会被套不透明度" {
            ColorTint.hasExplicitAlpha("0xD1D3D9") shouldBe false
            ColorTint.hasExplicitAlpha("#D1D3D9") shouldBe false
            ColorTint.hasExplicitAlpha("0x00D1D3D9") shouldBe true
            ColorTint.hasExplicitAlpha("red") shouldBe true
            ColorTint.appliesAlpha("keyboard_back_color", "0xD1D3D9") shouldBe true
            ColorTint.appliesAlpha("keyboard_back_color", "0x00D1D3D9") shouldBe false
            ColorTint.appliesAlpha("key_text_color", "0x000000") shouldBe false
        }

        "深色配色靠亮度判定" {
            ColorTint.isDarkColor(0xFF2A2A2C.toInt()) shouldBe true
            ColorTint.isDarkColor(0xFFD1D3D9.toInt()) shouldBe false
            // 已经烘焙过的 yaml（底色 E6C0BFC2）仍然要判成浅色
            ColorTint.isDarkColor(0xE6C0BFC2.toInt()) shouldBe false
        }

        "暖度拉到 200 比 26 更暖：红通道最高、蓝通道最低" {
            val hot = ColorTint.tint(0xFFFFFFFF.toInt(), ColorTint.of(200, 100, 100), false, false)
            val r = (hot ushr 16) and 0xFF
            val g = (hot ushr 8) and 0xFF
            val b = hot and 0xFF
            (r > g) shouldBe true
            (g > b) shouldBe true
        }
    })
