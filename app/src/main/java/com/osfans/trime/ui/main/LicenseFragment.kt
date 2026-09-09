/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import androidx.compose.runtime.Composable
import com.osfans.trime.ui.compose.ComposeFragment

class LicenseFragment : ComposeFragment() {
    @Composable
    override fun Content() {
        LicenseScreen(onNavigateUp = ::navigateUp)
    }
}
