/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.voice

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import com.osfans.trime.ime.compose.imeComposeView
import com.osfans.trime.ime.compose.theme.ImeColors
import com.osfans.trime.ime.compose.theme.ImeFonts
import com.osfans.trime.ime.compose.theme.ImeTokens
import com.osfans.trime.ime.compose.theme.LocalImeColors
import com.osfans.trime.ime.compose.theme.LocalImeFonts
import com.osfans.trime.ime.compose.theme.LocalImeTokens
import com.osfans.trime.voice.dictation.DictationPillPlacement
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Glyphs of dictation. Filled, unlike the outlined key glyphs: they mark something switched on. */
object DictationGlyphs {
    val Mic: ImageVector get() = Icons.Filled.Mic
    val MicOff: ImageVector get() = Icons.Filled.MicOff
    val Correcting: ImageVector get() = Icons.Filled.AutoFixHigh
}

/**
 * What dictation shows: the pill at the caret and the mic key's active style.
 *
 * Owned by [com.osfans.trime.voice.VoiceInputManager], which outlives the input views, and
 * written on the main thread. Everything is snapshot state and is read while drawing where it
 * can be, so a level change redraws the pill and the key without recomposing them.
 */
@Stable
class DictationIndicator {
    enum class Phase { HIDDEN, LISTENING, FINISHING, CORRECTING, ERROR }

    var phase by mutableStateOf(Phase.HIDDEN)
    var message by mutableStateOf("")

    /** Voice level, 0..1, while listening. */
    var level by mutableFloatStateOf(0f)

    /** The caret on screen, in pixels; [caretValid] is false when the editor did not say. */
    var caretValid by mutableStateOf(false)
        private set
    var caretX by mutableFloatStateOf(Float.NaN)
        private set
    var caretTop by mutableFloatStateOf(Float.NaN)
        private set
    var caretBottom by mutableFloatStateOf(Float.NaN)
        private set

    /** The mic key wears the accent from the first tap until the text is committed, correction included. */
    val keyActive: Boolean get() = phase == Phase.LISTENING || phase == Phase.FINISHING || phase == Phase.CORRECTING

    fun setCaret(
        x: Float,
        top: Float,
        bottom: Float,
    ) {
        caretX = x
        caretTop = top
        caretBottom = bottom
        caretValid = true
    }

    fun clearCaret() {
        caretValid = false
    }
}

/** Where the keyboard starts in the overlay, for the fallback position. Kept up to date by the input view. */
@Stable
class DictationOverlayFrame {
    var keyboardLeft by mutableIntStateOf(0)
        private set
    var keyboardTop by mutableIntStateOf(0)
        private set

    fun update(
        left: Int,
        top: Int,
    ) {
        keyboardLeft = left
        keyboardTop = top
    }
}

/**
 * The dictation pill, laid over the whole input view like the key popups. The IME window covers
 * the screen above the keyboard too (only its touchable region is the keyboard), so the pill can
 * sit at the caret in the app's text field.
 */
class DictationOverlay(
    context: Context,
    indicator: DictationIndicator,
) {
    val frame = DictationOverlayFrame()

    val view: View = DictationOverlayView(context, context.imeComposeView { DictationLayer(indicator, frame) })
}

/** Never takes a touch: the pill is only an indicator, taps go to the app or the keyboard below. */
@SuppressLint("ViewConstructor")
private class DictationOverlayView(
    context: Context,
    content: View,
) : FrameLayout(context) {
    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        // (0, 0) at the top left whatever the locale, as the placement assumes
        layoutDirection = LAYOUT_DIRECTION_LTR
        // in fullscreen (extract) mode the input view is only as tall as the keyboard: draw above it anyway
        clipChildren = false
        clipToPadding = false
        (content as? FrameLayout)?.clipChildren = false
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = false

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean = false
}

/** Content of the pill as last shown, so it fades out with what it said instead of going blank. */
private class LastShown {
    var phase = DictationIndicator.Phase.LISTENING
    var message = ""
}

