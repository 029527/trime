/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.ExtractEditText
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import splitties.bitflags.hasFlag
import splitties.dimensions.dp

/**
 * 横屏全屏（抽取）模式下键盘上方的那个输入框。
 *
 * framework 在 [android.inputmethodservice.InputMethodService.setExtractView] 里按
 * `android.R.id.inputExtractEditText` 找 [ExtractEditText] 并接管它，所以那个 id 必须留着；
 * 右边的动作键是自己画自己接的，没有走 framework 的 `inputExtractAccessories`
 * （那两个 id 是 internal 的，拿不到）。
 *
 * 配色全部走 [ColorManager]，跟键盘一套；主题或配色一变，外面调 [applyColors] 重刷。
 */
@SuppressLint("ViewConstructor")
class ExtractInputUi(
    context: Context,
) : LinearLayout(context) {
    val editText =
        ExtractEditText(context).apply {
            id = android.R.id.inputExtractEditText
            gravity = Gravity.TOP or Gravity.START
            isVerticalScrollBarEnabled = true
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

    private val actionButton =
        TextView(context).apply {
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, ACTION_TEXT_SIZE_SP)
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
        addView(editText, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(
            actionButton,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            },
        )
    }

    fun setOnActionClickListener(listener: () -> Unit) {
        actionButton.setOnClickListener { listener() }
    }

    /** 从 [ColorManager] 重新取一遍颜色，配色/主题变了要调。 */
    fun applyColors(theme: Theme) {
        val corner = dp(theme.generalStyle.roundCorner)
        val textColor = ColorManager.getColor("text_color")
        // 主题的 border_color / key_border_color 经常直接落回 back_color，那样输入框和
        // 动作键就跟底色糊成一片；描边固定用正文色压到三成，任何配色下都还看得出边界
        val strokeColor = ColorUtils.setAlphaComponent(textColor, BORDER_ALPHA)
        background = ColorDrawable(ColorManager.getColor("back_color"))
        editText.apply {
            background = boxDrawable("text_back_color", strokeColor, corner)
            setTextColor(textColor)
            setHintTextColor(ColorManager.getColor("comment_text_color"))
            // 选中区用主题的高亮底色；光标跟正文同色，这样不管主题的高亮色多浅都看得见
            highlightColor = ColorManager.getColor("hilited_back_color")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                textCursorDrawable =
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setSize(dp(2), 0)
                        setColor(textColor)
                    }
            }
        }
        actionButton.apply {
            background = boxDrawable("key_back_color", strokeColor, corner)
            setTextColor(ColorManager.getColor("key_text_color"))
        }
    }

    private fun boxDrawable(
        colorKey: String,
        strokeColor: Int,
        corner: Float,
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = corner
        setColor(ColorManager.getColor(colorKey))
        setStroke(dp(1), strokeColor)
    }

    /**
     * 按当前编辑器刷新右边的动作键：有 [EditorInfo.actionLabel] 就用它，
     * 没有就用主题 `enter_labels` 里对应的文案；编辑器压根不要动作键时整个藏掉。
     */
    fun updateAction(
        info: EditorInfo?,
        theme: Theme,
    ) {
        if (info == null || !hasAction(info)) {
            actionButton.visibility = View.GONE
            return
        }
        actionButton.visibility = View.VISIBLE
        val label = info.actionLabel?.takeIf { it.isNotEmpty() }
        actionButton.text = label ?: themeLabelOf(info, theme)
    }

    private fun hasAction(info: EditorInfo): Boolean {
        if (info.actionLabel?.isNotEmpty() == true) return true
        if (info.inputType == InputType.TYPE_NULL) return false
        if (info.imeOptions.hasFlag(EditorInfo.IME_FLAG_NO_ACCESSORY_ACTION)) return false
        return info.imeOptions and EditorInfo.IME_MASK_ACTION != EditorInfo.IME_ACTION_NONE
    }

    private fun themeLabelOf(
        info: EditorInfo,
        theme: Theme,
    ): String {
        val labels = theme.generalStyle.enterLabel
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_DONE -> labels.done
            EditorInfo.IME_ACTION_GO -> labels.go
            EditorInfo.IME_ACTION_NEXT -> labels.next
            EditorInfo.IME_ACTION_PREVIOUS -> labels.pre
            EditorInfo.IME_ACTION_SEARCH -> labels.search
            EditorInfo.IME_ACTION_SEND -> labels.send
            else -> labels.default
        }
    }

    companion object {
        private const val BORDER_ALPHA = 0x4D
        private const val TEXT_SIZE_SP = 18f
        private const val ACTION_TEXT_SIZE_SP = 16f
    }
}
