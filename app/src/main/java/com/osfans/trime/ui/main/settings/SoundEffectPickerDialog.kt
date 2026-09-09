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
import com.osfans.trime.data.soundeffect.SoundEffectManager
import com.osfans.trime.ui.compose.preference.NoticeDialog
import com.osfans.trime.ui.compose.preference.SingleChoiceDialog
import kotlinx.coroutines.launch

/**
 * The key sound profiles. Picking one asks [SoundEffectManager] to switch profile, so
 * this is not a plain preference write either.
 *
 * [build] is the platform dialog the keyboard shows over its own window;
 * [SoundEffectSelectionDialog] is the Material 3 one the settings page uses.
 */
object SoundEffectPickerDialog {
    fun build(
        scope: LifecycleCoroutineScope,
        context: Context,
    ): AlertDialog {
        val all = SoundEffectManager.getAllSoundEffects().map { it.name }
        val current = SoundEffectManager.activeSoundEffect?.name ?: ""
        val currentIndex = all.indexOfFirst { it == current }
        return AlertDialog
            .Builder(context)
            .apply {
                setTitle(R.string.custom_sound_effect_name)
                if (all.isEmpty()) {
                    setMessage(R.string.no_effect_to_select)
                } else {
                    setSingleChoiceItems(
                        all.toTypedArray(),
                        currentIndex,
                    ) { dialog, which ->
                        scope.launch {
                            if (which != currentIndex) {
                                SoundEffectManager.switchEffect(all[which])
                            }
                            dialog.dismiss()
                        }
                    }
                }
                setNegativeButton(android.R.string.cancel, null)
            }.create()
    }

    /** The Material 3 face of [build], for the virtual keyboard settings page. */
    @Composable
    fun SoundEffectSelectionDialog(onDismiss: () -> Unit) {
        val all = SoundEffectManager.getAllSoundEffects().map { it.name }
        val title = stringResource(R.string.custom_sound_effect_name)
        if (all.isEmpty()) {
            NoticeDialog(
                title = title,
                message = stringResource(R.string.no_effect_to_select),
                onDismiss = onDismiss,
            )
            return
        }
        val currentIndex = all.indexOfFirst { it == (SoundEffectManager.activeSoundEffect?.name ?: "") }
        SingleChoiceDialog(
            title = title,
            entries = all,
            selectedIndex = currentIndex,
            onDismiss = onDismiss,
            onSelect = { which ->
                if (which != currentIndex) {
                    SoundEffectManager.switchEffect(all[which])
                }
                onDismiss()
            },
        )
    }
}
