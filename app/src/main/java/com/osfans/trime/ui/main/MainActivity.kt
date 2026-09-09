/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.osfans.trime.BuildConfig
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.soundeffect.SoundEffectManager
import com.osfans.trime.databinding.ActivityMainBinding
import com.osfans.trime.ui.setup.SetupActivity
import com.osfans.trime.util.isStorageAvailable
import com.osfans.trime.util.parcelable
import com.osfans.trime.util.startActivity
import com.osfans.trime.worker.BackgroundSyncWork
import kotlinx.coroutines.launch

/**
 * Host of the settings app. Every destination of [NavigationRoute]'s graph is a Compose
 * screen that draws its own Material 3 chrome edge to edge, so the activity owns no
 * toolbar of its own: it only wires up the navigation host, the test input panel and
 * the entry points other parts of Trime use ([EXTRA_SETTINGS_ROUTE]).
 */
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val uiMode by AppPrefs.defaultInstance().advanced.uiMode

    private lateinit var navController: NavController
    private var testInputPanel: TestInputPanel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val uiMode =
            when (uiMode) {
                AppPrefs.Advanced.UiMode.AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                AppPrefs.Advanced.UiMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                AppPrefs.Advanced.UiMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            }
        AppCompatDelegate.setDefaultNightMode(uiMode)
        super.onCreate(savedInstanceState)
        if (SetupActivity.shouldShowUp()) {
            startActivity<SetupActivity>()
            finish()
            return
        }
        enableEdgeToEdge()
        val binding = ActivityMainBinding.inflate(layoutInflater)

        // The Compose screens consume the window insets themselves; the test input
        // panel is an activity-level View, so it is the only thing inset by hand — and
        // it has to dodge the keyboard as well as the navigation bar.
        fun applyInsets(windowInsets: WindowInsetsCompat) {
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            binding.testInputPanel.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = systemBars.left
                rightMargin = systemBars.right
                bottomMargin = maxOf(systemBars.bottom, ime.bottom)
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            applyInsets(windowInsets)
            windowInsets
        }
        ViewCompat.setWindowInsetsAnimationCallback(
            binding.root,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: List<WindowInsetsAnimationCompat?>,
                ): WindowInsetsCompat {
                    applyInsets(insets)
                    return insets
                }
            },
        )
        setContentView(binding.root)
        navController = binding.navHostFragment.getFragment<NavHostFragment>().navController
        navController.graph = NavigationRoute.createGraph(navController)
        onBackPressedDispatcher.addCallback {
            if (binding.testInputPanel.isVisible) {
                binding.testInputPanel.dismiss()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
        testInputPanel = binding.testInputPanel

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.testInputRequests.collect { testInputPanel?.show(window) }
            }
        }

        processIntent(intent)
        checkNotificationPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            Intent.ACTION_MAIN -> if (SetupActivity.shouldShowUp()) {
                startActivity<SetupActivity>()
            }
            Intent.ACTION_RUN -> {
                val route = intent.parcelable<NavigationRoute>(EXTRA_SETTINGS_ROUTE) ?: return
                navController.popBackStack(NavigationRoute.Main, false)
                navController.navigate(route)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (viewModel.restartBackgroundSyncWork.value == true) {
            viewModel.restartBackgroundSyncWork.value = false
            BackgroundSyncWork.forceStart(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isStorageAvailable()) {
            SoundEffectManager.init()
        }
    }

    override fun onStop() {
        super.onStop()
        testInputPanel?.dismiss()
    }

    override fun onDestroy() {
        testInputPanel = null
        super.onDestroy()
    }

    private fun checkNotificationPermission() {
        if (XXPermissions.isGranted(this, Permission.POST_NOTIFICATIONS)) {
            return
        } else {
            AlertDialog
                .Builder(this)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.notification_permission_title)
                .setMessage(R.string.notification_permission_message)
                .setPositiveButton(R.string.grant_permission) { _, _ ->
                    XXPermissions
                        .with(this)
                        .permission(Permission.POST_NOTIFICATIONS)
                        .request(null)
                }.setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    companion object {
        const val EXTRA_SETTINGS_ROUTE = "${BuildConfig.APPLICATION_ID}.EXTRA_SETTINGS_ROUTE"
    }
}
