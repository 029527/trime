/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.AppPrefs.Advanced
import com.osfans.trime.data.prefs.AppPrefs.Candidates
import com.osfans.trime.data.prefs.AppPrefs.Keyboard
import com.osfans.trime.data.prefs.PreferencePage
import com.osfans.trime.data.prefs.PreferencePage.Section
import com.osfans.trime.data.theme.ThemePrefs

/**
 * Which setting lives on which page. The home screen groups pages into
 * **输入** (what typing does), **外观** (what the keyboard looks like) and **数据**;
 * a page only ever belongs to one of them, whatever owner its rows are stored in.
 * The table in `docs/modern-ui-notes.md` §5 mirrors this file, and the rules every page
 * follows (sections, order, children under their switch) are written down there too.
 *
 * 语音输入 and 配置 are hand-written; the voice page shows [AppPrefs.General.PREFERRED_VOICE_INPUT].
 */
object SettingsPages {
    /** 输入 › 常规 */
    val General = PreferencePage(
        R.string.general,
        listOf(
            Section(
                R.string.settings_section_typing,
                listOf(
                    // AppPrefs.General spelled out: the [General] page below would shadow it
                    AppPrefs.General.INLINE_PREEDIT_MODE,
                    // how the code being typed is shown, next to where it is shown
                    Keyboard.USE_SOFT_CURSOR,
                    AppPrefs.General.ASCII_SWITCH_TIPS,
                    Keyboard.LANDSCAPE_SCHEMA,
                ),
            ),
            Section(
                R.string.settings_section_system,
                listOf(
                    AppPrefs.General.INLINE_SUGGESTIONS,
                    Advanced.SHOW_APP_ICON,
                ),
            ),
        ),
    )

    /** 输入 › 按键与手势 */
    val KeysAndGestures = PreferencePage(
        R.string.keys_and_gestures,
        listOf(
            Section(
                R.string.settings_section_touch,
                listOf(
                    Keyboard.EXPAND_KEYPRESS_AREA,
                    Keyboard.LONG_PRESS_TIMEOUT,
                    Keyboard.REPEAT_INTERVAL,
                    Keyboard.DOUBLE_TAP_TIMEOUT,
                ),
            ),
            Section(
                R.string.settings_section_swipe,
                listOf(
                    Keyboard.SWIPE_TRAVEL,
                    Keyboard.SWIPE_VELOCITY,
                    Keyboard.SLIDE_STEP_SIZE,
                ),
            ),
            Section(
                R.string.settings_section_ctrl,
                listOf(
                    Keyboard.HOOK_CTRL_A,
                    Keyboard.HOOK_CTRL_CV,
                    Keyboard.HOOK_CTRL_LR,
                    Keyboard.HOOK_CTRL_ZY,
                ),
            ),
            Section(
                R.string.settings_section_shift,
                listOf(
                    Keyboard.HOOK_SHIFT_SPACE,
                    Keyboard.HOOK_SHIFT_NUM,
                    Keyboard.HOOK_SHIFT_SYMBOL,
                    Keyboard.HOOK_SHIFT_ARROW,
                ),
            ),
        ),
    )

    /** 输入 › 按键反馈 */
    val KeyFeedback = PreferencePage(
        R.string.key_feedback,
        listOf(
            Section(
                R.string.settings_section_sound,
                listOf(
                    Keyboard.SOUND_ON_KEYPRESS,
                    Keyboard.KEY_SOUND_VOLUME,
                    Keyboard.USE_CUSTOM_SOUND_EFFECT,
                    Keyboard.CUSTOM_SOUND_EFFECT,
                ),
            ),
            Section(
                R.string.settings_section_vibration,
                listOf(
                    Keyboard.VIBRATE_ON_KEY_PRESS,
                    Keyboard.VIBRATE_ON_KEY_RELEASE,
                    Keyboard.VIBRATE_ON_KEY_REPEAT,
                    Keyboard.VIBRATION_EFFECT,
                    Keyboard.VIBRATION_DURATION,
                    Keyboard.VIBRATION_AMPLITUDE,
                ),
            ),
            Section(
                R.string.settings_section_speech,
                listOf(
                    Keyboard.SPEAK_ON_KEYPRESS,
                    Keyboard.SPEAK_ON_COMMIT,
                ),
            ),
        ),
    )

