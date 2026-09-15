/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.popup

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.SparseArray
import android.view.View
import androidx.compose.ui.unit.Density
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizePx
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.ime.compose.imeComposeView
import com.osfans.trime.ime.compose.popup.BubbleSlot
import com.osfans.trime.ime.compose.popup.PopupGeometry
import com.osfans.trime.ime.compose.popup.PopupKeyboardLayout
import com.osfans.trime.ime.compose.popup.PopupKeyboardState
import com.osfans.trime.ime.compose.popup.PopupLayer
import com.osfans.trime.ime.compose.popup.PopupLayerState
import com.osfans.trime.ime.compose.popup.PopupLayerView
import com.osfans.trime.ime.compose.popup.PopupMetrics
import com.osfans.trime.ime.compose.theme.ImeTokens
import com.osfans.trime.ime.dependency.InputDependencyManager
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode
import com.osfans.trime.ime.keyboard.KeyboardSwitcher
import com.osfans.trime.ime.keyboard.isIconFont
import com.osfans.trime.ime.keyboard.toIconName
import org.kodein.di.instance
import kotlin.math.roundToInt

/**
 * Key preview bubbles and long-press keyboards, driven by [PopupAction]s from the keyboard.
 *
 * Positions are worked out here, in pixels, because [PopupAction.ChangeFocusAction] needs its
 * answer synchronously; [PopupLayer] only draws what [PopupLayerState] says. Action bounds are in
 * window coordinates and are shifted into the layer's.
 */
class PopupDelegate {
    private val context: Context by InputDependencyManager.getInstance().di.instance()

    private val handler = Handler(Looper.getMainLooper())

    private val state by lazy {
        val landscape = context.isLandscapeMode()
        PopupLayerState(
            PopupMetrics.of(
                Density(context),
                if (landscape) ImeTokens.Landscape else ImeTokens.Portrait,
            ),
        )
    }
    private val metrics get() = state.metrics

    /** Bubbles on screen by key; SparseArray, so key indices are not boxed on every press. */
    private val showingBubbles = SparseArray<BubbleSlot>()
    private val freeBubbles = ArrayDeque<BubbleSlot>()

    private val rootLocation = IntArray(2)

    /** Overlay covering the whole input view; see [PopupLayerView] for why it ignores touches. */
    val root: View by lazy {
        PopupLayerView(context, context.imeComposeView { PopupLayer(state) })
    }

    private fun updateRootLocation() = root.getLocationInWindow(rootLocation)

    private fun showPreview(
        viewId: Int,
        content: String,
        bounds: Rect,
    ) {
        val slot = showingBubbles[viewId] ?: obtainBubble().also {
            it.viewId = viewId
            showingBubbles.put(viewId, it)
        }
        handler.removeCallbacks(slot.hideTask)
        updateRootLocation()
        val m = metrics
        val centerX = (bounds.left + bounds.right) / 2 - rootLocation[0]
        slot.x = PopupGeometry.centeredLeft(centerX, m.previewWidth, root.width)
        slot.y = PopupGeometry.topAbove(bounds.top - rootLocation[1] + m.anchorOffset, m.previewHeight)
        slot.text = content
        slot.shownAt = SystemClock.uptimeMillis()
        slot.visible = true
    }

    private fun obtainBubble(): BubbleSlot = freeBubbles.removeFirstOrNull() ?: BubbleSlot().also { slot ->
        slot.hideTask = Runnable { hideBubble(slot) }
        state.bubbles.add(slot)
    }

    private fun updatePreview(
        viewId: Int,
        content: String,
    ) {
        showingBubbles[viewId]?.text = content
    }

    private fun hideBubble(slot: BubbleSlot) {
        handler.removeCallbacks(slot.hideTask)
        if (slot.viewId == -1) return
        if (showingBubbles[slot.viewId] === slot) showingBubbles.remove(slot.viewId)
        slot.visible = false
        slot.viewId = -1
        freeBubbles.addLast(slot)
    }

