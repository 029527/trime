/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.hotwords

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.data.hotwords.HotWord
import com.osfans.trime.data.hotwords.HotWordManager
import com.osfans.trime.ui.common.withLoadingDialog
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.util.item
import com.osfans.trime.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.editText
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.verticalLayout
import splitties.views.dsl.core.wrapContent

/**
 * Lets the user keep a list of words that should beat the dictionaries (`okok`
 * instead of `哦可哦可`). Every change is written to the app's private storage and
 * followed by a deploy, which regenerates the Rime tables for both keyboards.
 */
class HotWordFragment : Fragment() {
    private val viewModel: MainViewModel by activityViewModels()

    private var words: MutableList<HotWord> = HotWordManager.getAll().toMutableList()

    private val ui: HotWordListUi by lazy {
        HotWordListUi(
            requireContext(),
            words,
            onClick = { showEditDialog(it) },
            onMore = { word, anchor ->
                PopupMenu(requireContext(), anchor).apply {
                    menu.item(R.string.hot_word_edit) { showEditDialog(word) }
                    menu.item(R.string.hot_word_delete) {
                        words.remove(word)
                        commit()
                    }
                }.show()
            },
        ).apply {
            fab.setOnClickListener { showEditDialog(null) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ui.root

    private fun showEditDialog(existing: HotWord?) {
        val ctx = requireContext()
        val textInput =
            ctx.editText {
                setHint(R.string.hot_word_text_hint)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setText(existing?.text.orEmpty())
            }
        val codeInput =
            ctx.editText {
                setHint(R.string.hot_word_code_hint)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setText(existing?.code.orEmpty())
            }
        val weightInput =
            ctx.editText {
                setHint(R.string.hot_word_weight_hint)
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(existing?.weight?.toString() ?: "1")
            }
        val form =
            ctx.verticalLayout {
                val pad = dp(20)
                setPadding(pad, dp(8), pad, 0)
                add(textInput, lParams(matchParent, wrapContent))
                add(codeInput, lParams(matchParent, wrapContent))
                add(weightInput, lParams(matchParent, wrapContent))
            }
        val dialog =
            AlertDialog
                .Builder(ctx)
                .setTitle(if (existing == null) R.string.hot_word_add else R.string.hot_word_edit)
                .setMessage(R.string.hot_word_dialog_message)
                .setView(form)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = HotWordManager.sanitizeText(textInput.text.toString())
                val code = HotWordManager.sanitizeCode(codeInput.text.toString())
                val weight = weightInput.text.toString().toIntOrNull() ?: 1
                when {
                    text.isEmpty() -> textInput.error = getString(R.string.hot_word_text_empty)
                    code.isEmpty() && HotWordManager.defaultCode(text).isEmpty() ->
                        codeInput.error = getString(R.string.hot_word_code_empty)
                    else -> {
                        val word = HotWord(text, code, weight.coerceIn(0, 1000))
                        val index = existing?.let { words.indexOf(it) } ?: -1
                        if (index >= 0) words[index] = word else words.add(0, word)
                        dialog.dismiss()
                        commit()
                    }
                }
            }
        }
        dialog.show()
        textInput.requestFocus()
    }

    /** Persist the list, then redeploy so the regenerated tables take effect. */
    private fun commit() {
        val ctx = requireContext()
        val snapshot = words.toList()
        ui.submit(snapshot)
        lifecycleScope.launch {
            withLoadingDialog(ctx, R.string.hot_word_deploying) {
                runCatching {
                    withContext(Dispatchers.IO) { HotWordManager.save(snapshot) }
                    viewModel.rime.runOnReady { deploy(skipImport = true) }
                }.onSuccess {
                    ui.showSnackBar(getString(R.string.hot_word_deployed))
                }.onFailure {
                    ctx.toast(getString(R.string.hot_word_deploy_failed, it.message ?: it.javaClass.simpleName))
                }
            }
        }
    }
}