    /** 输入 › 剪贴板. Every row below the first depends on it, so it leads the page. */
    val Clipboard = PreferencePage(
        R.string.clipboard,
        listOf(
            Section(
                R.string.settings_section_history,
                listOf(
                    AppPrefs.Clipboard.CLIPBOARD_LISTENING,
                    AppPrefs.Clipboard.CLIPBOARD_LIMIT,
                ),
            ),
            Section(
                R.string.settings_section_paste,
                listOf(
                    AppPrefs.Clipboard.CLIPBOARD_SUGGESTION,
                    AppPrefs.Clipboard.CLIPBOARD_SUGGESTION_TIMEOUT,
                    AppPrefs.Clipboard.CLIPBOARD_RETURN_AFTER_PASTE,
                ),
            ),
            Section(
                R.string.settings_section_clipboard_rules,
                listOf(
                    AppPrefs.Clipboard.CLIPBOARD_COMPARE_RULES,
                    AppPrefs.Clipboard.CLIPBOARD_OUTPUT_RULES,
                ),
            ),
        ),
    )

    /** 外观 › 配色. The try-it field and the reset row are the fragment's footer, after the sliders. */
    val Theme = PreferencePage(
        R.string.theme,
        listOf(
            Section(
                R.string.settings_section_color_mode,
                listOf(
                    // the day / night choice is greyed out while the wallpaper colours are on
                    ThemePrefs.FOLLOW_WALLPAPER,
                    ThemePrefs.DAY_NIGHT_MODE,
                    ThemePrefs.NAVBAR_BACKGROUND,
                ),
            ),
            Section(
                R.string.settings_section_tuning,
                listOf(
                    ThemePrefs.THEME_TINT_WARM,
                    ThemePrefs.THEME_TINT_DIM,
                    ThemePrefs.THEME_TINT_ALPHA,
                ),
            ),
        ),
    )

    /** 外观 › 键盘界面 */
    val KeyboardUi = PreferencePage(
        R.string.keyboard_ui,
        listOf(
            Section(
                R.string.settings_section_keys_and_bar,
                listOf(
                    Keyboard.HIDE_INPUT_BAR,
                    Keyboard.HIDE_KEY_SYMBOL,
                    Keyboard.HIDE_KEY_HINT,
                    Keyboard.POPUP_ON_KEY_PRESS,
                ),
            ),
            Section(
                R.string.candidates_window,
                listOf(
                    Candidates.MODE,
                    Candidates.LAYOUT,
                    Candidates.POSITION,
                ),
            ),
            Section(
                R.string.settings_section_landscape,
                listOf(
                    Keyboard.LANDSCAPE_FULLSCREEN,
                    Keyboard.LANDSCAPE_MODE,
                    Keyboard.SPLIT_SPACE_PERCENT,
                ),
            ),
            Section(
                R.string.settings_section_floating,
                listOf(
                    Keyboard.LANDSCAPE_FLOATING,
                    Keyboard.LANDSCAPE_FLOATING_SIZE,
                    Keyboard.LANDSCAPE_FLOATING_WIDTH,
                    Keyboard.LANDSCAPE_FLOATING_HEIGHT,
                    Keyboard.LANDSCAPE_FLOATING_MARGIN,
                ),
            ),
            Section(
                R.string.settings_section_insets,
                listOf(
                    Keyboard.LAYOUT_IN_DISPLAY_CUTOUT,
                    Advanced.IGNORE_SYSTEM_GESTURE_INSETS,
                ),
            ),
        ),
    )

    val all = listOf(General, KeysAndGestures, KeyFeedback, Clipboard, Theme, KeyboardUi)
}
