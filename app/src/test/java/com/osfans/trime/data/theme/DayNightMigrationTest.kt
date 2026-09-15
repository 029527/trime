/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.osfans.trime.data.theme.DayNightMigration.FOLLOW_SYSTEM_DAY_NIGHT
import com.osfans.trime.data.theme.DayNightMigration.NORMAL_MODE_COLOR
import com.osfans.trime.data.theme.DayNightMigration.SELECTED_THEME
import com.osfans.trime.data.theme.ThemePrefs.DayNightMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DayNightMigrationTest :
    FunSpec({
        fun prefsOf(vararg entries: Pair<String, Any>) = MemoryPrefs(mutableMapOf(*entries))

        fun MemoryPrefs.migrated(): MemoryPrefs = also { DayNightMigration.migrate(it) }

        test("follow_system_day_night=true → 跟随系统，旧 key 删除") {
            val prefs = prefsOf(FOLLOW_SYSTEM_DAY_NIGHT to true, SELECTED_THEME to "ios.trime").migrated()
            prefs.contains(ThemePrefs.DAY_NIGHT_MODE) shouldBe false // 跟随系统就是默认值
            prefs.values.keys shouldBe emptySet()
        }

        test("跟随系统优先于配色方案") {
            val prefs = prefsOf(FOLLOW_SYSTEM_DAY_NIGHT to true, NORMAL_MODE_COLOR to "ios_dark").migrated()
            prefs.contains(ThemePrefs.DAY_NIGHT_MODE) shouldBe false
            prefs.values.keys shouldBe emptySet()
        }

        test("不跟随系统、选过深色方案 → 深色") {
            val prefs = prefsOf(FOLLOW_SYSTEM_DAY_NIGHT to false, NORMAL_MODE_COLOR to "ios_dark").migrated()
            prefs.values shouldBe mapOf(ThemePrefs.DAY_NIGHT_MODE to "DARK")
        }

        test("选过浅色方案 → 浅色；default 和不认识的方案也按浅色") {
            for (scheme in listOf("ios_light", "default", "ink")) {
                prefsOf(NORMAL_MODE_COLOR to scheme).migrated().values shouldBe
                    mapOf(ThemePrefs.DAY_NIGHT_MODE to "LIGHT")
            }
        }

        test("两个都没存过 → 跟随系统，什么都不写") {
            val prefs = prefsOf("theme_tint_dim" to 100).migrated()
            prefs.values shouldBe mapOf("theme_tint_dim" to 100)
        }

        test("只存过 follow_system_day_night=false → 跟随系统") {
            prefsOf(FOLLOW_SYSTEM_DAY_NIGHT to false).migrated().values shouldBe emptyMap()
        }

        test("已经有新偏好时不覆盖，只清旧 key") {
            val prefs = prefsOf(ThemePrefs.DAY_NIGHT_MODE to "DARK", NORMAL_MODE_COLOR to "ios_light").migrated()
            prefs.values shouldBe mapOf(ThemePrefs.DAY_NIGHT_MODE to "DARK")
        }

        test("resolve 的判定规则") {
            DayNightMigration.resolve(null, null) shouldBe DayNightMode.FOLLOW_SYSTEM
            DayNightMigration.resolve(true, "ios_light") shouldBe DayNightMode.FOLLOW_SYSTEM
            DayNightMigration.resolve(false, "ios_dark") shouldBe DayNightMode.DARK
            DayNightMigration.resolve(null, "ios_light") shouldBe DayNightMode.LIGHT
        }

        test("App 的 ui_mode 并入深浅色：只删旧 key，不改键盘已有的深浅色") {
            val prefs = prefsOf(ThemePrefs.DAY_NIGHT_MODE to "LIGHT", DayNightMigration.UI_MODE to "DARK").migrated()
            prefs.values shouldBe mapOf(ThemePrefs.DAY_NIGHT_MODE to "LIGHT")
        }

        test("只存过 ui_mode → 跟随系统（默认值），什么都不写") {
            prefsOf(DayNightMigration.UI_MODE to "DARK").migrated().values shouldBe emptyMap()
        }

        test("升级后：深浅色保留，跟随壁纸取色默认关闭且不写入") {
            val prefs = prefsOf(ThemePrefs.DAY_NIGHT_MODE to "DARK", DayNightMigration.UI_MODE to "AUTO")
            val theme = ThemePrefs(prefs)
            theme.dayNightMode.getValue() shouldBe DayNightMode.DARK
            theme.followWallpaper.getValue() shouldBe false
            theme.isFollowingWallpaper shouldBe false
            prefs.contains(DayNightMigration.UI_MODE) shouldBe false
            prefs.contains(ThemePrefs.FOLLOW_WALLPAPER) shouldBe false
        }

        test("App 的深浅色：跟随壁纸时跟随系统，否则跟键盘的选择") {
            ThemePrefs.appNightMode(true, DayNightMode.LIGHT) shouldBe AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemePrefs.appNightMode(true, DayNightMode.DARK) shouldBe AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemePrefs.appNightMode(false, DayNightMode.LIGHT) shouldBe AppCompatDelegate.MODE_NIGHT_NO
            ThemePrefs.appNightMode(false, DayNightMode.DARK) shouldBe AppCompatDelegate.MODE_NIGHT_YES
            ThemePrefs.appNightMode(false, DayNightMode.FOLLOW_SYSTEM) shouldBe AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

        test("内置配色的深浅按底色判断，旧方案 id 也认") {
            DayNightMigration.isBuiltinDarkScheme("ios_dark") shouldBe true
            DayNightMigration.isBuiltinDarkScheme("ios_light") shouldBe false
            DayNightMigration.isBuiltinDarkScheme("no_such_scheme") shouldBe false
            DayNightMigration.isBuiltinDarkScheme("dark") shouldBe true
            DayNightMigration.isBuiltinDarkScheme("light") shouldBe false
        }
    })

/** Just enough of [SharedPreferences] for the migration. */
private class MemoryPrefs(
    val values: MutableMap<String, Any>,
) : SharedPreferences {
    override fun getAll(): Map<String, *> = values

    override fun getString(
        key: String,
        defValue: String?,
    ): String? = values[key] as String? ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(
        key: String,
        defValues: Set<String>?,
    ): Set<String>? = values[key] as Set<String>? ?: defValues

    override fun getInt(
        key: String,
        defValue: Int,
    ): Int = values[key] as Int? ?: defValue

    override fun getLong(
        key: String,
        defValue: Long,
    ): Long = values[key] as Long? ?: defValue

    override fun getFloat(
        key: String,
        defValue: Float,
    ): Float = values[key] as Float? ?: defValue

    override fun getBoolean(
        key: String,
        defValue: Boolean,
    ): Boolean = values[key] as Boolean? ?: defValue

    override fun contains(key: String): Boolean = key in values

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val puts = mutableMapOf<String, Any>()
        private val removes = mutableSetOf<String>()

        override fun putString(
            key: String,
            value: String?,
        ) = apply { if (value == null) removes += key else puts[key] = value }

        override fun putStringSet(
            key: String,
            values: Set<String>?,
        ) = apply { if (values == null) removes += key else puts[key] = values }

        override fun putInt(
            key: String,
            value: Int,
        ) = apply { puts[key] = value }

        override fun putLong(
            key: String,
            value: Long,
        ) = apply { puts[key] = value }

        override fun putFloat(
            key: String,
            value: Float,
        ) = apply { puts[key] = value }

        override fun putBoolean(
            key: String,
            value: Boolean,
        ) = apply { puts[key] = value }

        override fun remove(key: String) = apply { removes += key }

        override fun clear() = apply { removes += values.keys }

        override fun commit(): Boolean {
            removes.forEach { values.remove(it) }
            values.putAll(puts)
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
