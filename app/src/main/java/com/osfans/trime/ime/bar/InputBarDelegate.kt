/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.bar

import android.content.Context
import android.os.Build
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.FrameLayout
import android.widget.inline.InlineContentView
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.candidates.compact.CompactCandidateDelegate
import com.osfans.trime.ime.candidates.unrolled.UnrolledCandidateWindow
import com.osfans.trime.ime.compose.bar.AlwaysMode
import com.osfans.trime.ime.compose.bar.InlineSuggestionViews
import com.osfans.trime.ime.compose.bar.InputBar
import com.osfans.trime.ime.compose.bar.InputBarActions
import com.osfans.trime.ime.compose.bar.InputBarConfig
import com.osfans.trime.ime.compose.bar.InputBarState
import com.osfans.trime.ime.compose.bar.TabContent
import com.osfans.trime.ime.compose.imeComposeView
import com.osfans.trime.ime.compose.theme.ImeTokens
import com.osfans.trime.ime.composition.PreeditDelegate
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputDependencyManager
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import com.osfans.trime.ime.keyboard.KeyboardPrefs.candidateViewHeight
import com.osfans.trime.ime.keyboard.KeyboardPrefs.inputBarHeight
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.switches.SwitchOptionWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ui.main.ClipEditActivity
import com.osfans.trime.util.AppUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.kodein.di.instance
import splitties.dimensions.dp
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * Owns the bar above the keyboard: decides what it shows through [QuickBarStateMachine] and
 * [UnrollButtonStateMachine], and carries out what is tapped there. Rendering is the Compose
 * [InputBar]; this class only writes [InputBarState].
 */
class InputBarDelegate : InputBroadcastReceiver {
    private val di = InputDependencyManager.getInstance().di
    private val context: Context by di.instance()
    private val service: TrimeInputMethodService by di.instance()
    private val theme: Theme by di.instance()
    private val windowManager: BoardWindowManager by di.instance()
    private val commonKeyboardActionListener: CommonKeyboardActionListener by di.instance()
    private val candidate: CompactCandidateDelegate by di.instance()
    private val rime: RimeSession by di.instance()
    private val preedit: PreeditDelegate by di.instance()

    val themedHeight = context.inputBarHeight(theme)

    private val prefs = AppPrefs.defaultInstance()

    private val hideQuickBar by prefs.keyboard.hideInputBar

    private val clipboardSuggestion by prefs.clipboard.clipboardSuggestion

    private val clipboardSuggestionTimeout by prefs.clipboard.clipboardSuggestionTimeout

    private val state = InputBarState()

    private val clipboardPreviewLength =
        (if (context.isLandscapeMode()) ImeTokens.Landscape else ImeTokens.Portrait).barClipboardPreviewLength

    private var clipboardTimeoutJob: Job? = null

    private var isClipboardFresh: Boolean = false
    private var isInlineSuggestionPresent: Boolean = false

    @Keep
    private val onClipboardUpdateListener = ClipboardHelper.OnClipboardUpdateListener {
        if (!clipboardSuggestion) return@OnClipboardUpdateListener
        service.lifecycleScope.launch {
            if (it.text.isNullOrEmpty()) {
                isClipboardFresh = false
            } else {
                state.clipboardText = it.text.take(clipboardPreviewLength)
                isClipboardFresh = true
                launchClipboardTimeoutJob()
            }
            evalAlwaysUiState()
        }
    }

    private fun launchClipboardTimeoutJob() {
        clipboardTimeoutJob?.cancel()
        val timeout = clipboardSuggestionTimeout * 1000L
        if (timeout < 0L) return
        clipboardTimeoutJob = service.lifecycleScope.launch {
            delay(timeout)
            isClipboardFresh = false
            clipboardTimeoutJob = null
            evalAlwaysUiState()
        }
    }

    private fun evalAlwaysUiState() {
        state.always =
            when {
                isClipboardFresh -> AlwaysMode.Clipboard
                isInlineSuggestionPresent -> AlwaysMode.InlineSuggestion
                else -> AlwaysMode.Toolbar
            }
    }

    private fun dismissClipboardSuggestion() {
        clipboardTimeoutJob?.cancel()
        clipboardTimeoutJob = null
        isClipboardFresh = false
        evalAlwaysUiState()
    }

    private val actions =
        object : InputBarActions {
            override fun onButton(action: String) {
                if (action.isNotEmpty()) {
                    commonKeyboardActionListener.listener.onAction(KeyActionManager.getAction(action))
                } else {
                    windowManager.attachWindow(SwitchOptionWindow())
                }
            }

            override fun onHideKeyboard() {
                service.requestHideSelf(0)
            }

            override fun onUnroll() {
                when (state.unroll) {
                    UnrollButtonStateMachine.State.ClickToAttachWindow -> {
                        // the grid continues after what the bar shows at its start
                        candidate.scrollToStart()
                        windowManager.attachWindow(UnrolledCandidateWindow())
                    }
                    UnrollButtonStateMachine.State.ClickToDetachWindow -> windowManager.attachWindow(KeyboardWindow)
                    UnrollButtonStateMachine.State.Hidden -> {}
                }
            }

            override fun onCommitClipboard() {
                ClipboardHelper.lastBean?.text?.let { service.commitText(it) }
                dismissClipboardSuggestion()
            }

            override fun onEditClipboard() {
                ClipboardHelper.lastBean?.let {
                    AppUtils.launchClipEdit(context, it.id, ClipEditActivity.FROM_CLIPBOARD)
                }
            }

            override fun onDismissClipboard() = dismissClipboardSuggestion()

            override fun onBack() {
                windowManager.attachWindow(KeyboardWindow)
            }
        }

