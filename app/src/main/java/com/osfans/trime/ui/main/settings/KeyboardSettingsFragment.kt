/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ui.compose.preference.PreferenceDelegateComposeFragment

/**
 * 输入 › 按键与手势, see [SettingsPages.KeysAndGestures]. Still the `VirtualKeyboard` route:
 * routes are parcelled into the keyboard's intents, so they keep their names.
 */
class KeyboardSettingsFragment :
    PreferenceDelegateComposeFragment(
        SettingsPages.KeysAndGestures,
        AppPrefs.defaultInstance().keyboard,
    )
