/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.dictation

import androidx.compose.ui.unit.IntOffset
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

class DictationPillPlacementTest :
    FunSpec({
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

        fun map(
            values: FloatArray,
            x: Float,
            y: Float,
        ) = FloatArray(2).also { DictationPillPlacement.mapPoint(values, x, y, it) }.toList()

        test("矩阵：单位矩阵原样返回") {
            map(identity, 12f, 34f) shouldBe listOf(12f, 34f)
        }

        test("矩阵：编辑框在屏幕上的平移和缩放") {
            // view drawn at 2x, its origin at (10, 20) on screen
            val m = floatArrayOf(2f, 0f, 10f, 0f, 2f, 20f, 0f, 0f, 1f)
            map(m, 5f, 5f) shouldBe listOf(20f, 30f)
        }

        test("矩阵：带透视分量时除以 w") {
            val m = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 2f)
            val (x, y) = map(m, 100f, 50f)
            x shouldBe (50f plusOrMinus 0.001f)
            y shouldBe (25f plusOrMinus 0.001f)
        }

        // a 1080 px wide screen, keyboard from y = 1300, pill 120 x 76, gap 10, margin 20
        fun place(
            x: Float,
            top: Float,
            bottom: Float,
            keyboardLeft: Int = 0,
        ) = DictationPillPlacement.place(x, top, bottom, 120, 76, 1080, keyboardLeft, 1300, 10, 20)

        test("紧跟光标右边，和光标所在行垂直居中") {
            place(500f, 400f, 460f) shouldBe IntOffset(510, 392)
        }

        test("光标贴右边：胶囊不出屏") {
            place(1050f, 400f, 460f) shouldBe IntOffset(940, 392)
        }

        test("光标贴左边、贴顶：留出边距") {
            place(0f, 0f, 30f) shouldBe IntOffset(20, 20)
        }

        test("光标就在键盘上沿：胶囊不压键盘") {
            place(500f, 1250f, 1290f) shouldBe IntOffset(510, 1204)
        }

        test("拿不到光标：退回键盘左上角上方") {
            place(Float.NaN, Float.NaN, Float.NaN) shouldBe IntOffset(20, 1204)
        }

        test("光标被键盘挡住或滚出屏幕：退回") {
            place(500f, 1350f, 1400f) shouldBe IntOffset(20, 1204)
            place(500f, -80f, -20f) shouldBe IntOffset(20, 1204)
            place(-5f, 400f, 460f) shouldBe IntOffset(20, 1204)
        }

        test("浮动键盘：退回位置对齐键盘左边") {
            place(Float.NaN, Float.NaN, Float.NaN, keyboardLeft = 600) shouldBe IntOffset(620, 1204)
        }
    })