    /**
     * The candidate row as the View bar laid it out: the candidates after a start inset and,
     * on a floating keyboard, the preedit above them. The unroll button beside it is Compose.
     * Lazy, because [PreeditDelegate.embedded] is only decided once `InputView` starts building.
     */
    private val candidateLayer by lazy {
        FrameLayout(context).apply {
            isVisible = false
            val inset = dp(theme.generalStyle.candidatePadding / 2)
            // a floating keyboard keeps its preedit inside the bar instead of above the window
            val leading = preedit.ui.root.takeIf { preedit.embedded }
            if (leading != null) {
                addView(
                    leading,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
                        marginStart = inset
                    },
                )
            }
            addView(
                candidate.view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context.candidateViewHeight(theme)),
                    if (leading != null) Gravity.BOTTOM else Gravity.CENTER_VERTICAL,
                ).apply { marginStart = inset },
            )
        }
    }

    private val barStateMachine =
        QuickBarStateMachine.new {
            switchUiByState(it)
        }

    val unrollButtonStateMachine =
        UnrollButtonStateMachine.new {
            state.unroll = it
        }

    /** Called by the candidate bar whenever its list turns empty or non-empty. */
    fun onCandidatesEmptyChanged(isEmpty: Boolean) {
        barStateMachine.push(
            QuickBarStateMachine.TransitionEvent.CandidatesUpdated,
            QuickBarStateMachine.BooleanKey.CandidateEmpty to isEmpty,
        )
    }

    private fun switchUiByState(newState: QuickBarStateMachine.State) {
        // straight on the View, not through recomposition: candidates show in the frame they arrive
        candidateLayer.isVisible = newState == QuickBarStateMachine.State.Candidate
        if (newState != QuickBarStateMachine.State.Tab) state.tab = null
        state.bar = newState
    }

    val view: View by lazy {
        val config =
            InputBarConfig(
                toolBar = theme.toolBar,
                buttonSizeDp = theme.generalStyle.run { candidateViewHeight + commentHeight },
                borderPx = context.dp(theme.generalStyle.candidateBorder),
                borderRadiusPx = context.dp(theme.generalStyle.candidateBorderRound),
            )
        val layer = candidateLayer
        context
            .imeComposeView {
                InputBar(state, config, layer, actions)
            }.apply {
                visibility = if (hideQuickBar) View.GONE else View.VISIBLE
                isFocusable = false
                isFocusableInTouchMode = false
                isSoundEffectsEnabled = false
                isHapticFeedbackEnabled = false
                evalAlwaysUiState()
                ClipboardHelper.addOnUpdateListener(onClipboardUpdateListener)
                syncToolbarOptionStates()
            }
    }

    override fun onStartInput(info: EditorInfo) {
        evalAlwaysUiState()
    }

    override fun onWindowAttached(window: BoardWindow) {
        if (window is BoardWindow.BarBoardWindow) {
            state.tab = TabContent(window.title, window.showTitle, window.onCreateBarView())
            barStateMachine.push(QuickBarStateMachine.TransitionEvent.BarBoardWindowAttached)
        }
    }

    override fun onWindowDetached(window: BoardWindow) {
        barStateMachine.push(QuickBarStateMachine.TransitionEvent.WindowDetached)
    }

    private val suggestionSize by lazy {
        Size(ViewGroup.LayoutParams.WRAP_CONTENT, context.dp(themedHeight))
    }

    private val directExecutor by lazy {
        Executor { it.run() }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun handleInlineSuggestions(response: InlineSuggestionsResponse): Boolean {
        val suggestions = response.inlineSuggestions
        if (suggestions.isEmpty()) {
            isInlineSuggestionPresent = false
            state.inline = InlineSuggestionViews()
            evalAlwaysUiState()
            return true
        }
        var pinned: InlineSuggestion? = null
        val scrollable = mutableListOf<InlineSuggestion>()
        var extraPinnedCount = 0
        suggestions.forEach {
            if (it.info.isPinned) {
                if (pinned == null) {
                    pinned = it
                } else {
                    scrollable.add(extraPinnedCount++, it)
                }
            } else {
                scrollable.add(it)
            }
        }
        val pinnedSuggestion = pinned
        service.lifecycleScope.launch {
            val pinnedView = pinnedSuggestion?.let { inflateInlineContentView(it) }
            val views = scrollable.map { s -> service.lifecycleScope.async { inflateInlineContentView(s) } }.awaitAll()
            state.inline = InlineSuggestionViews(pinnedView, views.filterNotNull())
        }
        isInlineSuggestionPresent = true
        evalAlwaysUiState()
        return true
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun inflateInlineContentView(suggestion: InlineSuggestion): InlineContentView? = suspendCancellableCoroutine { c ->
        // callback view might be null
        suggestion.inflate(context, suggestionSize, directExecutor) { v ->
            c.resume(v)
        }
    }

    /** Rime options the theme's toolbar buttons toggle. */
    private fun toggleOptions(): Set<String> = buildSet {
        val toolBar = theme.toolBar
        (listOfNotNull(toolBar.primaryButton) + toolBar.buttons).forEach { button ->
            KeyActionManager.getAction(button.action).toggle.takeIf { it.isNotEmpty() }?.let { add(it) }
        }
    }

    /** Seed the toggle buttons with the current value of their rime options. */
    private fun syncToolbarOptionStates() {
        val options = toggleOptions()
        if (options.isEmpty()) return
        options.forEach { state.options.putIfAbsent(it, false) }
        rime.launchOnReady { api ->
            val values = options.associateWith { api.getRuntimeOption(it) }
            ContextCompat.getMainExecutor(context).execute {
                state.options.putAll(values)
            }
        }
    }

    override fun onRimeOptionUpdated(value: RimeMessage.OptionMessage.Data) {
        if (value.option in state.options) state.options[value.option] = value.value
    }
}