@Composable
private fun DictationLayer(
    indicator: DictationIndicator,
    frame: DictationOverlayFrame,
) {
    val colors = LocalImeColors.current
    val fonts = LocalImeFonts.current
    val tokens = LocalImeTokens.current
    val phase = indicator.phase
    val visible = phase != DictationIndicator.Phase.HIDDEN
    val last = remember { LastShown() }
    if (visible) {
        last.phase = phase
        last.message = indicator.message
    }
    val appear = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) APPEAR_MS else DISAPPEAR_MS),
        label = "dictation-pill",
    )
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                val p = it.positionOnScreen()
                origin = IntOffset(p.x.roundToInt(), p.y.roundToInt())
            },
    ) {
        if (visible || appear.value > 0f) {
            val shape = RoundedCornerShape(percent = 50)
            DictationPill(
                phase = last.phase,
                message = last.message,
                indicator = indicator,
                colors = colors,
                fonts = fonts,
                tokens = tokens,
                modifier =
                Modifier
                    .layout { measurable, constraints ->
                        val margin = tokens.voicePillScreenMargin.roundToPx()
                        val pill = measurable.measure(Constraints(maxWidth = (constraints.maxWidth - 2 * margin).coerceAtLeast(0)))
                        val caret = indicator.caretValid
                        val position =
                            DictationPillPlacement.place(
                                caretX = if (caret) indicator.caretX - origin.x else Float.NaN,
                                caretTop = if (caret) indicator.caretTop - origin.y else Float.NaN,
                                caretBottom = if (caret) indicator.caretBottom - origin.y else Float.NaN,
                                pillWidth = pill.width,
                                pillHeight = pill.height,
                                areaWidth = constraints.maxWidth,
                                keyboardLeft = frame.keyboardLeft,
                                keyboardTop = frame.keyboardTop,
                                caretGap = tokens.voicePillCaretGap.roundToPx(),
                                margin = margin,
                            )
                        layout(constraints.maxWidth, constraints.maxHeight) { pill.place(position) }
                    }.graphicsLayer {
                        val a = appear.value
                        alpha = a
                        scaleX = START_SCALE + (1 - START_SCALE) * a
                        scaleY = scaleX
                        shadowElevation = tokens.voicePillShadowElevation.toPx()
                        this.shape = shape
                        clip = false
                    }
                    // opaque: the pill floats over the app's content
                    .background(colors.accentBack.copy(alpha = 1f), shape)
                    .heightIn(min = tokens.voicePillHeight)
                    .padding(horizontal = tokens.voicePillHorizontalPadding),
            )
        }
    }
}

@Composable
private fun DictationPill(
    phase: DictationIndicator.Phase,
    message: String,
    indicator: DictationIndicator,
    colors: ImeColors,
    fonts: ImeFonts,
    tokens: ImeTokens,
    modifier: Modifier,
) {
    val content = colors.accentText.copy(alpha = 1f)
    val error = phase == DictationIndicator.Phase.ERROR
    val correcting = phase == DictationIndicator.Phase.CORRECTING
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.voicePillContentGap),
    ) {
        Image(
            painter = rememberVectorPainter(
                when {
                    error -> DictationGlyphs.MicOff
                    correcting -> DictationGlyphs.Correcting
                    else -> DictationGlyphs.Mic
                },
            ),
            contentDescription = null,
            colorFilter = ColorFilter.tint(content),
            modifier = Modifier.size(tokens.voicePillIconSize),
        )
        if (error || correcting) {
            BasicText(
                text = message,
                style = TextStyle(
                    color = content,
                    fontSize = tokens.voicePillTextSize,
                    fontFamily = fonts.family,
                    fontWeight = tokens.panelLabelWeight,
                ),
                maxLines = tokens.voicePillMessageMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!error) {
            LevelBars(indicator, phase = phase, color = content, tokens = tokens)
        }
    }
}

/**
 * Three bars that ripple gently and grow with the voice. Flat and barely moving while the text is
 * being finalised; a pulse running through them while the text is being corrected. The level and
 * the ripple are read while drawing, so only this spacer redraws.
 */
@Composable
private fun LevelBars(
    indicator: DictationIndicator,
    phase: DictationIndicator.Phase,
    color: Color,
    tokens: ImeTokens,
) {
    val listening = phase == DictationIndicator.Phase.LISTENING
    val correcting = phase == DictationIndicator.Phase.CORRECTING
    val ripple = rememberInfiniteTransition(label = "dictation-ripple")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(RIPPLE_MS, easing = LinearEasing)), label = "dictation-ripple-phase")
    val barWidth = tokens.voiceLevelBarWidth
    val gap = tokens.voiceLevelBarGap
    Spacer(
        Modifier
            .size(barWidth * BARS + gap * (BARS - 1), tokens.voiceLevelBarMaxHeight)
            .drawBehind {
                val w = barWidth.toPx()
                val g = gap.toPx()
                val minH = tokens.voiceLevelBarMinHeight.toPx()
                val maxH = size.height
                val level = if (listening) indicator.level else 0f
                val t = ripple.value
                for (i in 0 until BARS) {
                    val wave = 0.5f + 0.5f * sin(2 * PI * (t + i / BARS.toFloat())).toFloat()
                    val amount =
                        when {
                            listening -> (IDLE_RIPPLE * wave + level * (0.55f + 0.45f * wave)).coerceIn(0f, 1f)
                            // a sharpened wave: one bar peaks at a time and the peak runs across, like a thinking indicator
                            correcting -> CORRECTING_RIPPLE_FLOOR + (1 - CORRECTING_RIPPLE_FLOOR) * wave * wave * wave
                            else -> FINISHING_RIPPLE * wave
                        }
                    val h = minH + (maxH - minH) * amount
                    drawRoundRect(color, Offset(i * (w + g), (maxH - h) / 2), Size(w, h), CornerRadius(w / 2))
                }
            },
    )
}

private const val APPEAR_MS = 120
private const val DISAPPEAR_MS = 180
private const val START_SCALE = 0.85f
private const val RIPPLE_MS = 1100
private const val BARS = 3

/** Listening but quiet still ripples clearly, so it never looks like the flat bars of finalising. */
private const val IDLE_RIPPLE = 0.5f
private const val FINISHING_RIPPLE = 0.12f

/** Correcting never drops to the flat bars of finalising: the running peak says work is going on. */
private const val CORRECTING_RIPPLE_FLOOR = 0.15f
