/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import android.content.SharedPreferences
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegateOwner
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.lang.reflect.Proxy

/**
 * 设置页按「输入 / 外观 / 数据」重排时，偏好只换了页，存储不能动：
 * key 字符串一个都不能改，每一行恰好出现在一个页面上，互相依赖（置灰）的行在同一页。
 */
class SettingsPagesTest :
    StringSpec({
        // 建 owner 时不读偏好；真读了说明测试写错了
        val shared = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, _ -> error("unexpected SharedPreferences.${method.name}") } as SharedPreferences

        val general = AppPrefs.General(shared)
        val keyboard = AppPrefs.Keyboard(shared)
        val candidates = AppPrefs.Candidates(shared)
        val advanced = AppPrefs.Advanced(shared)
        val owners = listOf<PreferenceDelegateOwner>(general, keyboard, candidates, advanced)

        fun PreferenceDelegateOwner.rowKeys() = preferenceDelegatesUi.map { it.key }

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
        }

        "每一行恰好在一个页面上" {
            val shown = SettingsPages.all.flatMap { it.keys } + handWritten
            withClue("重复出现") { shown.groupBy { it }.filterValues { it.size > 1 }.keys.shouldBeEmpty() }
            withClue("哪页都没有") { (owners.flatMap { it.rowKeys() } - shown.toSet()).shouldBeEmpty() }
            withClue("页面上有、owner 里没有") { (shown - owners.flatMap { it.rowKeys() }.toSet()).shouldBeEmpty() }
        }

        "每页都能在它的 owner 里找到全部行，没有空段" {
            SettingsPages.all.forEach { page ->
                val sections = page.resolve(owners)
                sections.sumOf { it.rows.size } shouldBe page.keys.size
                sections.size shouldBe page.sections.size
            }
        }

        "有依赖（置灰）的行在同一页" {
            val groups = listOf(
                listOf(
                    AppPrefs.Keyboard.LANDSCAPE_FLOATING_SIZE,
                    AppPrefs.Keyboard.LANDSCAPE_FLOATING_WIDTH,
                    AppPrefs.Keyboard.LANDSCAPE_FLOATING_HEIGHT,
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
            )
            groups.forEach { group ->
                withClue(group) {
                    SettingsPages.all.count { page -> page.keys.containsAll(group) } shouldBe 1
                }
            }
        }
    })
