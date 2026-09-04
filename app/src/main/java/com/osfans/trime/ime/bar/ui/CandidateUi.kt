// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.view.View
import com.osfans.trime.R
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.keyboard.KeyboardPrefs.candidateViewHeight
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
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
                // small preedit line at the top start; the candidates sit under it at the bottom
                add(
                    leading,
                    lParams(wrapContent, wrapContent) {
                        topOfParent()
                        startOfParent(padding)
                    },
                )
            }
            add(
                compatView,
                if (leading != null) {
                    // one candidate row, pinned to the bottom under the preedit line
                    lParams(wrapContent, dp(ctx.candidateViewHeight(theme))) {
                        bottomOfParent()
                        startOfParent(padding)
                        before(unrollButton)
                    }
                } else {
                    lParams {
                        centerVertically()
                        startOfParent(padding)
                        before(unrollButton)
                    }
                },
            )
        }
}
