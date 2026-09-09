/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import androidx.compose.runtime.Composable
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.ui.compose.ComposeFragment

class MainFragment : ComposeFragment() {
    override val showNavigateUp = false

    @Composable
    override fun Content() {
        MainScreen(
            onNavigate = ::navigate,
            onDeploy = { mainViewModel.rime.launchOnReady { it.deploy() } },
            onTestInput = { mainViewModel.requestTestInput() },
        )
    }
}
