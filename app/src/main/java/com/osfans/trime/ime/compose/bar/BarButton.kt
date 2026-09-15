/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.bar

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizeDp
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.ime.compose.theme.ImeColors
import com.osfans.trime.ime.compose.theme.ImeIcons
import com.osfans.trime.ime.compose.theme.LocalImeColors
import com.osfans.trime.ime.compose.theme.LocalImeFonts
import com.osfans.trime.ime.compose.theme.LocalImeTokens
import com.osfans.trime.ime.keyboard.KeyAction
import com.osfans.trime.ime.keyboard.KeyboardSwitcher
import com.osfans.trime.ime.keyboard.toIconName

/** What a bar button looks like and does: a theme `tool_bar` entry, or a built-in icon. */
@Immutable
internal sealed interface BarButtonSpec {
    /** A `tool_bar` button from the theme. */
    data class Configured(
        val config: ToolBar.Button,
    ) : BarButtonSpec {
        val action: KeyAction get() = KeyActionManager.getAction(config.action)

        /** The rime option this button shows, when its action toggles one. */
        val option: String? get() = action.toggle.ifEmpty { null }
    }

    /** A built-in glyph tinted with the candidate text colour. */
    data class Icon(
        @param:DrawableRes val res: Int,
    ) : BarButtonSpec
}

/** Width and height of a `tool_bar` button, in dp; negative values are the View bar's match / wrap. */
internal fun ToolBar.Button?.sizeDp(default: Int): Pair<Int, Int> {
    val size = this?.size?.takeIf { it.size == 2 } ?: listOf(default, default)
    return size[0] to size[1]
}

/** Applies a `tool_bar` size: `>= 0` dp, `-1` fill, anything else wraps. */
internal fun Modifier.barButtonSize(size: Pair<Int, Int>): Modifier {
    val (w, h) = size
    return this
        .then(
            when {
                w >= 0 -> Modifier.width(w.dp)
                w == -1 -> Modifier.fillMaxWidth()
                else -> Modifier.wrapContentWidth()
            },
        ).then(
            when {
                h >= 0 -> Modifier.height(h.dp)
                h == -1 -> Modifier.fillMaxHeight()
                else -> Modifier.wrapContentHeight()
            },
        )
}

/**
 * One button of the bar. A [BarButtonSpec.Configured] button follows its theme entry: its
 * foreground style (`ic@` glyph, image file, or text; the action's label when empty), its
 * two `option_styles` for [optionEnabled], its background shape and pressed colours.
 */
@Composable
internal fun BarButton(
    spec: BarButtonSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    optionEnabled: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
) {
    val pressed = remember { mutableStateOf(false) }
    val isPressed by pressed
    val colors = LocalImeColors.current
    val tokens = LocalImeTokens.current
    when (spec) {
        is BarButtonSpec.Icon -> {
            Box(
                modifier =
                modifier
                    .barButtonGestures(pressed, onClick, onLongClick, onSwipeDown)
                    .drawBehind {
                        if (isPressed) {
                            val r = tokens.barIconButtonCornerRadius.toPx()
                            drawRoundRect(colors.highlightedCandidateBack, cornerRadius = CornerRadius(r, r))
                        }
                    }.padding(tokens.barIconButtonPadding),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(spec.res),
                    contentDescription = null,
                    contentScale = ContentScale.Inside,
                    colorFilter = ColorFilter.tint(colors.candidateText),
                )
            }
        }
        is BarButtonSpec.Configured -> ConfiguredButton(spec, isPressed, optionEnabled, colors) { content ->
            Box(
                modifier =
                modifier.barButtonGestures(
                    pressed,
                    onClick,
                    onLongClick,
                    onSwipeDown,
                    repeatable = spec.action.isRepeatable,
                ),
                contentAlignment = Alignment.Center,
            ) { content() }
        }
    }
}

