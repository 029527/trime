/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.theme

import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.data.theme.ThemePrefs
import com.osfans.trime.ui.compose.preference.PreferenceDelegateComposeFragment
import com.osfans.trime.ui.main.settings.ColorPickerDialog
import com.osfans.trime.ui.main.settings.ThemePickerDialog
import kotlinx.coroutines.launch

class ThemeSettingsFragment : PreferenceDelegateComposeFragment(ThemeManager.prefs) {
    @Composable
    override fun clickHandlers(): Map<String, () -> Unit> = mapOf(
        // Both are still the old AlertDialogs: they don't just write a preference,
        // they ask ThemeManager to load the theme / colour scheme.
        ThemePrefs.SELECTED_THEME to {
            // ThemePickerDialog.build is suspending (it enumerates the theme files).
            lifecycleScope.launch { ThemePickerDialog.build(lifecycleScope, requireContext()).show() }
            Unit
        },
        ThemePrefs.NORMAL_MODE_COLOR to {
            ColorPickerDialog.build(lifecycleScope, requireContext()).show()
        },
    )
}
