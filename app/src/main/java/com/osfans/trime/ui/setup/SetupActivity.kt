/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.setup

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.sync.DataStorageMode
import com.osfans.trime.data.sync.RimeDataSync
import com.osfans.trime.ui.main.MainActivity
import com.osfans.trime.ui.theme.TrimeTheme
import com.osfans.trime.util.appContext
import com.osfans.trime.util.createNotificationChannel
import com.osfans.trime.util.startActivity
import com.osfans.trime.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.systemservices.notificationManager

/**
 * The first-run wizard. The screen itself is [SetupScreen]; this activity keeps what
 * only an activity can do — the folder picker, the "finish setting up" reminder
 * notification, and re-checking system state every time the window comes back into
 * focus (the user leaves to enable the IME in system settings and returns).
 */
class SetupActivity : FragmentActivity() {
    /**
     * Bumped whenever the wizard has to re-read system state; every `isDone()` check in
     * [SetupScreen] keys off it. This is what the old fragments' `sync()` did.
     */
    private var revision by mutableIntStateOf(0)

    private val prefs = AppPrefs.defaultInstance().profile

    private val dataPathPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        RimeDataSync.persistTreeUri(this@SetupActivity, uri)
                        RimeDataSync.importToLocal(this@SetupActivity).getOrThrow()
                    }
                    refresh()
                    toast(R.string.setup__data_path_imported)
                }.onFailure {
                    withContext(Dispatchers.IO) {
                        RimeDataSync.clearExternalTree(this@SetupActivity)
                    }
                    refresh()
                    toast(R.string.setup__data_path_import_failed)
                }
            }
        }

    fun launchDataPathPicker() {
        dataPathPicker.launch(null as Uri?)
    }

    private fun refresh() {
        revision++
    }

    /**
     * Leaving external sync throws the granted tree away and marks the user database as
     * un-migrated again — unchanged from the old radio group listener.
     */
    private fun onStorageModeChange(newMode: DataStorageMode) {
        val oldMode = prefs.dataStorageMode.getValue()
        if (oldMode == DataStorageMode.EXTERNAL_SYNC && newMode == DataStorageMode.APP_STORAGE) {
            prefs.userDbMigrated.setValue(false)
            RimeDataSync.clearExternalTree(this)
        }
        prefs.dataStorageMode.setValue(newMode)
        refresh()
    }

    private fun completeSetup() {
        startActivity<MainActivity>()
        finish()
    }

    companion object {
        private var shown = false
        private const val CHANNEL_ID = "setup"
        private const val NOTIFY_ID = 87463

        fun shouldShowUp() = !shown && SetupPage.hasUndonePage()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrimeTheme {
                SetupScreen(
                    revision = revision,
                    onAction = { it.getButtonAction(this) },
                    onStorageModeChange = ::onStorageModeChange,
                    onFinish = ::completeSetup,
                )
            }
        }
        shown = true
        createNotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.setup_channel),
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refresh()
    }

    override fun onPause() {
        if (SetupPage.hasUndonePage()) {
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_trime_status)
                .setContentTitle(getText(R.string.trime_app_name))
                .setContentText(getText(R.string.setup__notify_hint))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, javaClass),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).setAutoCancel(true)
                .build()
                .let { notificationManager.notify(NOTIFY_ID, it) }
        }
        super.onPause()
    }

    override fun onResume() {
        notificationManager.cancel(NOTIFY_ID)
        super.onResume()
    }
}
