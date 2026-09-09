// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.RimeSession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MainViewModel : ViewModel() {
    private val _testInputRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * The test input panel lives in the activity layout, so a Compose screen asks for
     * it through here instead of reaching for the activity.
     */
    val testInputRequests = _testInputRequests.asSharedFlow()

    fun requestTestInput() {
        _testInputRequests.tryEmit(Unit)
    }

    val rime = RimeDaemon.createSession(javaClass.name)

    val restartBackgroundSyncWork = MutableLiveData(false)

    override fun onCleared() {
        RimeDaemon.destroySession(javaClass.name)
    }
}
