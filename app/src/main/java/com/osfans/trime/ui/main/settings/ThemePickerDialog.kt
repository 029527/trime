// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.main.settings

import android.app.AlertDialog
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.data.sync.RimeDataSync
import com.osfans.trime.data.theme.ThemeItem
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ui.compose.preference.NoticeDialog
import com.osfans.trime.ui.compose.preference.SingleChoiceDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Picking a theme is not a plain preference write: the file may have to be copied out
 * of the synced directory first, and [ThemeManager] then reloads it.
 *
 * There are two front ends for the very same [selectTheme] work. [build] returns a
 * platform [AlertDialog] because the keyboard shows it over the input method window,
 * where there is no composition to host a Compose dialog; the settings app uses
 * [ThemeSelectionDialog] instead.
 */
object ThemePickerDialog {
    private suspend fun loadThemes() = withContext(Dispatchers.IO) { ThemeManager.getAllThemes() }

    private suspend fun selectTheme(
        context: Context,
        configId: String,
    ) {
        withContext(Dispatchers.IO) {
            if (RimeDataSync.usesExternalSync()) {
                RimeDataSync
                    .importThemeToLocal(context, configId)
                    .onFailure { Timber.w(it, "Theme import failed for $configId") }
            }
        }
        ThemeManager.selectTheme(configId)
    }

    suspend fun build(
        scope: LifecycleCoroutineScope,
        context: Context,
        afterConfirm: (suspend () -> Unit)? = null,
    ): AlertDialog {
        val allThemes = loadThemes()
        val selectedTheme by ThemeManager.prefs.selectedTheme
        val selectedIndex = allThemes.indexOfFirst { it.configId == selectedTheme }
        return AlertDialog
            .Builder(context)
            .apply {
                setTitle(R.string.selected_theme)
                if (allThemes.isEmpty()) {
                    setMessage(R.string.no_theme_to_select)
                } else {
                    setSingleChoiceItems(
                        allThemes.map { it.name }.toTypedArray(),
                        selectedIndex,
                    ) { dialog, which ->
                        scope.launch {
                            afterConfirm?.invoke()
                            selectTheme(context, allThemes[which].configId)
                            dialog.dismiss()
                        }
                    }
                }
                setNegativeButton(android.R.string.cancel, null)
            }.create()
    }

    /**
     * The Material 3 face of [build], for the theme settings page. Nothing is drawn
     * until the theme files have been enumerated, which is what the suspending [build]
     * does before it hands its dialog back.
     */
    @Composable
    fun ThemeSelectionDialog(onDismiss: () -> Unit) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var themes by remember { mutableStateOf<List<ThemeItem>?>(null) }
        LaunchedEffect(Unit) { themes = loadThemes() }

        val all = themes ?: return
        val title = stringResource(R.string.selected_theme)
        if (all.isEmpty()) {
            NoticeDialog(
                title = title,
                message = stringResource(R.string.no_theme_to_select),
                onDismiss = onDismiss,
            )
            return
        }
        val selectedTheme by ThemeManager.prefs.selectedTheme
        SingleChoiceDialog(
            title = title,
            entries = all.map { it.name },
            selectedIndex = all.indexOfFirst { it.configId == selectedTheme },
            onDismiss = onDismiss,
            onSelect = { which ->
                scope.launch {
                    selectTheme(context, all[which].configId)
                    onDismiss()
                }
            },
        )
    }
}
