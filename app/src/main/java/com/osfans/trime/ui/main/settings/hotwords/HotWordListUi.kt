/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.hotwords

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.osfans.trime.R
import com.osfans.trime.data.hotwords.HotWord
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.resources.resolveThemeAttribute
import splitties.resources.styledColor
import splitties.views.backgroundColor
import splitties.views.bottomPadding
import splitties.views.dsl.coordinatorlayout.coordinatorLayout
import splitties.views.dsl.coordinatorlayout.defaultLParams
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.margin
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.view
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.gravityEndBottom
import splitties.views.imageDrawable
import splitties.views.recyclerview.verticalLayoutManager
import splitties.views.setPaddingDp
import splitties.views.textAppearance

class HotWordListUi(
    override val ctx: Context,
    entries: List<HotWord>,
    onClick: (HotWord) -> Unit,
    onMore: (HotWord, android.view.View) -> Unit,
) : Ui {
    val fab =
        view(::FloatingActionButton) {
            imageDrawable =
                drawable(R.drawable.ic_baseline_add_24)!!.apply {
                    setTint(styledColor(android.R.attr.colorForegroundInverse))
                }
        }

    val adapter = HotWordListAdapter(entries, onClick, onMore)

    private val emptyHint = textView {
        setText(R.string.hot_words_empty)
        gravity = Gravity.CENTER
        setPaddingDp(32, 48, 32, 48)
        textAppearance = ctx.resolveThemeAttribute(android.R.attr.textAppearanceListItemSecondary)
    }

    private val list = recyclerView {
        layoutManager = verticalLayoutManager()
        adapter = this@HotWordListUi.adapter
        clipToPadding = false
    }

    fun submit(words: List<HotWord>) {
        adapter.submitList(words.toList())
        emptyHint.isVisible = words.isEmpty()
    }

    fun showSnackBar(text: String) {
        Snackbar.make(root, text, Snackbar.LENGTH_SHORT).setAnchorView(fab).show()
    }

    private fun updateViewMargin(insets: WindowInsetsCompat? = null) {
        val windowInsets = (insets ?: ViewCompat.getRootWindowInsets(root)) ?: return
        val navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
        fab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = navBars.bottom + ctx.dp(16)
        }
        list.bottomPadding = navBars.bottom + ctx.dp(88)
    }

    override val root = coordinatorLayout {
        backgroundColor = styledColor(android.R.attr.colorBackground)
        add(
            list,
            defaultLParams {
                height = matchParent
                width = matchParent
            },
        )
        add(
            emptyHint,
            defaultLParams {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                width = matchParent
            },
        )
        add(
            fab,
            defaultLParams {
                gravity = gravityEndBottom
                margin = dp(16)
            },
        )
        doOnAttach { updateViewMargin() }
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
            updateViewMargin(windowInsets)
            windowInsets
        }
        emptyHint.isVisible = entries.isEmpty()
    }
}
