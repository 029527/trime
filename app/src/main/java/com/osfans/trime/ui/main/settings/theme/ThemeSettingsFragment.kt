/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.data.theme.ThemePrefs
import com.osfans.trime.ui.compose.preference.PreferenceDelegateComposeFragment
import com.osfans.trime.ui.main.settings.ColorPickerDialog
import com.osfans.trime.ui.main.settings.ThemePickerDialog

class ThemeSettingsFragment : PreferenceDelegateComposeFragment(ThemeManager.prefs) {
    private enum class Picker { THEME, COLOR }

    private var picker by mutableStateOf<Picker?>(null)

    @Composable
    override fun clickHandlers(): Map<String, () -> Unit> = mapOf(
        // Neither row is a plain preference write: they ask ThemeManager / ColorManager
        // to load the theme or the colour scheme, so they get their own dialogs.
        ThemePrefs.SELECTED_THEME to { picker = Picker.THEME },
        ThemePrefs.NORMAL_MODE_COLOR to { picker = Picker.COLOR },
    )

    @Composable
    override fun Dialogs() {
        when (picker) {
            Picker.THEME -> ThemePickerDialog.ThemeSelectionDialog { picker = null }
            Picker.COLOR -> ColorPickerDialog.ColorSelectionDialog { picker = null }
            null -> Unit
        }
    }
}
