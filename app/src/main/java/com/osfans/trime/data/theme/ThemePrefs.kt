/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import com.osfans.trime.R
import com.osfans.trime.data.prefs.PreferenceDelegateEnum
import com.osfans.trime.data.prefs.PreferenceDelegateOwner

/**
 * Look of the keyboard and of the settings app alike: one light / dark preference and one
 * wallpaper colour switch drive both, so the two can never disagree.
 */
class ThemePrefs(
    sharedPrefs: SharedPreferences,
) : PreferenceDelegateOwner(sharedPrefs, R.string.theme) {
    init {
        // 旧版的「配色方案 + 跟随系统夜间模式」换算成下面的三选一，App 的 ui_mode 并入它，必须在读 dayNightMode 之前做
        DayNightMigration.migrate(sharedPrefs)
    }

    /** Which of the built-in colour schemes the keyboard uses, and whether the app is light or dark. */
    enum class DayNightMode(
        override val stringRes: Int,
    ) : PreferenceDelegateEnum {
        FOLLOW_SYSTEM(R.string.day_night_follow_system),
        LIGHT(R.string.day_night_light),
        DARK(R.string.day_night_dark),
    }

    /** Greyed out while [followWallpaper] is on: the wallpaper palette has its own light and dark, picked by the system. */
    val dayNightMode =
        enum(
            R.string.day_night_mode,
            DAY_NIGHT_MODE,
            DayNightMode.FOLLOW_SYSTEM,
            enableUiOn = { !isFollowingWallpaper },
        )

    /**
     * Material You: colours from the wallpaper instead of the neutral schemes, for the keyboard and
     * the settings app. Only offered on Android 12+, where the platform exposes the wallpaper palette.
     */
    val followWallpaper =
        switch(
            R.string.follow_wallpaper_colors,
            FOLLOW_WALLPAPER,
            false,
            R.string.follow_wallpaper_colors_summary,
            visible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        )

    /** [followWallpaper], and the device can actually do it. */
    val isFollowingWallpaper: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && followWallpaper.getValue()

    /** The `AppCompatDelegate` night mode the settings app runs in, see [appNightMode]. */
    val appNightMode: Int
        get() = appNightMode(isFollowingWallpaper, dayNightMode.getValue())

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

    // ---- 配色微调：给主题里的每个颜色叠一层滤镜，改完立刻生效，见 [ColorTint] ----
    // 区间刻意给得宽：100% 只是「基准强度」而不是上限，亮度可以超过 100% 提亮
    // （通道相乘后 coerceIn(0, 255) 兜底，过曝也只是压成白色，不会出错）。
    // 三个都拨到中性（0 / 100 / 100）就等于不加滤镜；跟随壁纸取色时同样叠加在壁纸配色上。

    // 步长 2：0..300 逐 1 有 300 档，超过 256 档会被渲染成输入框而不是滑块，就没法边拖边看了
    val tintWarm = int(R.string.theme_tint_warm, THEME_TINT_WARM, 26, 0, 300, "%", step = 2)

    val tintDim = int(R.string.theme_tint_dim, THEME_TINT_DIM, 92, 50, 150, "%")

    val tintAlpha = int(R.string.theme_tint_alpha, THEME_TINT_ALPHA, 90, 20, 100, "%")

    /** 把三个滑块拨回默认值。 */
    fun resetTint() {
        tintWarm.setValue(tintWarm.defaultValue)
        tintDim.setValue(tintDim.defaultValue)
        tintAlpha.setValue(tintAlpha.defaultValue)
    }

    companion object {
        const val DAY_NIGHT_MODE = "day_night_mode"
        const val FOLLOW_WALLPAPER = "theme_follow_wallpaper"
        const val NAVBAR_BACKGROUND = "navbar_background"
        const val THEME_TINT_WARM = "theme_tint_warm"
        const val THEME_TINT_DIM = "theme_tint_dim"
        const val THEME_TINT_ALPHA = "theme_tint_alpha"

        /**
         * Night mode of the settings app. Following the wallpaper means following the system's light
         * or dark too, as the keyboard does; otherwise the app takes the same choice as the keyboard.
         */
        fun appNightMode(
            followingWallpaper: Boolean,
            mode: DayNightMode,
        ): Int = when {
            followingWallpaper -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            mode == DayNightMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            mode == DayNightMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }
}
