/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.content.ClipData
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.TrimeApplication
import com.osfans.trime.ui.compose.preference.NoticeDialog
import com.osfans.trime.ui.main.log.LogLine
import com.osfans.trime.ui.main.log.LogScreen
import com.osfans.trime.ui.theme.TrimeTheme
import com.osfans.trime.util.DeviceInfo
import com.osfans.trime.util.Logcat
import com.osfans.trime.util.iso8601UTCDateTime
import com.osfans.trime.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.systemservices.clipboardManager

/**
 * The log viewer, in the three flavours the launching intent picks:
 *
 * - **crash** — the stack trace of the process that died plus its logcat. There is
 *   nothing to clear there, and it is meant to be exported rather than copied.
 * - **deploy failure** — only the failure trace; no logcat to follow.
 * - **real time** — this process's logcat as it happens.
 *
 * This file is adapted from fcitx5-android project.
 * Source: [fcitx5-android/LogActivity](https://github.com/fcitx5-android/fcitx5-android/blob/24457e13b7c3f9f59a6f220db7caad3d02f27651/app/src/main/java/org/fcitx/fcitx5/android/ui/main/LogActivity.kt)
 */
class LogActivity : AppCompatActivity() {
    private lateinit var launcher: ActivityResultLauncher<String>

    private val lines = mutableStateListOf<LogLine>()
    private var logcat: Logcat? = null
    private var showCrashNotice by mutableStateOf(false)

    private val currentLog: String
        get() = lines.joinToString("\n") { it.text }

    companion object {
        const val FROM_CRASH = "from_crash"
        const val FROM_DEPLOY = "from_deploy"
        const val CRASH_STACK_TRACE = "crash_stack_trace"
        const val DEPLOY_FAILURE_TRACE = "deploy_failure_trace"
    }

    private fun registerLauncher() {
        launcher =
            registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
                if (uri == null) return@registerForActivityResult
                lifecycleScope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            contentResolver.openOutputStream(uri)!!.use { os ->
                                os.bufferedWriter().use {
                                    it.write(DeviceInfo.get(this@LogActivity))
                                    it.write(currentLog)
                                }
                            }
                        }
                    }.let { toast(it) }
                }
            }
    }

    private fun setLogcat(logcat: Logcat) {
        this.logcat = logcat
        logcat.initLogFlow()
        logcat.logFlow
            .onEach { lines.add(LogLine.ofLogcat(it)) }
            .launchIn(lifecycleScope)
    }

    private fun copyLog() {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("log", currentLog))
        if (clipboardManager.hasPrimaryClip()) {
            toast(R.string.copy_done)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val fromCrash = intent.hasExtra(FROM_CRASH)
        val fromDeploy = intent.hasExtra(FROM_DEPLOY)
        val titleRes = when {
            fromCrash -> R.string.crash_logs
            fromDeploy -> R.string.deploy_failure
            else -> R.string.real_time_logs
        }
        when {
            fromCrash -> {
                showCrashNotice = true
                lines.add(LogLine("--------- Crash stacktrace"))
                lines.add(LogLine(intent.getStringExtra(CRASH_STACK_TRACE) ?: "<empty>"))
                setLogcat(Logcat(TrimeApplication.getLastPid()))
            }
            fromDeploy -> lines.add(LogLine(intent.getStringExtra(DEPLOY_FAILURE_TRACE) ?: "<empty>"))
            else -> setLogcat(Logcat())
        }

        setContent {
            TrimeTheme {
                LogScreen(
                    title = stringResource(titleRes),
                    lines = lines,
                    onNavigateUp = ::finish,
                    onClear = if (fromCrash || fromDeploy) null else ({ lines.clear() }),
                    onCopy = if (fromCrash) null else ::copyLog,
                    onExport = { launcher.launch("$packageName-${iso8601UTCDateTime()}.txt") },
                )
                if (showCrashNotice) {
                    NoticeDialog(
                        title = stringResource(R.string.app_crash),
                        message = stringResource(R.string.app_crash_message),
                        onDismiss = { showCrashNotice = false },
                    )
                }
            }
        }
        registerLauncher()
    }

    override fun onDestroy() {
        logcat?.shutdownLogFlow()
        logcat = null
        super.onDestroy()
    }
}
