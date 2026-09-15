/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.symbol

import android.view.View
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Dp
import com.osfans.trime.ime.compose.keyboard.gesture.PrefsKeyGestureConfig
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import kotlinx.coroutines.launch

/** A key body: the cell inset by half the gaps on each side, with rounded corners. */
internal fun DrawScope.drawKeyBody(
    color: Color,
    cornerRadius: Dp,
    insetX: Dp,
    insetY: Dp,
) {
    val x = insetX.toPx()
    val y = insetY.toPx()
    val radius = cornerRadius.toPx()
    drawRoundRect(
        color,
        topLeft = Offset(x, y),
        size = Size(size.width - 2 * x, size.height - 2 * y),
        cornerRadius = CornerRadius(radius, radius),
    )
}

/**
 * Press feedback of a grid cell: the key body turns [color] while pressed, like a key on the keyboard.
 *
 * An [IndicationNodeFactory], so `clickable` only creates the node on a cell's first touch and a
 * press redraws that one cell without recomposing. Inside the scrolling grid `clickable` delays the
 * press a little, so a fling does not flash every cell it passes.
 */
@Immutable
internal data class SymbolPressIndication(
    val color: Color,
    val cornerRadius: Dp,
    val insetX: Dp,
    val insetY: Dp,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressNode(interactionSource, this)

    private class PressNode(
        private val source: InteractionSource,
        private val spec: SymbolPressIndication,
    ) : Modifier.Node(),
        DrawModifierNode {
        private var presses = 0

        override fun onAttach() {
            coroutineScope.launch {
                source.interactions.collect {
                    when (it) {
                        is PressInteraction.Press -> presses++
                        is PressInteraction.Release, is PressInteraction.Cancel -> presses = (presses - 1).coerceAtLeast(0)
                    }
                    invalidateDraw()
                }
            }
        }

        override fun ContentDrawScope.draw() {
            if (presses > 0) drawKeyBody(spec.color, spec.cornerRadius, spec.insetX, spec.insetY)
            drawContent()
        }
    }
}

/**
 * Touch handling of a fixed bar key, with the keyboard's timing and vibration preferences:
 * a tap fires on release; a [repeatable] key held past the long-press timeout fires at once and
 * then every repeat interval until released.
 */
internal fun Modifier.barKeyGestures(
    repeatable: Boolean,
    view: View,
    onPressedChange: (Boolean) -> Unit,
    onTrigger: () -> Unit,
): Modifier = pointerInput(repeatable, view, onPressedChange, onTrigger) {
    val config = PrefsKeyGestureConfig
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false).consume()
        onPressedChange(true)
        InputFeedbackManager.keyPressVibrate(view)
        try {
            if (!repeatable) {
                if (waitForUpOrCancellation() != null) onTrigger()
                return@awaitEachGesture
            }
            // null: still held when the timeout ran out
            val released = withTimeoutOrNull(config.longPressTimeout.toLong()) { waitForUpOrCancellation() != null }
            when (released) {
                true -> onTrigger()
                false -> {}
                null -> {
                    InputFeedbackManager.keyPressVibrate(view, longPress = true)
                    while (true) {
                        onTrigger()
                        if (config.vibrateOnKeyRepeat) InputFeedbackManager.keyPressVibrate(view)
                        val ended = withTimeoutOrNull(config.repeatInterval.toLong()) {
                            waitForUpOrCancellation()
                            true
                        }
                        if (ended != null) break
                    }
                }
            }
        } finally {
            onPressedChange(false)
            if (config.vibrateOnKeyRelease) InputFeedbackManager.keyPressVibrate(view)
        }
    }
}
