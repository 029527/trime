/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.symbol

import android.content.Context
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.AutoScaleTextView
import com.osfans.trime.ime.keyboard.GestureFrame
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.setPaddingDp

class LiquidItemUi(
    override val ctx: Context,
    private val theme: Theme,
) : Ui {
    val mainText = view(::AutoScaleTextView) {
        isClickable = false
        isFocusable = false
        background = null
        textSize = theme.generalStyle.keyTextSize
        typeface = FontManager.getTypeface("key_font")
        setPaddingDp(8, 4, 8, 4)
    }

    private val content = constraintLayout {
        add(
            mainText,
            lParams(wrapContent, wrapContent) {
                centerInParent()
            },
        )
    }

    override val root = view(::GestureFrame) {
        add(content, lParams(matchParent, matchParent))
    }

    private var colorRevision = -1

    init {
        applyColors()
    }

    /** Cheap when nothing changed, so the adapter can call it on every bind. */
    fun applyColors() {
        val revision = ColorManager.revision
        if (revision == colorRevision) return
        colorRevision = revision
        mainText.setTextColor(ColorManager.getColor("key_text_color"))
        content.background = ColorManager.getDecorDrawable(
            "key_back_color",
            "key_border_color",
            ctx.dp(theme.generalStyle.keyBorder),
            ctx.dp(theme.generalStyle.roundCorner),
        )
    }
}
