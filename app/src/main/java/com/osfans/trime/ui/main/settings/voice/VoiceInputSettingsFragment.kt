/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.voice

import android.content.Intent
import androidx.compose.runtime.Composable
import com.osfans.trime.ui.compose.ComposeFragment
import com.osfans.trime.voice.VoicePermissionActivity

class VoiceInputSettingsFragment : ComposeFragment() {
    @Composable
    override fun Content() {
        VoiceInputScreen(
            onNavigateUp = ::navigateUp,
            onRequestPermission = {
                startActivity(Intent(requireContext(), VoicePermissionActivity::class.java))
            },
        )
    }
}
