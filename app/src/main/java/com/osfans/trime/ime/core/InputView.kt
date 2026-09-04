/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import android.annotation.SuppressLint
import android.os.Build
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeSession
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.view.doOnLayout
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.isLandscape
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.InputBarDelegate
import com.osfans.trime.ime.broadcast.EnterKeyDisplayDelegate
import com.osfans.trime.ime.broadcast.InputBroadcaster
import com.osfans.trime.ime.candidates.popup.PopupCandidatesMode
import com.osfans.trime.ime.composition.PreeditDelegate
import com.osfans.trime.ime.dependency.InputDependencyManager
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.popup.PopupDelegate
import com.osfans.trime.ime.symbol.LiquidWindow
import com.osfans.trime.ime.window.BoardWindowManager
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.kodein.di.instance
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.startToStartOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable

/**
 * Successor of the old InputRoot
 */
@SuppressLint("ViewConstructor")
class InputView(
    service: TrimeInputMethodService,
    rime: RimeSession,
    theme: Theme,
) : BaseInputView(service, rime, theme) {
    private val keyboardBackground =
        imageView {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
    private val placeholderListener = OnClickListener { }

    private val leftPaddingSpace =
        view(::View) {
            isFocusable = false
            setOnClickListener(placeholderListener)
        }

    private val rightPaddingSpace =
        view(::View) {
            isFocusable = false
            setOnClickListener(placeholderListener)
        }

    private val bottomPaddingSpace =
        view(::View) {
            isFocusable = false
            setOnClickListener(placeholderListener)
        }

    private val updateWindowViewHeightJob: Job

    private val inputDepMgr = InputDependencyManager.initialize(this, themedContext, theme, service, rime)
    private val di = inputDepMgr.di
    private val broadcaster: InputBroadcaster by di.instance()
    private val popup: PopupDelegate by di.instance()
    private val enterKeyDisplay: EnterKeyDisplayDelegate by di.instance()
    private val preedit: PreeditDelegate by di.instance()
    private val windowManager: BoardWindowManager by di.instance()
    private val inputBar: InputBarDelegate by di.instance()
    private val keyboardWindow: KeyboardWindow by di.instance()
    private val liquidWindow: LiquidWindow by di.instance()

    private val candidatesMode by AppPrefs.defaultInstance().candidates.mode

    private val landscapeFloating by AppPrefs.defaultInstance().keyboard.landscapeFloating
    private val landscapeFloatingMargin by AppPrefs.defaultInstance().keyboard.landscapeFloatingMargin
    private val floatingOffsetX = AppPrefs.defaultInstance().keyboard.landscapeFloatingOffsetX
    private val floatingOffsetY = AppPrefs.defaultInstance().keyboard.landscapeFloatingOffsetY

    /** Floating mode: a small keyboard window at the bottom end of the screen, landscape only. */
    val isFloating: Boolean
        get() = landscapeFloating && resources.configuration.isLandscape()

    private val floatingDrag: FloatingDragHelper? = if (isFloating) FloatingDragHelper() else null

    private val keyboardSidePadding = theme.generalStyle.keyboardPadding
    private val keyboardSidePaddingLandscape = theme.generalStyle.keyboardPaddingLand
    private val keyboardBottomPadding = theme.generalStyle.keyboardPaddingBottom
    private val keyboardBottomPaddingLandscape = theme.generalStyle.keyboardPaddingLandBottom

    private val keyboardSidePaddingPx: Int
        get() {
            if (isFloating) return 0 // the floating window is already small; no side padding
            val value =
                if (context.isLandscapeMode()) keyboardSidePaddingLandscape else keyboardSidePadding
            return dp(value)
        }

    private var lastAppearanceState = Triple(false, false, false)

    private fun broadcastKeyAppearanceUpdate() {
        val composing = rime.run { statusCached.isComposing }
        val hasMenu = rime.run { hasMenu }
        val paging = rime.run { paging }
        val current = Triple(composing, hasMenu, paging)
        if (current != lastAppearanceState) {
            lastAppearanceState = current
            broadcaster.onKeyAppearanceUpdate(current.first, current.second, current.third)
        }
    }

    private val keyboardBottomPaddingPx: Int
        get() {
            val value =
                if (context.isLandscapeMode()) keyboardBottomPaddingLandscape else keyboardBottomPadding
            return dp(value)
        }

    val keyboardView: View

    init {
        // MUST call before any operation
        inputDepMgr.start()

        windowManager.cacheResidentWindow(keyboardWindow, createView = true)
        windowManager.cacheResidentWindow(liquidWindow)
        // show KeyboardWindow by default
        windowManager.attachWindow(KeyboardWindow)

        keyboardBackground.imageDrawable = ColorManager.getDrawable("keyboard_background")

        keyboardView =
            constraintLayout {
                isMotionEventSplittingEnabled = true
                add(
                    keyboardBackground,
                    lParams {
                        centerInParent()
                    },
                )
                add(
                    inputBar.view,
                    lParams(matchParent, dp(inputBar.themedHeight)) {
                        topOfParent()
                        centerHorizontally()
                    },
                )
                add(
                    leftPaddingSpace,
                    lParams {
                        below(inputBar.view)
                        startOfParent()
                        bottomOfParent()
                    },
                )
                add(
                    rightPaddingSpace,
                    lParams {
                        below(inputBar.view)
                        endOfParent()
                        bottomOfParent()
                    },
                )
                add(
                    windowManager.view,
                    lParams {
                        below(inputBar.view)
                        above(bottomPaddingSpace)
                    },
                )
                add(
                    bottomPaddingSpace,
                    lParams {
                        startToEndOf(leftPaddingSpace)
                        endToStartOf(rightPaddingSpace)
                        bottomOfParent()
                    },
                )
            }

        // round the top corners of the whole keyboard area (candidate bar + keyboard),
        // like iOS; the bottom edge is extended so only the top corners are clipped.
        // A floating keyboard is rounded on all four corners.
        val cornerRadius = dp(theme.generalStyle.keyboardCornerRadius)
        if (cornerRadius > 0f) {
            val roundAll = isFloating
            keyboardView.outlineProvider =
                object : ViewOutlineProvider() {
                    override fun getOutline(
                        view: View,
                        outline: Outline,
                    ) {
                        val bottom = if (roundAll) view.height else view.height + cornerRadius.toInt()
                        outline.setRoundRect(0, 0, view.width, bottom, cornerRadius)
                    }
                }
            keyboardView.clipToOutline = true
        }

        updateWindowViewHeightJob =
            service.lifecycleScope.launch {
                keyboardWindow.currentKeyboardHeight.collect {
                    windowManager.view.updateLayoutParams {
                        height = it
                    }
                }
            }

        updateKeyboardSize()

        add(
            preedit.ui.root,
            lParams(wrapContent, wrapContent) {
                above(keyboardView)
                // keep the preedit next to a floating keyboard instead of the screen corner
                if (isFloating) startToStartOf(keyboardView) else startOfParent()
            },
        )

        if (isFloating) {
            val margin = dp(landscapeFloatingMargin)
            add(
                keyboardView,
                lParams(matchConstraints, wrapContent) {
                    endOfParent()
                    bottomOfParent()
                    matchConstraintPercentWidth = AppPrefs.defaultInstance().keyboard.floatingWidthPercent() / 100f
                    marginEnd = margin
                    bottomMargin = margin
                },
            )
            // restore the spot the user dragged the window to last time
            keyboardView.doOnLayout {
                floatingDrag?.moveTo(dp(floatingOffsetX.getValue()).toFloat(), dp(floatingOffsetY.getValue()).toFloat())
            }
        } else {
            add(
                keyboardView,
                lParams(matchParent, wrapContent) {
                    centerHorizontally()
                    bottomOfParent()
                },
            )
        }

        add(
            popup.root,
            lParams(matchParent, matchParent) {
                centerInParent()
            },
        )
    }

    private fun updateKeyboardSize() {
        bottomPaddingSpace.updateLayoutParams {
            height = keyboardBottomPaddingPx
        }
        val sidePadding = keyboardSidePaddingPx
        val unset = LayoutParams.UNSET
        if (sidePadding == 0) {
            // hide side padding space views when unnecessary
            leftPaddingSpace.visibility = View.GONE
            rightPaddingSpace.visibility = View.GONE
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToEnd = unset
                endToStart = unset
                startOfParent()
                endOfParent()
            }
        } else {
            leftPaddingSpace.visibility = View.VISIBLE
            rightPaddingSpace.visibility = View.VISIBLE
            leftPaddingSpace.updateLayoutParams {
                width = sidePadding
            }
            rightPaddingSpace.updateLayoutParams {
                width = sidePadding
            }
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToStart = unset
                endToEnd = unset
                startToEndOf(leftPaddingSpace)
                endToStartOf(rightPaddingSpace)
            }
        }
        preedit.ui.root.setPadding(sidePadding, 0, sidePadding, 0)
        inputBar.view.setPadding(sidePadding, 0, sidePadding, 0)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = floatingDrag?.onIntercept(ev) ?: false || super.onInterceptTouchEvent(ev)

    override fun onTouchEvent(event: MotionEvent): Boolean = floatingDrag?.onTouch(event) ?: false || super.onTouchEvent(event)

    /**
     * Long-pressing an empty part of the candidate bar (nothing being composed) and
     * dragging moves the floating keyboard; the spot is remembered across sessions.
     */
    private inner class FloatingDragHelper {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        private var downX = 0f
        private var downY = 0f
        private var startTx = 0f
        private var startTy = 0f
        private var pending = false
        private var dragging = false
        private val startDrag =
            Runnable {
                pending = false
                dragging = true
                keyboardView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }

        private fun isOnIdleBar(ev: MotionEvent): Boolean {
            if (rime.run { statusCached.isComposing }) return false
            val bar = inputBar.view
            val left = bar.left + keyboardView.left + keyboardView.translationX
            val top = bar.top + keyboardView.top + keyboardView.translationY
            return ev.x >= left && ev.x < left + bar.width && ev.y >= top && ev.y < top + bar.height
        }

        private fun cancelPending() {
            pending = false
            removeCallbacks(startDrag)
        }

        private fun movedBeyondSlop(ev: MotionEvent) = abs(ev.x - downX) > slop || abs(ev.y - downY) > slop

        fun onIntercept(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = false
                    cancelPending()
                    if (isOnIdleBar(ev)) {
                        downX = ev.x
                        downY = ev.y
                        startTx = keyboardView.translationX
                        startTy = keyboardView.translationY
                        pending = true
                        postDelayed(startDrag, longPressTimeout)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragging) return true
                    if (pending && movedBeyondSlop(ev)) cancelPending()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelPending()
            }
            return false
        }

        fun onTouch(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> return pending
                MotionEvent.ACTION_MOVE -> {
                    if (dragging) {
                        moveTo(startTx + ev.x - downX, startTy + ev.y - downY)
                        return true
                    }
                    if (pending && movedBeyondSlop(ev)) cancelPending()
                    return pending
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelPending()
                    if (dragging) {
                        dragging = false
                        persist()
                        return true
                    }
                }
            }
            return false
        }

        /** Translate the window, keeping it inside the IME window. */
        fun moveTo(
            tx: Float,
            ty: Float,
        ) {
            val v = keyboardView
            if (v.width == 0 || width == 0) return
            val minTx = -v.left.toFloat()
            val maxTx = (width - v.right).toFloat()
            val minTy = -v.top.toFloat()
            val maxTy = (height - v.bottom).toFloat()
            v.translationX = tx.coerceIn(minOf(minTx, maxTx), maxOf(minTx, maxTx))
            v.translationY = ty.coerceIn(minOf(minTy, maxTy), maxOf(minTy, maxTy))
        }

        private fun persist() {
            val density = resources.displayMetrics.density
            floatingOffsetX.setValue((keyboardView.translationX / density).roundToInt())
            floatingOffsetY.setValue((keyboardView.translationY / density).roundToInt())
        }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        bottomPaddingSpace.updateLayoutParams<LayoutParams> {
            bottomMargin = getNavBarBottomInset(insets)
        }
        return insets
    }

    fun startInput(
        info: EditorInfo,
        restarting: Boolean = false,
    ) {
        updateEnterKeyLabel(info)
        broadcaster.onStartInput(info)
        if (!restarting) {
            windowManager.attachWindow(KeyboardWindow)
        }
    }

    fun updateEnterKeyLabel(info: EditorInfo) {
        enterKeyDisplay.updateLabelOnEditorInfo(info)
    }

    override fun handleRimeMessage(it: RimeMessage<*>) {
        when (it) {
            is RimeMessage.SchemaMessage -> {
                broadcaster.onRimeSchemaUpdated(it.data)

                windowManager.attachWindow(KeyboardWindow)
            }

            is RimeMessage.OptionMessage -> {
                broadcaster.onRimeOptionUpdated(it.data)

                if (it.data.option == "_liquid_keyboard") {
                    ContextCompat.getMainExecutor(service).execute {
                        windowManager.attachWindow(LiquidWindow)
                        liquidWindow.setDataByIndex(0)
                    }
                }
            }
            is RimeMessage.CompositionMessage -> {
                val data = if (candidatesMode == PopupCandidatesMode.ALWAYS_SHOW) {
                    CompositionProto()
                } else {
                    it.data
                }
                broadcaster.onCompositionUpdate(data)
            }
            is RimeMessage.BulkCandidatesMessage -> {
                broadcaster.onCandidateListUpdate(it.data)
            }
            else -> {}
        }
        broadcastKeyAppearanceUpdate()
    }

    fun updateSelection(
        start: Int,
        end: Int,
    ) {
        broadcaster.onSelectionUpdate(start, end)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun handleInlineSuggestions(response: InlineSuggestionsResponse): Boolean = inputBar.handleInlineSuggestions(response)

    override fun onDetachedFromWindow() {
        ViewCompat.setOnApplyWindowInsetsListener(this, null)
        // cancel the notification job and clear all broadcast receivers,
        // implies that InputView should not be attached again after detached.
        updateWindowViewHeightJob.cancel()
        popup.root.removeAllViews()
        inputDepMgr.stop()
        super.onDetachedFromWindow()
    }
}
