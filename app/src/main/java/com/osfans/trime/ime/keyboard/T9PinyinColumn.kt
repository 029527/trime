/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import splitties.dimensions.dp

/**
 * The "choose pinyin" column of a nine-key keyboard: a vertical list of syllables drawn
 * over the leftmost key column while a T9 input is being composed, like iOS.
 */
class T9PinyinColumn(
    context: Context,
    private val theme: Theme,
    private val onSyllable: (String) -> Unit,
) : ScrollView(context) {
    private val list =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

    private fun color(
        key: String,
        fallback: Int,
    ): Int = runCatching { ColorManager.getColor(key) }.getOrDefault(fallback)

    init {
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        setBackgroundColor(color("keyboard_back_color", Color.TRANSPARENT))
        addView(list)
    }

    fun update(syllables: List<String>) {
        list.removeAllViews()
        val gapV = dp(theme.generalStyle.verticalGap)
        val gapH = dp(theme.generalStyle.horizontalGap)
        val radius = dp(theme.generalStyle.roundCorner)
        val keyColor = color("key_back_color", Color.WHITE)
        val pressedColor = color("hilited_key_back_color", Color.LTGRAY)
        val textColor = color("key_text_color", Color.BLACK)
        val height = dp(theme.generalStyle.keyHeight).takeIf { it > 0 } ?: dp(44)
        syllables.forEach { syllable ->
            val chip =
                TextView(context).apply {
                    text = syllable
                    gravity = Gravity.CENTER
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.generalStyle.keyLongTextSize.takeIf { it > 0 } ?: 16f)
                    typeface = FontManager.getTypeface("key_font")
                    background =
                        GradientDrawable().apply {
                            setColor(keyColor)
                            cornerRadius = radius
                        }
                    setOnClickListener { onSyllable(syllable) }
                    setOnTouchListener { v, event ->
                        (v.background as? GradientDrawable)?.setColor(
                            if (event.action == android.view.MotionEvent.ACTION_DOWN) pressedColor else keyColor,
                        )
                        false
                    }
                }
            list.addView(
                chip,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height).apply {
                    setMargins(gapH / 2, gapV / 2, gapH / 2, gapV / 2)
                },
            )
        }
    }
}
