/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import android.content.Context
import android.content.SharedPreferences

/**
 * The one preferences file the whole app reads and writes — **every** user setting
 * lives in it.
 *
 * It used to be reached through `androidx.preference.PreferenceManager`, which is gone
 * now that no screen uses `androidx.preference` any more. The name and mode below are
 * copied from it verbatim (`PreferenceManager.getDefaultSharedPreferencesName()` /
 * `getDefaultSharedPreferencesMode()`), because changing either would point the app at
 * an empty file and lose the user's configuration.
 */
val Context.defaultSharedPreferences: SharedPreferences
    get() = getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)
