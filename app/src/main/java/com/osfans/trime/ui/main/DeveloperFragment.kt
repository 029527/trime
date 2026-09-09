/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import androidx.compose.runtime.Composable
import com.osfans.trime.ui.compose.ComposeFragment
import com.osfans.trime.util.AppUtils
import com.osfans.trime.util.Logcat

class DeveloperFragment : ComposeFragment() {
    @Composable
    override fun Content() {
        DeveloperScreen(
            onNavigateUp = ::navigateUp,
            onOpenLogs = { AppUtils.launchLogActivity(requireContext()) },
            onClearLogs = { Logcat.default.clearLog() },
        )
    }
}