@Composable
private fun ConfiguredButton(
    spec: BarButtonSpec.Configured,
    isPressed: Boolean,
    optionEnabled: Boolean,
    colors: ImeColors,
    container: @Composable (content: @Composable () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val config = spec.config
    val fg = config.foreground
    val bg = config.background
    // keyed on colors: a new ImeColors instance is what a scheme or tint change looks like
    val palette =
        remember(config, colors) {
            ButtonPalette(
                foreground = Color(ColorManager.getColor(fg.normal.ifEmpty { "candidate_text_color" })),
                pressedForeground = Color(ColorManager.getColor(fg.highlight.ifEmpty { "hilited_candidate_text_color" })),
                background = bg.normal.takeIf { it.isNotEmpty() }?.let { Color(ColorManager.getColor(it)) } ?: Color.Transparent,
                pressedBackground = Color(ColorManager.getColor(bg.highlight.ifEmpty { "hilited_candidate_button_color" })),
            )
        }
    val style =
        if (spec.option != null && fg.optionStyles.size == 2) {
            fg.optionStyles[if (optionEnabled) 1 else 0]
        } else {
            fg.style
        }
    val image =
        remember(style, colors) {
            when {
                IMAGE_PATTERN.matches(style) -> ColorManager.getDrawable(style)?.let { ButtonImage(it, tinted = false) }
                style.startsWith(ICON_PREFIX) && ImeIcons.vector(style) == null ->
                    ButtonImage(IconicsDrawable(context, style.toIconName()).apply { sizeDp = fg.fontSize.toInt() }, tinted = true)
                else -> null
            }
        }
    val vector = remember(style) { ImeIcons.vector(style) }
    val foreground = if (isPressed) palette.pressedForeground else palette.foreground
    container {
        Box(
            modifier =
            Modifier
                .matchParentSizeOrFill()
                .drawBehind {
                    val color = if (isPressed) palette.pressedBackground else palette.background
                    if (color.alpha == 0f) return@drawBehind
                    val h = bg.horizontalInset.dp.toPx()
                    val v = bg.verticalInset.dp.toPx()
                    val w = size.width - 2 * h
                    val hh = size.height - 2 * v
                    if (w <= 0 || hh <= 0) return@drawBehind
                    when (bg.type) {
                        ToolBar.Button.Background.Type.RECTANGLE -> {
                            val r = bg.cornerRadius.dp.toPx()
                            drawRoundRect(color, Offset(h, v), Size(w, hh), CornerRadius(r, r))
                        }
                        ToolBar.Button.Background.Type.CIRCLE ->
                            drawOval(color, Offset(h, v), Size(w, hh))
                    }
                }.padding(fg.padding.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (vector != null) {
                Image(
                    painter = rememberVectorPainter(vector),
                    contentDescription = null,
                    modifier = Modifier.size(fg.fontSize.dp),
                    colorFilter = ColorFilter.tint(foreground),
                )
            } else if (image != null) {
                Image(
                    painter = remember(image) { DrawablePainter(image.drawable) },
                    contentDescription = null,
                    contentScale = ContentScale.Inside,
                    colorFilter = if (image.tinted) ColorFilter.tint(foreground) else null,
                )
            } else {
                // an image style whose file is missing falls back to the label, like an empty style
                val label =
                    remember(style, optionEnabled) {
                        if (style.isEmpty() || IMAGE_PATTERN.matches(style)) spec.action.getLabel(KeyboardSwitcher.currentKeyboard) else style
                    }
                val fonts = LocalImeFonts.current
                val tokens = LocalImeTokens.current
                BasicText(
                    text = label,
                    style = TextStyle(fontFamily = fonts.family, fontWeight = tokens.panelLabelWeight, fontSize = fg.fontSize.sp),
                    color = { foreground },
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL_SIZE.sp, maxFontSize = fg.fontSize.coerceAtLeast(MIN_LABEL_SIZE).sp),
                )
            }
        }
    }
}

private fun Modifier.matchParentSizeOrFill(): Modifier = this.then(Modifier.fillMaxWidth().fillMaxHeight())

@Immutable
private data class ButtonPalette(
    val foreground: Color,
    val pressedForeground: Color,
    val background: Color,
    val pressedBackground: Color,
)

private class ButtonImage(
    val drawable: android.graphics.drawable.Drawable,
    val tinted: Boolean,
)

private const val ICON_PREFIX = "ic@"
private const val MIN_LABEL_SIZE = 8f
private val IMAGE_PATTERN = ".*\\.(png|jpg|gif|webp)$".toRegex()
