/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import androidx.compose.runtime.Composable
import com.osfans.trime.R
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.ui.compose.ComposeFragment
import com.osfans.trime.util.toast
import timber.log.Timber

class MainFragment : ComposeFragment() {
    override val showNavigateUp = false

    @Composable
    override fun Content() {
        MainScreen(
            onNavigate = ::navigate,
            onDeploy = {
                val ctx = requireContext()
                mainViewModel.rime.launchOnReady {
                    runCatching {
                        it.deploy()
                    }.onSuccess {
                        ctx.toast(R.string.deploy_finish)
                    }.onFailure { e ->
                        Timber.e(e, "Deploy failed")
                        ctx.toast(getString(R.string.hot_word_deploy_failed, e.message ?: e.javaClass.simpleName))
                    }
                }
            },
            onTestInput = { mainViewModel.requestTestInput() },
        )
    }
}
