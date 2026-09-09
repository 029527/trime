/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ui.compose.preference.PreferenceDelegateComposeFragment

/**
 * The virtual keyboard settings page — the reference implementation of a
 * Compose-rendered settings screen. See `docs/modern-ui-notes.md`.
 */
class KeyboardSettingsFragment : PreferenceDelegateComposeFragment(AppPrefs.defaultInstance().keyboard) {
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
