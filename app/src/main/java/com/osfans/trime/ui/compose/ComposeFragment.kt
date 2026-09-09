/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.ui.main.NavigationRoute
import com.osfans.trime.ui.theme.TrimeTheme
import com.osfans.trime.util.navigateWithAnim

/**
 * Base class for a screen that is written in Compose but still lives as a destination
 * of the existing `MainActivity` navigation graph (see [NavigationRoute]).
 *
 * Keeping the Navigation-for-Fragments graph means the not-yet-migrated pages keep
 * working untouched, and outside entry points ([MainActivity.EXTRA_SETTINGS_ROUTE],
 * which the keyboard itself uses) keep working as well.
 *
 * A subclass only implements [Content]; the whole window chrome (large top app bar,
 * insets, edge-to-edge) belongs to the composable, not to the activity toolbar. Add
 * the route to [NavigationRoute.composeDestinations] so the activity hides its
 * legacy toolbar for it.
 */
abstract class ComposeFragment : Fragment() {
    protected val mainViewModel: MainViewModel by activityViewModels()

    /** The screen itself. Already wrapped in [TrimeTheme]. */
    @Composable
    protected abstract fun Content()

    /** `null` when this destination is the graph's start, so no up arrow is drawn. */
    protected open val showNavigateUp: Boolean = true

    protected fun navigateUp() {
        findNavController().navigateUp()
    }

    protected fun navigate(route: NavigationRoute) {
        findNavController().navigateWithAnim(route)
    }

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            TrimeTheme {
                Content()
            }
        }
    }
}
