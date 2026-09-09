// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.main.settings

import android.app.AlertDialog
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ui.compose.preference.NoticeDialog
import com.osfans.trime.ui.compose.preference.SingleChoiceDialog
import kotlinx.coroutines.launch

/**
 * The colour schemes the active theme ships with. Picking one asks [ColorManager] to
 * load it, so this is not a plain preference write either.
 *
 * [build] is the platform dialog the keyboard shows over its own window;
 * [ColorSelectionDialog] is the Material 3 one the settings page uses.
 */
object ColorPickerDialog {
    fun build(
        scope: LifecycleCoroutineScope,
        context: Context,
        afterConfirm: (suspend () -> Unit)? = null,
    ): AlertDialog {
        val presetSchemes = ThemeManager.activeTheme.colorSchemes
        val currentScheme = ColorManager.activeColorScheme
        val currentIndex = presetSchemes.indexOfFirst { it.id == currentScheme.id }
        return AlertDialog
            .Builder(context)
            .apply {
                setTitle(R.string.normal_mode_color)
                if (presetSchemes.isEmpty()) {
                    setMessage(R.string.no_color_to_select)
                } else {
                    setSingleChoiceItems(
                        presetSchemes.map { it.colors["name"] }.toTypedArray(),
                        currentIndex,
                    ) { dialog, which ->
                        scope.launch {
                            afterConfirm?.invoke()
                            if (which != currentIndex) {
                                ColorManager.setColorScheme(presetSchemes[which])
                            }
                            dialog.dismiss()
                        }
                    }
                }
                setNegativeButton(android.R.string.cancel, null)
            }.create()
    }

    /** The Material 3 face of [build], for the theme settings page. */
    @Composable
    fun ColorSelectionDialog(onDismiss: () -> Unit) {
        val presetSchemes = ThemeManager.activeTheme.colorSchemes
        val title = stringResource(R.string.normal_mode_color)
        if (presetSchemes.isEmpty()) {
            NoticeDialog(
                title = title,
                message = stringResource(R.string.no_color_to_select),
                onDismiss = onDismiss,
            )
            return
        }
        val currentIndex = presetSchemes.indexOfFirst { it.id == ColorManager.activeColorScheme.id }
        SingleChoiceDialog(
            title = title,
            entries = presetSchemes.map { it.colors["name"].toString() },
            selectedIndex = currentIndex,
            onDismiss = onDismiss,
            onSelect = { which ->
                if (which != currentIndex) {
                    ColorManager.setColorScheme(presetSchemes[which])
                }
                onDismiss()
            },
        )
    }
}
