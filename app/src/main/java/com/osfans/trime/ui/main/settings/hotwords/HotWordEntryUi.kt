/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.hotwords

import android.content.Context
import android.content.res.ColorStateList
import android.view.ViewGroup
import com.osfans.trime.R
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.resources.resolveThemeAttribute
import splitties.resources.styledColor
import splitties.resources.styledDimenPxSize
import splitties.resources.styledDrawable
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageButton
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable
import splitties.views.textAppearance

class HotWordEntryUi(
    override val ctx: Context,
) : Ui {
    val textView = textView {
        textAppearance = ctx.resolveThemeAttribute(android.R.attr.textAppearanceListItem)
    }

    val summaryView = textView {
        textAppearance = ctx.resolveThemeAttribute(android.R.attr.textAppearanceListItemSecondary)
    }

    val moreButton = imageButton {
        background = styledDrawable(android.R.attr.selectableItemBackground)
        imageDrawable = drawable(R.drawable.ic_baseline_more_horiz_24)
        imageTintList = ColorStateList.valueOf(styledColor(android.R.attr.colorControlNormal))
    }

    override val root = constraintLayout {
        layoutParams = ViewGroup.LayoutParams(matchParent, wrapContent)
        background = styledDrawable(android.R.attr.selectableItemBackground)
        minHeight = styledDimenPxSize(android.R.attr.listPreferredItemHeight)

        val paddingStart = styledDimenPxSize(android.R.attr.listPreferredItemPaddingStart)
        add(
            textView,
            lParams {
                width = matchConstraints
                height = wrapContent
                topOfParent(dp(12))
                startOfParent(paddingStart)
                before(moreButton)
            },
        )
        add(
            summaryView,
            lParams {
                width = matchConstraints
                height = wrapContent
                below(textView, dp(2))
                bottomOfParent(dp(12))
                startOfParent(paddingStart)
                before(moreButton)
            },
        )
        add(
            moreButton,
            lParams {
                width = dp(53)
                height = matchConstraints
                centerVertically()
                endOfParent()
            },
        )
    }
}
