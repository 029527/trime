/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ui.compose.preference.PreferenceDelegateComposeFragment

/** 输入 › 按键反馈, see [SettingsPages.KeyFeedback]. */
class KeyFeedbackSettingsFragment :
    PreferenceDelegateComposeFragment(
        SettingsPages.KeyFeedback,
        AppPrefs.defaultInstance().keyboard,
    ) {
    private var showSoundEffectPicker by mutableStateOf(false)

    @Composable
    override fun clickHandlers(): Map<String, () -> Unit> = mapOf(
        // Picking a sound effect is not a plain preference write, it asks
        // SoundEffectManager to switch profile.
        AppPrefs.Keyboard.CUSTOM_SOUND_EFFECT to { showSoundEffectPicker = true },
    )

    @Composable
    override fun Dialogs() {
        if (showSoundEffectPicker) {
            SoundEffectPickerDialog.SoundEffectSelectionDialog { showSoundEffectPicker = false }
        }
    }
}
