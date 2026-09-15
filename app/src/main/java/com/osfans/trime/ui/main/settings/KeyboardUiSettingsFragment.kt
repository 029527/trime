/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ui.compose.preference.PreferenceDelegateComposeFragment

/**
 * 外观 › 键盘界面, see [SettingsPages.KeyboardUi]. The IME still recreates its views per owner
 * (`prefs.candidates` as a group, the keyboard and gesture-inset prefs one by one), so which
 * page a row sits on changes nothing there.
 */
class KeyboardUiSettingsFragment :
    PreferenceDelegateComposeFragment(
        SettingsPages.KeyboardUi,
        AppPrefs.defaultInstance().keyboard,
        AppPrefs.defaultInstance().candidates,
        AppPrefs.defaultInstance().advanced,
    )
