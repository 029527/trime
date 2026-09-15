/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.content.res.ColorStateList
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.ime.keyboard.GestureFrame
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.gravityCenter
import splitties.views.imageResource
import splitties.views.padding

/**
 * A plain icon button for the View panels that still put one into their bar (switches,
 * segments, clipboard). The input bar itself is Compose now (`ime/compose/bar`); delete this
 * once those panels are.
 */
class ToolButton(
    context: Context,
    @DrawableRes icon: Int,
) : GestureFrame(context) {
    private val image = imageView {
        isClickable = false
        isFocusable = false
        adjustViewBounds = true
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        imageTintList = ColorStateList.valueOf(ColorManager.getColor("candidate_text_color"))
        padding = dp(4)
    }

    init {
        add(image, lParams { gravity = gravityCenter })
        setIcon(icon)
    }

    fun setIcon(
        @DrawableRes src: Int,
    ) {
        image.imageResource = src
    }
}
