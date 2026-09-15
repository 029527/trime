/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.osfans.trime.BuildConfig
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ui.compose.TrimeScreen
import com.osfans.trime.ui.compose.preference.PreferenceCategoryHeader
import com.osfans.trime.ui.compose.preference.PreferenceRow
import com.osfans.trime.ui.compose.preference.SliderPreferenceItem
import com.osfans.trime.ui.compose.preference.SwitchPreferenceItem

/**
 * The developer page: actions on the logcat reader, and in debug builds the switches that let
 * dictation run without credentials or a voice.
 */
@Composable
fun DeveloperScreen(
    onNavigateUp: () -> Unit,
    onOpenLogs: () -> Unit,
    onClearLogs: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    TrimeScreen(
        title = stringResource(R.string.developer),
        onNavigateUp = onNavigateUp,
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                PreferenceRow(
                    title = stringResource(R.string.real_time_logs),
                    onClick = onOpenLogs,
                )
                PreferenceRow(
                    title = stringResource(R.string.real_time_logs_clear),
                    onClick = { confirmClear = true },
                )
            }
            if (BuildConfig.DEBUG) {
                item { VoiceDebugSection() }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            text = { Text(stringResource(R.string.real_time_logs_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClearLogs()
                    },
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/**
 * Debug builds only. Simulated recognition replaces Volcengine with the scripted
 * `FakeVoiceRecognitionProvider`; the simulated microphone talks for a set time and then stays
 * silent, which drives the automatic stop on an emulator. `VoiceInputManager` checks
 * `BuildConfig.DEBUG` again, so leftover values do nothing in a release build.
 */
@Composable
private fun VoiceDebugSection() {
    val prefs = AppPrefs.defaultInstance().voice
    var simulateRecognition by remember { mutableStateOf(prefs.debugSimulateRecognition.getValue()) }
    var simulateMicrophone by remember { mutableStateOf(prefs.debugSimulateMicrophone.getValue()) }
    var speechSeconds by remember { mutableIntStateOf(prefs.debugSimulatedSpeechSeconds.getValue()) }
    PreferenceCategoryHeader(stringResource(R.string.voice_debug))
    SwitchPreferenceItem(
        title = stringResource(R.string.voice_debug_simulate_recognition),
        summary = stringResource(R.string.voice_debug_simulate_recognition_summary),
        checked = simulateRecognition,
        onCheckedChange = {
            prefs.debugSimulateRecognition.setValue(it)
            simulateRecognition = it
        },
    )
    SwitchPreferenceItem(
        title = stringResource(R.string.voice_debug_simulate_microphone),
        summary = stringResource(R.string.voice_debug_simulate_microphone_summary),
        checked = simulateMicrophone,
        enabled = simulateRecognition,
        onCheckedChange = {
            prefs.debugSimulateMicrophone.setValue(it)
            simulateMicrophone = it
        },
    )
    SliderPreferenceItem(
        title = stringResource(R.string.voice_debug_simulated_speech),
        value = speechSeconds,
        min = 0,
        max = 10,
        step = 1,
        valueLabel = "$speechSeconds s",
        unit = "s",
        defaultValue = 4,
        enabled = simulateRecognition && simulateMicrophone,
        onValueChangeFinished = {
            prefs.debugSimulatedSpeechSeconds.setValue(it)
            speechSeconds = it
        },
    )
}
