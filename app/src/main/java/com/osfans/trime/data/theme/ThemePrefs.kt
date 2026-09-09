/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import com.osfans.trime.R
import com.osfans.trime.data.prefs.PreferenceDelegateEnum
import com.osfans.trime.data.prefs.PreferenceDelegateOwner

class ThemePrefs(
    sharedPrefs: SharedPreferences,
) : PreferenceDelegateOwner(sharedPrefs, R.string.theme) {
    val selectedTheme =
        string(
            R.string.selected_theme,
            SELECTED_THEME,
            "trime",
            R.string.selected_theme_summary,
        )

    val normalModeColor =
        string(
            R.string.normal_mode_color,
            NORMAL_MODE_COLOR,
            "default",
            R.string.normal_mode_color_summary,
        )

    enum class NavbarBackground(
        override val stringRes: Int,
    ) : PreferenceDelegateEnum {
        NONE(R.string.navbar_bkg_none),
        COLOR_ONLY(R.string.navbar_bkg_color_only),
        FULL(R.string.navbar_bkg_full),
    }

    val navbarBackground =
        enum(
            R.string.navbar_background,
            NAVBAR_BACKGROUND,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                NavbarBackground.FULL
            } else {
                NavbarBackground.COLOR_ONLY
            },
            enableUiOn = { Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM },
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                sharedPreferences.edit {
                    remove(this@apply.key)
                }
            }
        }

    val followSystemDayNight =
        switch(
            R.string.follow_system_day_night_color,
            FOLLOW_SYSTEM_DAY_NIGHT,
            false,
        )

    // ---- 配色微调：给主题里的每个颜色叠一层滤镜，改完立刻生效，见 [ColorTint] ----
    // 默认值 26 / 92 / 90 就是 ios.trime.yaml 现在已经烘焙进颜色里的那套，
    // 换成「未烘焙」的主题时观感不变；三个都拨到中性（0 / 100 / 100）就等于不加滤镜。

    val tintWarm = int(R.string.theme_tint_warm, THEME_TINT_WARM, 26, 0, 200, "%")

    val tintDim = int(R.string.theme_tint_dim, THEME_TINT_DIM, 92, 80, 100, "%")

    val tintAlpha = int(R.string.theme_tint_alpha, THEME_TINT_ALPHA, 90, 60, 100, "%")

    /** 把三个滑块拨回默认值。 */
    fun resetTint() {
        tintWarm.setValue(tintWarm.defaultValue)
        tintDim.setValue(tintDim.defaultValue)
        tintAlpha.setValue(tintAlpha.defaultValue)
    }

    companion object {
        const val SELECTED_THEME = "selected_theme"
        const val NORMAL_MODE_COLOR = "normal_mode_color"
        const val FOLLOW_SYSTEM_DAY_NIGHT = "follow_system_day_night"
        const val NAVBAR_BACKGROUND = "navbar_background"
        const val THEME_TINT_WARM = "theme_tint_warm"
        const val THEME_TINT_DIM = "theme_tint_dim"
        const val THEME_TINT_ALPHA = "theme_tint_alpha"
    }
}
