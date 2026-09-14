/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.osfans.trime.R
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.data.theme.ThemePrefs
import com.osfans.trime.ui.compose.preference.PreferenceDelegateComposeFragment
import com.osfans.trime.ui.compose.preference.PreferenceRow
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

    // 三个配色微调滑块边拖边写（节流），键盘原地重新上色
    override val livePreviewKeys = setOf(
        ThemePrefs.THEME_TINT_WARM,
        ThemePrefs.THEME_TINT_DIM,
        ThemePrefs.THEME_TINT_ALPHA,
    )

    /**
     * 滑块下面放一个试用输入框：设置页和键盘同在一个屏幕上，点它调出键盘，
     * 再拖上面的滑块就能直接看到键盘颜色变化，打到一半也不会被打断。
     * 没用主界面那个「测试输入」面板：它连同键盘要占掉大半屏，三个滑块露不全。
     *
     * 「恢复配色默认值」：把配色微调的三个滑块拨回 26 / 92 / 90。
     * 写回 PreferenceDelegate 就够了 —— 列表靠 provider 的变更通知自己刷新，
     * 键盘靠 ColorManager 监听这三个键重新上色。
     */
    @Composable
    override fun Footer() {
        var sample by rememberSaveable { mutableStateOf("") }
        var focused by remember { mutableStateOf(false) }
        var fieldHeight by remember { mutableIntStateOf(0) }
        val requester = remember { BringIntoViewRequester() }
        val density = LocalDensity.current
        val imeBottom = WindowInsets.ime.getBottom(density)
        LaunchedEffect(focused, imeBottom) {
            if (!focused || imeBottom == 0) return@LaunchedEffect
            // Scaffold 只给列表加了键盘高度的底部留白，视口没缩，默认的 bring-into-view
            // 以为输入框还看得见；这里显式要求「上面三个滑块 + 输入框 + 下面一个键盘高」都进视口
            val above = with(density) { SLIDER_ROWS_ABOVE_DP.dp.toPx() }
            requester.bringIntoView(Rect(0f, -above, 1f, (fieldHeight + imeBottom).toFloat()))
        }
        OutlinedTextField(
            value = sample,
            onValueChange = { sample = it },
            label = { Text(stringResource(R.string.theme_tint_preview)) },
            placeholder = { Text(stringResource(R.string.theme_tint_preview_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .bringIntoViewRequester(requester)
                .onSizeChanged { fieldHeight = it.height }
                .onFocusChanged { focused = it.isFocused },
        )
        PreferenceRow(
            title = stringResource(R.string.theme_tint_reset),
            summary = stringResource(R.string.theme_tint_reset_summary),
            onClick = { ThemeManager.prefs.resetTint() },
        )
    }

    @Composable
    override fun Dialogs() {
        when (picker) {
            Picker.THEME -> ThemePickerDialog.ThemeSelectionDialog { picker = null }
            Picker.COLOR -> ColorPickerDialog.ColorSelectionDialog { picker = null }
            null -> Unit
        }
    }

    private companion object {
        /** Roughly three slider rows (title + track), kept visible above the preview field. */
        const val SLIDER_ROWS_ABOVE_DP = 3 * 95
    }
}
