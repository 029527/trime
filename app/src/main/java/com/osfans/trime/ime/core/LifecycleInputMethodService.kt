// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.core

import android.inputmethodservice.InputMethodService
import androidx.annotation.CallSuper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Gives the IME window the view-tree owners an Activity would. The saved-state owner
 * exists only for Compose: a ComposeView refuses to compose without one, and an
 * InputMethodService has no state to save, so the registry is always restored empty.
 */
open class LifecycleInputMethodService :
    InputMethodService(),
    LifecycleOwner,
    SavedStateRegistryOwner {
    private val lifecycleRegistry by lazy { LifecycleRegistry(this) }

    override val lifecycle = lifecycleRegistry

    private val savedStateRegistryController by lazy { SavedStateRegistryController.create(this) }

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    @CallSuper
    override fun onCreate() {
        super.onCreate()
        // must happen while the lifecycle is still INITIALIZED
        savedStateRegistryController.performRestore(null)
        window.window!!.decorView.apply {
            setViewTreeLifecycleOwner(this@LifecycleInputMethodService)
            setViewTreeSavedStateRegistryOwner(this@LifecycleInputMethodService)
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    @CallSuper
    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