    /** A bubble stays up at least [HIDE_THRESHOLD] ms, so a quick tap still shows a stable bubble. */
    private fun dismissPreview(viewId: Int) {
        val slot = showingBubbles[viewId] ?: return
        val timeLeft = slot.shownAt + HIDE_THRESHOLD - SystemClock.uptimeMillis()
        if (timeLeft <= 0L) {
            hideBubble(slot)
        } else {
            handler.removeCallbacks(slot.hideTask)
            handler.postDelayed(slot.hideTask, timeLeft)
        }
    }

    private fun showKeyboard(
        viewId: Int,
        keys: List<String>,
        bounds: Rect,
    ) {
        // the keyboard takes the bubble's place
        showingBubbles[viewId]?.let { hideBubble(it) }
        if (keys.isEmpty()) return
        updateRootLocation()
        val m = metrics
        val left = bounds.left - rootLocation[0]
        val top = bounds.top - rootLocation[1]
        val layout = PopupKeyboardLayout(
            keyCount = keys.size,
            containerWidth = root.width,
            triggerLeft = left,
            triggerRight = bounds.right - rootLocation[0],
            bottom = top + m.anchorOffset,
            cellWidth = m.cellWidth,
            cellHeight = m.cellHeight,
            padding = m.keyboardPadding,
        )
        val labels = keys.map(::labelOf)
        val iconSize = m.cellTextSize.roundToInt()
        val icons = Array(labels.size) { i ->
            labels[i].takeIf { it.isIconFont }?.let { IconicsDrawable(context, it.toIconName()).apply { sizePx = iconSize } }
        }
        state.keyboard = PopupKeyboardState(viewId, keys, labels, icons, layout, left, top)
    }

    private fun labelOf(key: String): String = if (key.length == 1 && key[0].code < 128) {
        key
    } else {
        KeyActionManager.getAction(key).getLabel(KeyboardSwitcher.currentKeyboard).let {
            when {
                it.isIconFont -> it
                it.isNotEmpty() -> String(Character.toChars(it.codePointAt(0)))
                else -> ""
            }
        }
    }

    private fun keyboardOf(viewId: Int) = state.keyboard?.takeIf { it.viewId == viewId }

    /** @return whether the keyboard closed because the finger slid away from it. */
    private fun changeFocus(
        viewId: Int,
        x: Float,
        y: Float,
    ): Boolean {
        val keyboard = keyboardOf(viewId) ?: return false
        when (val index = keyboard.layout.focusAt(keyboard.triggerLeft + x, keyboard.triggerTop + y)) {
            PopupKeyboardLayout.OUTSIDE -> {
                state.keyboard = null
                return true
            }
            PopupKeyboardLayout.EMPTY -> {}
            else -> keyboard.focus = index
        }
        return false
    }

    private fun triggerFocused(viewId: Int): String? = keyboardOf(viewId)?.let { it.keys.getOrNull(it.focus) }

    private fun dismiss(viewId: Int) {
        if (keyboardOf(viewId) != null) state.keyboard = null
        dismissPreview(viewId)
    }

    fun dismissAll() {
        state.keyboard = null
        while (showingBubbles.size() > 0) hideBubble(showingBubbles.valueAt(0))
    }

    val listener = PopupActionListener { action ->
        with(action) {
            when (this) {
                is PopupAction.ChangeFocusAction -> outResult = changeFocus(viewId, x, y)
                is PopupAction.DismissAction -> dismiss(viewId)
                is PopupAction.PreviewAction -> showPreview(viewId, content, bounds)
                is PopupAction.PreviewUpdateAction -> updatePreview(viewId, content)
                is PopupAction.ShowKeyboardAction -> showKeyboard(viewId, keys, bounds)
                is PopupAction.TriggerAction -> outAction = triggerFocused(viewId)
            }
        }
    }

    companion object {
        private const val HIDE_THRESHOLD = 100L
    }
}
