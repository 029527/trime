/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ui.compose.preference.PreferenceDelegateComposeFragment

class CandidatesSettingsFragment : PreferenceDelegateComposeFragment(AppPrefs.defaultInstance().candidates)
