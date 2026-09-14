/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.osfans.trime.ime.compose.theme.ImeTheme
import com.osfans.trime.ime.compose.theme.ImeTokens
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode

/**
 * A [ComposeView] for use inside the keyboard window.
 *
 * It finds its lifecycle and saved-state owners by walking up to the IME window's decor
 * view, where [com.osfans.trime.ime.core.LifecycleInputMethodService] installs them. A view
 * shown in a **separate** window (PopupWindow, a floating preedit) has no such parent:
 * set both owners on that window's root yourself, or composition crashes at attach time.
 *
 * Disposed when detached: `InputView` is rebuilt on every theme / pref change and never
 * reattached, so there is nothing to keep alive.
 */
fun Context.imeComposeView(content: @Composable () -> Unit): ComposeView = ComposeView(this).apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    val tokens = if (isLandscapeMode()) ImeTokens.Landscape else ImeTokens.Portrait
    setContent { ImeTheme(tokens) { content() } }
}
