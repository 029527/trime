// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.view.View
import com.osfans.trime.R
import com.osfans.trime.data.theme.Theme
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.wrapContent

class CandidateUi(
    override val ctx: Context,
    theme: Theme,
    private val compatView: View,
    /** Optional view shown before the candidates, e.g. the preedit of a floating keyboard. */
    private val leading: View? = null,
) : Ui {
    val unrollButton =
        ToolButton(ctx, R.drawable.ic_baseline_expand_more_24).apply {
            visibility = View.INVISIBLE
        }

    override val root =
        ctx.constraintLayout {
            add(
                unrollButton,
                lParams(dp(40)) {
                    centerVertically()
                    endOfParent()
                },
            )
            val padding = dp(theme.generalStyle.candidatePadding / 2)
            if (leading != null) {
                add(
                    leading,
                    lParams(wrapContent, wrapContent) {
                        centerVertically()
                        startOfParent(padding)
                    },
                )
            }
            add(
                compatView,
                lParams {
                    centerVertically()
                    if (leading != null) startToEndOf(leading, dp(4)) else startOfParent(padding)
                    before(unrollButton)
                },
            )
        }
}
