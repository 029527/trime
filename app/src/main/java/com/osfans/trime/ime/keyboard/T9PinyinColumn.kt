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
import com.osfans.trime.data.theme.SourceHanSans
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.compose.theme.ImeTokens
import com.osfans.trime.ime.keyboard.KeyboardPrefs.floatingScale
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

    private val keyColor get() = color("key_back_color", Color.WHITE)
    private val pressedColor get() = color("hilited_key_back_color", Color.LTGRAY)
    private val textColor get() = color("key_text_color", Color.BLACK)

    init {
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        setBackgroundColor(color("keyboard_back_color", Color.TRANSPARENT))
        addView(list)
    }

    /** Re-read the colours in place, keeping the syllables and the scroll position. */
    fun refreshColors() {
        setBackgroundColor(color("keyboard_back_color", Color.TRANSPARENT))
        val chipColor = keyColor
        val chipTextColor = textColor
        for (i in 0 until list.childCount) {
            val chip = list.getChildAt(i) as? TextView ?: continue
            chip.setTextColor(chipTextColor)
            (chip.background as? GradientDrawable)?.setColor(chipColor)
        }
    }

    /**
     * [chipWidth] is the width of each syllable chip in px; the column itself covers the
     * whole key cell and centers the chips, so the keys underneath stay hidden.
     */
    fun update(
        syllables: List<String>,
        chipWidth: Int,
    ) {
        list.removeAllViews()
        val gapV = dp(4)
        val radius = dp(theme.generalStyle.roundCorner)
        val chipColor = keyColor
        val chipTextColor = textColor
        // about half a key tall: the column lists several syllables in the space of four keys
        val height = (dp(theme.generalStyle.keyHeight).takeIf { it > 0 } ?: dp(44)).let { maxOf(it / 2, dp(24)) }
        syllables.forEach { syllable ->
            val chip =
                TextView(context).apply {
                    text = syllable
                    gravity = Gravity.CENTER
                    setTextColor(chipTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, (theme.generalStyle.keyLongTextSize.takeIf { it > 0 } ?: 16f) * 0.85f * context.floatingScale())
                    typeface = SourceHanSans.typeface(ImeTokens.Portrait.keyLabelWeight)
                    background =
                        GradientDrawable().apply {
                            setColor(chipColor)
                            cornerRadius = radius
                        }
                    setOnClickListener { onSyllable(syllable) }
                    setOnTouchListener { v, event ->
                        // read at touch time: the tint may have changed since the chip was built
                        (v.background as? GradientDrawable)?.setColor(
                            if (event.action == android.view.MotionEvent.ACTION_DOWN) pressedColor else keyColor,
                        )
                        false
                    }
                }
            list.addView(
                chip,
                LinearLayout.LayoutParams(chipWidth, height).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    // no top margin on the first chip: its top edge lines up with the first key row
                    setMargins(0, if (list.childCount == 0) 0 else gapV, 0, 0)
                },
            )
        }
    }
}
