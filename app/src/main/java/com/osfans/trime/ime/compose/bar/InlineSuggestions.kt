/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.bar

import android.os.Build
import android.view.SurfaceControl
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.inline.InlineContentView
import androidx.annotation.RequiresApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.osfans.trime.ime.compose.theme.LocalImeTokens

/** Autofill chips inflated by the system, kept as the [InlineContentView]s it hands out. */
@Immutable
internal data class InlineSuggestionViews(
    val pinned: View? = null,
    val scrollable: List<View> = emptyList(),
)

/**
 * Autofill suggestions: the scrollable chips centred in a horizontal strip, the pinned one
 * (usually the autofill service's own icon) fixed at the end.
 *
 * An [InlineContentView] draws into a surface of the autofill service, and a surface is not
 * clipped by the views around it. Each chip's surface is therefore reparented under a
 * [SurfaceView] covering exactly the strip, so scrolled-out chips stay inside the strip
 * instead of drawing over the buttons beside it.
 */
@RequiresApi(Build.VERSION_CODES.R)
@Composable
internal fun InlineSuggestions(
    views: InlineSuggestionViews,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalImeTokens.current
    Row(modifier = modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val clip = remember { SurfaceView(context).apply { setZOrderOnTop(true) } }
            AndroidView(factory = { clip }, modifier = Modifier.matchParentSize())
            DisposableEffect(views.scrollable) {
                val contentViews = views.scrollable.filterIsInstance<InlineContentView>()
                contentViews.forEach {
                    it.setSurfaceControlCallback(
                        object : InlineContentView.SurfaceControlCallback {
                            override fun onCreated(surfaceControl: SurfaceControl) {
                                SurfaceControl.Transaction().reparent(surfaceControl, clip.surfaceControl).apply()
                            }

                            override fun onDestroyed(surfaceControl: SurfaceControl) {}
                        },
                    )
                }
                onDispose {
                    contentViews.forEach { v ->
                        v.surfaceControl?.let { SurfaceControl.Transaction().reparent(it, null).apply() }
                    }
                }
            }
            // the strip width comes from placement, not BoxWithConstraints: the IME's ConstraintLayout
            // measures twice with different constraints, which would recompose this every frame
            var stripWidth by remember { mutableIntStateOf(0) }
            val density = LocalDensity.current
            Box(modifier = Modifier.fillMaxSize().onSizeChanged { stripWidth = it.width }) {
                Row(
                    modifier =
                    Modifier
                        .fillMaxHeight()
                        .horizontalScroll(rememberScrollState())
                        .widthIn(min = with(density) { stripWidth.toDp() }),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    views.scrollable.forEach { view ->
                        key(view) { EmbeddedView(view, Modifier.fillMaxHeight()) }
                    }
                }
            }
        }
        views.pinned?.let { pinned ->
            key(pinned) {
                EmbeddedView(pinned, Modifier.fillMaxHeight().padding(horizontal = tokens.barInlinePinnedHorizontalMargin))
            }
        }
    }
}

/**
 * A view owned by someone else, shown through a container of our own so it can move between
 * compositions: whatever parent it had is left first, and it is taken out again on release.
 */
@Composable
internal fun EmbeddedView(
    view: View,
    modifier: Modifier = Modifier,
    /** Stretch the view across the container instead of letting it keep its own width. */
    matchWidth: Boolean = false,
) {
    AndroidView(
        factory = { context ->
            FrameLayout(context).apply {
                (view.parent as? ViewGroup)?.removeView(view)
                val width = if (matchWidth) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
                addView(view, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        },
        modifier = modifier,
        onRelease = { it.removeAllViews() },
    )
}
