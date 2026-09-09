/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import androidx.annotation.ColorInt

/**
 * 运行时配色微调：给主题里的每个颜色叠一层「暖度 / 亮度 / 不透明度」滤镜。
 *
 * 算法照抄 trime-config 的 `tools/gen_ios_theme.py`（`gains()` / `warm()` / `translucent()`），
 * 原来是生成主题 yaml 时烘焙进颜色里的，现在搬到运行时，好让用户在设置页拖滑块直接看效果：
 *
 *  * 像真实的暖色滤色片一样**按通道相乘**：越亮的地方越偏暖，白键变暖白，冷蓝灰底转暖灰；
 *  * 纯黑（RGB 三通道全 0）不动，免得文字被补偿成脏黑；
 *  * 深色配色近黑，相乘几乎没变化，所以额外给一点整数补偿（[BASE_LIFT]）；
 *  * 只有[底色][ALPHA_KEYS]才套不透明度，文字/图标色不套，否则字会被背后的界面糊掉。
 *
 * 这里只有纯函数，参数从外面传进来（[ColorManager] 负责读 [ThemePrefs] 并缓存），
 * 所以可以直接跑单元测试，见 `ColorTintTest`。
 */
object ColorTint {
    /** WARM = 1 时浅色配色的通道增益。 */
    private val BASE_LIGHT = doubleArrayOf(0.985, 0.945, 0.885)

    /** WARM = 1 时深色配色的通道增益。 */
    private val BASE_DARK = doubleArrayOf(1.0, 0.965, 0.90)

    /** 深色配色的整数补偿（WARM = 1 时），底色 2A2A2C 往暖炭灰推。 */
    private val BASE_LIFT = intArrayOf(6, 3, 0)

    private val NO_LIFT = intArrayOf(0, 0, 0)

    /**
     * 要套不透明度的「底色」类 key，与生成脚本里的 `ALPHA_KEYS` 一致。
     * 没列进来的（文字、图标、提示、分隔线）保持主题里写的 alpha。
     */
    val ALPHA_KEYS =
        hashSetOf(
            "back_color",
            "border_color",
            "keyboard_back_color",
            "keyboard_background",
            "root_background",
            "liquid_keyboard_background",
            "key_back_color",
            "off_key_back_color",
            "on_key_back_color",
            "hilited_key_back_color",
            "hilited_off_key_back_color",
            "hilited_on_key_back_color",
            "hilited_back_color",
            "hilited_candidate_back_color",
            "func_key_back_color",
            "func_key_hilited_back_color",
            "text_back_color",
            "long_text_back_color",
            "preview_back_color",
            "enter_key_action_back_color",
            "hilited_enter_key_action_back_color",
        )

    /**
     * 三个旋钮。都是「倍数」而不是百分比：
     *
     * @param warm 暖度，0 = 不加滤镜（原来的冷白），1.0 = 基准强度，越大越暖
     * @param dim 亮度，1.0 = 不压暗，越小整体越暗
     * @param alpha 底色不透明度，1.0 = 全不透明，0.90 = 透 10%
     */
    data class Params(
        val warm: Double,
        val dim: Double,
        val alpha: Double,
    ) {
        /** 三个旋钮到这组值时滤镜输出 == 输入，等于「什么都不做」。 */
        val isNeutral: Boolean get() = warm == 0.0 && dim == 1.0 && alpha == 1.0
    }

    /** 什么都不做。 */
    val Neutral = Params(0.0, 1.0, 1.0)

    /**
     * 当前 `ios.trime.yaml` 里已经烘焙进颜色的那套值，也是设置页三个滑块的默认值。
     * 换成「未烘焙」的主题时，这组参数应当还原出和现在一样的观感。
     */
    val Baked = Params(0.26, 0.92, 0.90)

    /** 设置页存的是整数百分比，这里换算成倍数。 */
    fun of(
        warmPercent: Int,
        dimPercent: Int,
        alphaPercent: Int,
    ) = Params(warmPercent / 100.0, dimPercent / 100.0, alphaPercent / 100.0)

    /** 主题里的颜色字面量是不是自带 alpha（`0xAARRGGBB`）。只有不自带的才会被套不透明度。 */
    fun hasExplicitAlpha(value: String): Boolean {
        val digits =
            when {
                value.startsWith("0x", ignoreCase = true) -> value.substring(2)
                value.startsWith("#") -> value.substring(1)
                else -> return true // 颜色名（red、blue……）当作已经定好 alpha，不动
            }
        // 只有恰好 6 位十六进制（即 RRGGBB）才算「没写 alpha」，与生成脚本的 len(value) == 8 等价
        return digits.length != 6 || digits.any { Character.digit(it, 16) < 0 }
    }

    /** 这个 key 的颜色要不要套不透明度。 */
    fun appliesAlpha(
        key: String,
        rawValue: String,
    ): Boolean = key in ALPHA_KEYS && !hasExplicitAlpha(rawValue)

    /**
     * 给一个已经解析好的颜色上滤镜。
     *
     * @param dark 当前配色是深色配色（用 [BASE_DARK] + [BASE_LIFT]）还是浅色（用 [BASE_LIGHT]）
     * @param applyAlpha 这个颜色是不是要被 [Params.alpha] 覆盖 alpha 位，见 [appliesAlpha]
     */
    @ColorInt
    fun tint(
        @ColorInt color: Int,
        params: Params,
        dark: Boolean,
        applyAlpha: Boolean,
    ): Int {
        if (params.isNeutral) return color
        var out = color
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        if (r or g or b != 0) { // 纯黑不动
            val base = if (dark) BASE_DARK else BASE_LIGHT
            val lift = if (dark) BASE_LIFT else NO_LIFT
            out =
                (color.toLong() and 0xFF000000L).toInt() or
                (channel(r, base[0], lift[0], params) shl 16) or
                (channel(g, base[1], lift[1], params) shl 8) or
                channel(b, base[2], lift[2], params)
        }
        if (applyAlpha) {
            out = (out and 0x00FFFFFF) or (round(params.alpha * 255) shl 24)
        }
        return out
    }

    private fun channel(
        value: Int,
        base: Double,
        lift: Int,
        params: Params,
    ): Int {
        // gains()：warm_up(v) = (1 - (1 - v) * WARM) * DIM，lift 也按 WARM 缩放
        val gain = (1.0 - (1.0 - base) * params.warm) * params.dim
        return (round(value * gain) + round(lift * params.warm)).coerceIn(0, 255)
    }

    /** Python 的 `round()` 是四舍六入五取偶，`Math.rint` 同规则，这样和生成脚本逐位一致。 */
    private fun round(value: Double): Int = Math.rint(value).toInt()

    /** 相对亮度低于一半就当成深色配色。 */
    fun isDarkColor(
        @ColorInt color: Int,
    ): Boolean {
        val r = ((color ushr 16) and 0xFF) / 255f
        val g = ((color ushr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        return (r * 0.2126f + g * 0.7152f + b * 0.0722f) < 0.5f
    }
}
