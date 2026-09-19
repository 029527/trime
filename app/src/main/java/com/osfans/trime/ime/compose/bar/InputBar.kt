/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.bar

import android.os.Build
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.ime.bar.QuickBarStateMachine
import com.osfans.trime.ime.bar.UnrollButtonStateMachine
import com.osfans.trime.ime.compose.theme.LocalImeColors
import com.osfans.trime.ime.compose.theme.LocalImeTokens
import com.osfans.trime.ime.compose.voice.DictationIndicator
import com.osfans.trime.ime.compose.voice.InlineDictationPill
import androidx.compose.foundation.layout.padding
import kotlin.math.roundToInt

/** What the always-on part of the bar shows between its two end buttons. */
internal enum class AlwaysMode {
    Toolbar,
    Clipboard,
    InlineSuggestion,
}

/**
 * Everything the bar renders, written by `InputBarDelegate` from its state machines and
 * callbacks. Main-thread only.
 */
@Stable
internal class InputBarState {
    var bar by mutableStateOf(QuickBarStateMachine.State.Always)
    var always by mutableStateOf(AlwaysMode.Toolbar)
    var unroll by mutableStateOf(UnrollButtonStateMachine.State.Hidden)
    var clipboardText by mutableStateOf("")
    var tab by mutableStateOf<TabContent?>(null)
    var inline by mutableStateOf(InlineSuggestionViews())

    /** Current value of every rime option a toolbar button toggles. */
    val options = mutableStateMapOf<String, Boolean>()
}

/** Where the bar sends what the user does; the bar itself holds no behaviour. */
internal interface InputBarActions {
    fun onButton(action: String)

    fun onHideKeyboard()

    fun onUnroll()

    fun onCommitClipboard()

    fun onEditClipboard()

    fun onDismissClipboard()

    fun onBack()

    fun onVoiceIndicatorClick() {}
}

/** Fixed facts of one bar, taken from the theme when the keyboard is built. */
internal class InputBarConfig(
    val toolBar: ToolBar,
    /** Default button edge, dp: the theme's candidate row plus its comment line. */
    val buttonSizeDp: Int,
    val borderPx: Int,
    val borderRadiusPx: Float,
)

/**
 * The bar above the keyboard, in one of [QuickBarStateMachine.State]:
 *
 * - `Always`: primary button, the toolbar buttons (or a clipboard / autofill suggestion in
 *   their place), and the hide-keyboard button;
 * - `Candidate`: the candidate bar and the unroll button;
 * - `Tab`: the attached board window's bar.
 *
 * The candidate row is a View ([candidateView], [leading]) held in a container that is never
 * taken out of composition: `CandidateBar` must stay attached to report that its list turned
 * non-empty, which is what brings this state about. `InputBarDelegate` shows and hides that
 * container directly, so candidates appear in the frame they arrive instead of a
 * recomposition later.
 */
