/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import android.content.SharedPreferences
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegateOwner
import com.osfans.trime.data.theme.ThemePrefs
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.lang.reflect.Proxy

/**
 * 设置页重排时，偏好只换了页，存储不能动：key 字符串一个都不能改，每一行恰好出现在一个页面上，
 * 互相依赖（置灰）的行在同一页、紧跟在开关下面。页面本身按 `docs/modern-ui-notes.md` §5 的原则分段。
 */
class SettingsPagesTest :
    StringSpec({
        // 建 owner 时不读偏好（ThemePrefs 的迁移只问一句 contains）；真读了说明测试写错了
        val shared = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, _ ->
            if (method.name == "contains") false else error("unexpected SharedPreferences.${method.name}")
        } as SharedPreferences

        val general = AppPrefs.General(shared)
        val keyboard = AppPrefs.Keyboard(shared)
        val candidates = AppPrefs.Candidates(shared)
        val advanced = AppPrefs.Advanced(shared)
        val clipboard = AppPrefs.Clipboard(shared)
        val theme = ThemePrefs(shared)
        val owners = listOf<PreferenceDelegateOwner>(general, keyboard, candidates, advanced, clipboard, theme)

        fun PreferenceDelegateOwner.rowKeys() = preferenceDelegatesUi.map { it.key }

        // 画出来的行（JVM 上 SDK_INT 是 0，「跟随壁纸」只注册不画）
        val rowKeys = owners.flatMap { it.rowKeys() }
        val delegateKeys = owners.flatMap { it.preferenceDelegates.keys }.toSet()

        // 语音输入页是手写的，直接画这一行
        val handWritten = listOf(AppPrefs.General.PREFERRED_VOICE_INPUT)

        "偏好 key 跟重排前一样（重排前从 AppPrefs 抄下来的）" {
            general.rowKeys() shouldBe listOf(
                "inline_preedit_mode",
                "ascii_switch_tips",
                "inline_suggestions",
                "preferred_voice_input",
            )
            keyboard.rowKeys() shouldBe listOf(
                "keyboard_landscape_mode", "keyboard_split_space", "keyboard_landscape_schema",
                "keyboard_landscape_fullscreen", "keyboard_landscape_floating", "keyboard_landscape_floating_size",
                "keyboard_landscape_floating_width", "keyboard_landscape_floating_height",
                "keyboard_landscape_floating_margin", "use_soft_cursor", "hide_input_bar", "hide_key_symbol",
                "hide_key_hint", "sound_on_keypress", "sound_volume", "custom_sound_effect_enabled",
                "custom_sound_effect_name", "vibrate_on_key_press", "vibrate_on_key_release", "vibrate_on_key_repeat",
                "vibration_effect", "vibration_duration", "vibration_amplitude", "speak_on_keypress",
                "speak_on_commit", "show_key_popup", "expand_keypress_area", "layout_in_display_cutout",
                "key_swipe_travel", "key_swipe_velocity", "key_long_press_timeout", "key_repeat_interval",
                "key_double_tap_timeout", "key_slide_step_size", "hook_ctrl_a", "hook_ctrl_cv", "hook_ctrl_lr",
                "hook_ctrl_zy", "hook_shift_space", "hook_shift_num", "hook_shift_symbol", "hook_shift_arrow",
            )
            candidates.rowKeys() shouldBe listOf(
                "show_candidates_window",
                "candidates_layout",
                "candidates_window_position",
            )
            advanced.rowKeys() shouldBe listOf("show_app_icon", "ignore_system_gesture_insets")
            clipboard.rowKeys() shouldBe listOf(
                "clipboard_listening",
                "clipboard_clipboard_limit",
                "clipboard_clipboard_compare",
                "clipboard_clipboard_output",
                "clipboard_suggestion",
                "clipboard_suggestion_timeout",
                "clipboard_return_after_paste",
            )
            (theme.rowKeys() + ThemePrefs.FOLLOW_WALLPAPER).toSet() shouldBe setOf(
                "day_night_mode",
                "theme_follow_wallpaper",
                "navbar_background",
                "theme_tint_warm",
                "theme_tint_dim",
                "theme_tint_alpha",
            )
        }

        "每一行恰好在一个页面上（渲染器页面 + 手写页面画的行）" {
            val shown = SettingsPages.all.flatMap { it.keys } + handWritten
            withClue("重复出现") { shown.groupBy { it }.filterValues { it.size > 1 }.keys.shouldBeEmpty() }
            withClue("哪页都没有") { (rowKeys - shown.toSet()).shouldBeEmpty() }
            withClue("页面上有、owner 里没有") { (shown - delegateKeys).shouldBeEmpty() }
        }

        "每页都能在它的 owner 里找到全部行，没有空段" {
            SettingsPages.all.forEach { page ->
                withClue(page.keys.first()) {
                    val sections = page.resolve(owners)
                    sections.sumOf { it.rows.size } shouldBe page.keys.count { it in rowKeys }
                    sections.size shouldBe page.sections.size
                }
            }
        }

        "分段：多于 4 行的页面每段都有标题，每段至少两行，段标题不重复页标题" {
            SettingsPages.all.forEach { page ->
                withClue(page.keys.first()) {
                    page.sections.forEach { section ->
                        if (page.keys.size > 4) section.title shouldNotBe 0
                        (section.keys.size >= 2) shouldBe true
                        section.title shouldNotBe page.title
                    }
                }
            }
        }

        "有依赖（置灰）的行在同一页，连续排列，开关在最前" {
            val groups = listOf(
                listOf(AppPrefs.Keyboard.LANDSCAPE_MODE, AppPrefs.Keyboard.SPLIT_SPACE_PERCENT),
                listOf(
                    AppPrefs.Keyboard.LANDSCAPE_FLOATING,
                    AppPrefs.Keyboard.LANDSCAPE_FLOATING_SIZE,
                    AppPrefs.Keyboard.LANDSCAPE_FLOATING_WIDTH,
                    AppPrefs.Keyboard.LANDSCAPE_FLOATING_HEIGHT,
                    AppPrefs.Keyboard.LANDSCAPE_FLOATING_MARGIN,
                ),
                listOf(
                    AppPrefs.Keyboard.SOUND_ON_KEYPRESS,
                    AppPrefs.Keyboard.KEY_SOUND_VOLUME,
                    AppPrefs.Keyboard.USE_CUSTOM_SOUND_EFFECT,
                    AppPrefs.Keyboard.CUSTOM_SOUND_EFFECT,
                ),
                listOf(
                    AppPrefs.Keyboard.VIBRATE_ON_KEY_PRESS,
                    AppPrefs.Keyboard.VIBRATE_ON_KEY_RELEASE,
                    AppPrefs.Keyboard.VIBRATE_ON_KEY_REPEAT,
                    AppPrefs.Keyboard.VIBRATION_EFFECT,
                    AppPrefs.Keyboard.VIBRATION_DURATION,
                    AppPrefs.Keyboard.VIBRATION_AMPLITUDE,
                ),
                listOf(
                    AppPrefs.Candidates.MODE,
                    AppPrefs.Candidates.LAYOUT,
                    AppPrefs.Candidates.POSITION,
                ),
                listOf(
                    AppPrefs.Clipboard.CLIPBOARD_LISTENING,
                    AppPrefs.Clipboard.CLIPBOARD_LIMIT,
                    AppPrefs.Clipboard.CLIPBOARD_SUGGESTION,
                    AppPrefs.Clipboard.CLIPBOARD_SUGGESTION_TIMEOUT,
                    AppPrefs.Clipboard.CLIPBOARD_RETURN_AFTER_PASTE,
                    AppPrefs.Clipboard.CLIPBOARD_COMPARE_RULES,
                    AppPrefs.Clipboard.CLIPBOARD_OUTPUT_RULES,
                ),
                listOf(ThemePrefs.FOLLOW_WALLPAPER, ThemePrefs.DAY_NIGHT_MODE),
            )
            groups.forEach { group ->
                withClue(group) {
                    val pages = SettingsPages.all.filter { page -> page.keys.containsAll(group) }
                    pages.size shouldBe 1
                    val keys = pages.single().keys
                    val start = keys.indexOf(group.first())
                    keys.subList(start, start + group.size) shouldBe group
                }
            }
        }
    })
