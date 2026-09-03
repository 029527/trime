/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.hotwords

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.osfans.trime.R
import com.osfans.trime.data.hotwords.HotWord
import com.osfans.trime.data.hotwords.HotWordManager

class HotWordListAdapter(
    items: List<HotWord>,
    private val onClick: (HotWord) -> Unit,
    private val onMore: (HotWord, android.view.View) -> Unit,
) : BaseQuickAdapter<HotWord, HotWordListAdapter.ViewHolder>(items) {
    class ViewHolder(
        val ui: HotWordEntryUi,
    ) : RecyclerView.ViewHolder(ui.root)

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder = ViewHolder(HotWordEntryUi(context))

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        item: HotWord?,
    ) {
        val word = item ?: return
        val ctx = holder.ui.ctx
        val code = HotWordManager.sanitizeCode(word.effectiveCode)
        holder.ui.textView.text = word.text
        holder.ui.summaryView.text =
            ctx.getString(
                R.string.hot_word_entry_summary,
                code.ifEmpty { "-" },
                HotWordManager.t9Code(code).ifEmpty { "-" },
                word.weight,
            )
        holder.ui.root.setOnClickListener { onClick(word) }
        holder.ui.moreButton.setOnClickListener { onMore(word, it) }
    }
}