@Composable
internal fun InputBar(
    state: InputBarState,
    config: InputBarConfig,
    candidateLayer: View,
    actions: InputBarActions,
    voiceIndicator: DictationIndicator? = null,
) {
    val colors = LocalImeColors.current
    val tokens = LocalImeTokens.current
    // the background may be an image with a border, so it goes through the drawable; re-read per colours
    val background =
        remember(colors) {
            ColorManager.getDecorDrawable("candidate_background", "candidate_border_color", config.borderPx, config.borderRadiusPx)
        }
    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .drawBehind {
                val d = background ?: return@drawBehind
                drawIntoCanvas {
                    d.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
                    d.draw(it.nativeCanvas)
                }
            },
    ) {
        val bar = state.bar
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            AndroidView(
                factory = { candidateLayer },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Box(modifier = Modifier.width(tokens.barUnrollButtonWidth).fillMaxHeight()) {
                val unroll = state.unroll
                if (bar == QuickBarStateMachine.State.Candidate && unroll != UnrollButtonStateMachine.State.Hidden) {
                    BarButton(
                        spec =
                        BarButtonSpec.Icon(
                            if (unroll == UnrollButtonStateMachine.State.ClickToDetachWindow) {
                                R.drawable.ic_baseline_expand_less_24
                            } else {
                                R.drawable.ic_baseline_expand_more_24
                            },
                        ),
                        onClick = actions::onUnroll,
                        onSwipeDown = actions::onHideKeyboard,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        when (bar) {
            QuickBarStateMachine.State.Always -> AlwaysBar(state, config, actions, voiceIndicator)
            QuickBarStateMachine.State.Tab ->
                state.tab?.let { tab ->
                    val first = config.toolBar.buttons.firstOrNull()
                    val back =
                        remember(first) {
                            if (first != null) {
                                // styled like the first toolbar button, showing the back glyph
                                BarButtonSpec.Configured(
                                    first.copy(foreground = first.foreground.copy(style = config.toolBar.backStyle, optionStyles = emptyList())),
                                )
                            } else {
                                BarButtonSpec.Icon(R.drawable.ic_baseline_arrow_back_24)
                            }
                        }
                    TabBar(tab, back, config.buttonSizeDp, actions::onBack)
                }
            QuickBarStateMachine.State.Candidate -> {}
        }
    }
}

@Composable
private fun AlwaysBar(
    state: InputBarState,
    config: InputBarConfig,
    actions: InputBarActions,
    voiceIndicator: DictationIndicator? = null,
) {
    val toolBar = config.toolBar
    val primary = toolBar.primaryButton
    val first = toolBar.buttons.firstOrNull()
    val mode = state.always
    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        val isVoiceActive = voiceIndicator != null && voiceIndicator.phase != DictationIndicator.Phase.HIDDEN
        if (isVoiceActive) {
            InlineDictationPill(
                indicator = voiceIndicator,
                onClick = actions::onVoiceIndicatorClick,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp),
            )
        } else {
            // the primary button borrows the first button's size while a suggestion shows, as the View bar did
            val leftSize = (if (mode == AlwaysMode.Toolbar) primary else first).sizeDp(config.buttonSizeDp)
            ToolbarButton(
                spec = remember(primary) { primary?.let { BarButtonSpec.Configured(it) } ?: BarButtonSpec.Icon(R.drawable.ic_baseline_more_horiz_24) },
                state = state,
                actions = actions,
                modifier = Modifier.barButtonSize(leftSize),
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (mode) {
                AlwaysMode.Toolbar -> ButtonsRow(state, config, actions)
                AlwaysMode.Clipboard ->
                    ClipboardSuggestion(
                        text = state.clipboardText,
                        onCommit = actions::onCommitClipboard,
                        onEdit = actions::onEditClipboard,
                        onDismiss = actions::onDismissClipboard,
                        modifier = Modifier.fillMaxSize(),
                    )
                AlwaysMode.InlineSuggestion ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        InlineSuggestions(state.inline)
                    }
            }
        }
        if (first != null) {
            // the end slot is where the hide-keyboard button always was: a downward swipe hides, whatever it shows
            ToolbarButton(
                spec = remember(first) { BarButtonSpec.Configured(first) },
                state = state,
                actions = actions,
                modifier = Modifier.barButtonSize(first.sizeDp(config.buttonSizeDp)),
                onSwipeDown = actions::onHideKeyboard,
            )
        } else {
            BarButton(
                spec = BarButtonSpec.Icon(R.drawable.ic_baseline_arrow_drop_down_24),
                onClick = actions::onHideKeyboard,
                onSwipeDown = actions::onHideKeyboard,
                modifier = Modifier.barButtonSize(null.sizeDp(config.buttonSizeDp)),
            )
        }
    }
}

/**
 * The theme's buttons after the first, with `button_spacing` between them and the bar's ends:
 * laid from the end towards the start, or in order from the start when the toolbar's
 * `buttonsAlignment` is START. When they do not fit, every button gives up width in proportion
 * to its own, so none is pushed out of the bar (the View bar's flexbox did the same).
 */
@Composable
private fun ButtonsRow(
    state: InputBarState,
    config: InputBarConfig,
    actions: InputBarActions,
) {
    val toolBar = config.toolBar
    val specs = remember(toolBar) { toolBar.buttons.drop(1).map { BarButtonSpec.Configured(it) } }
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            specs.forEach { spec ->
                ToolbarButton(
                    spec = spec,
                    state = state,
                    actions = actions,
                    modifier = Modifier.barButtonSize(spec.config.sizeDp(config.buttonSizeDp)),
                )
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val spacing = toolBar.buttonSpacing.dp.roundToPx()
        val natural = measurables.map { it.maxIntrinsicWidth(height) }
        val total = natural.sum()
        val available = (width - spacing * measurables.size).coerceAtLeast(0)
        val scale = if (total > available && total > 0) available.toFloat() / total else 1f
        val placeables =
            measurables.mapIndexed { i, m ->
                val w = (natural[i] * scale).toInt()
                m.measure(Constraints(minWidth = 0, maxWidth = w, minHeight = 0, maxHeight = height))
            }
        layout(width, height) {
            if (toolBar.buttonsAlignment == ToolBar.ButtonsAlignment.START) {
                var x = 0
                placeables.forEach {
                    x += spacing
                    it.place(x, (height - it.height) / 2)
                    x += it.width
                }
            } else {
                var x = width
                placeables.forEach {
                    x -= spacing + it.width
                    it.place(x, (height - it.height) / 2)
                }
            }
        }
    }
}

/** A theme button wired to its action, long-press action and option state; a plain one opens the switches. */
@Composable
private fun ToolbarButton(
    spec: BarButtonSpec,
    state: InputBarState,
    actions: InputBarActions,
    modifier: Modifier,
    onSwipeDown: (() -> Unit)? = null,
) {
    when (spec) {
        is BarButtonSpec.Configured -> {
            val longPress = spec.config.longPressAction
            BarButton(
                spec = spec,
                optionEnabled = spec.option?.let { state.options[it] } ?: false,
                onClick = { actions.onButton(spec.config.action) },
                onLongClick = if (longPress.isNotEmpty()) ({ actions.onButton(longPress) }) else null,
                onSwipeDown = onSwipeDown,
                modifier = modifier,
            )
        }
        is BarButtonSpec.Icon -> BarButton(spec = spec, onClick = { actions.onButton("") }, modifier = modifier)
    }
}
