/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.osfans.trime.ime.keyboard.KeyBehavior
import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.boolean
import com.osfans.trime.util.yaml.enum
import com.osfans.trime.util.yaml.float
import com.osfans.trime.util.yaml.int
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.sequence
import com.osfans.trime.util.yaml.string
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class TextKeyboard(
    val name: String = "",
    val author: String = "",
    val width: Float = 0f,
    val height: Float = 0f,
    val keyboardHeight: Int = 0,
    val keyboardHeightLand: Int = 0,
    val autoHeightIndex: Int = -1,
    val horizontalGap: Int = 0,
    val verticalGap: Int = 0,
    val roundCorner: Float = -1f,
    val keyBorder: Int = -1,
    val columns: Int = 30,
    val asciiMode: Boolean = true,
    val resetAsciiMode: Boolean = false,
    val labelTransform: LabelTransform = LabelTransform.NONE,
    val lock: Boolean = false,
    val asciiKeyboard: String = "",
    val landscapeKeyboard: String = "",
    val landscapeSplitPercent: Int = 0,
    val keyTextOffsetX: Float = 0f,
    val keyTextOffsetY: Float = 0f,
    val keySymbolOffsetX: Float = 0f,
    val keySymbolOffsetY: Float = 0f,
    val keyHintOffsetX: Float = 0f,
    val keyHintOffsetY: Float = 0f,
    val keyPressOffsetX: Float = 0f,
    val keyPressOffsetY: Float = 0f,
    val importPreset: String = "",
    val keys: List<TextKey> = emptyList(),
) : Parcelable {
    enum class LabelTransform {
        NONE,
        UPPERCASE,
    }

    /**
     * One key of a [TextKeyboard]. Every action slot (`click`, `longClick`, `swipeUp`, ...)
     * maps to the [KeyBehavior] of the same name; an empty slot is `null`.
     */
    @Parcelize
    data class TextKey(
        val width: Float = 0f,
        val height: Float = 0f,
        val roundCorner: Float = -1f,
        val keyBorder: Int = -1,
        val label: String = "",
        val labelSymbol: String = "",
        val hint: String = "",
        val sendBindings: Boolean = true,
        val keyTextSize: Float = 0f,
        val symbolTextSize: Float = 0f,
        val keyTextOffsetX: Float = 0f,
        val keyTextOffsetY: Float = 0f,
        val keySymbolOffsetX: Float = 0f,
        val keySymbolOffsetY: Float = 0f,
        val keyHintOffsetX: Float = 0f,
        val keyHintOffsetY: Float = 0f,
        val keyPressOffsetX: Float = 0f,
        val keyPressOffsetY: Float = 0f,
        val keyTextColor: String = "",
        val keyBackColor: String = "",
        val keyBorderColor: String = "",
        val keySymbolColor: String = "",
        val hlKeyTextColor: String = "",
        val hlKeyBackColor: String = "",
        val hlKeyBorderColor: String = "",
        val hlKeySymbolColor: String = "",
        val popup: List<String> = emptyList(),
        /** Drop this key (and its row, if every key in the row is dropped) when the device is in landscape. */
        val hideInLandscape: Boolean = false,
        val composing: KeyActionToken? = null,
        val hasMenu: KeyActionToken? = null,
        val paging: KeyActionToken? = null,
        val combo: KeyActionToken? = null,
        val ascii: KeyActionToken? = null,
        val click: KeyActionToken? = null,
        val doubleClick: KeyActionToken? = null,
        val lazyDoubleClick: KeyActionToken? = null,
        val swipeUp: KeyActionToken? = null,
        val longClick: KeyActionToken? = null,
        val swipeDown: KeyActionToken? = null,
        val swipeLeft: KeyActionToken? = null,
        val swipeRight: KeyActionToken? = null,
        val extra: KeyActionToken? = null,
    ) : Parcelable {
        /** The action slots by [KeyBehavior]; [KeyBehavior.CLICK] is always present, the rest only when set. */
        @IgnoredOnParcel
        val behaviors: Map<KeyBehavior, KeyActionToken?> =
            buildMap {
                KeyBehavior.entries.forEach { behavior ->
                    val token = tokenOf(behavior)
                    if (token != null || behavior == KeyBehavior.CLICK) put(behavior, token)
                }
            }

        val hasClickAction: Boolean get() = click != null

        private fun tokenOf(behavior: KeyBehavior): KeyActionToken? = when (behavior) {
            KeyBehavior.COMPOSING -> composing
            KeyBehavior.HAS_MENU -> hasMenu
            KeyBehavior.PAGING -> paging
            KeyBehavior.COMBO -> combo
            KeyBehavior.ASCII -> ascii
            KeyBehavior.CLICK -> click
            KeyBehavior.DOUBLE_CLICK -> doubleClick
            KeyBehavior.LAZY_DOUBLE_CLICK -> lazyDoubleClick
            KeyBehavior.SWIPE_UP -> swipeUp
            KeyBehavior.LONG_CLICK -> longClick
            KeyBehavior.SWIPE_DOWN -> swipeDown
            KeyBehavior.SWIPE_LEFT -> swipeLeft
            KeyBehavior.SWIPE_RIGHT -> swipeRight
            KeyBehavior.EXTRA -> extra
        }

        companion object {
            private fun decodeToken(
                node: Node.Mapping,
                behavior: KeyBehavior,
            ): KeyActionToken? = KeyActionToken.decode(node[behavior.name.lowercase()])?.takeIf {
                when (it) {
                    is KeyActionToken.Plain -> it.token.isNotEmpty()
                    is KeyActionToken.Inline -> listOfNotNull(it.token.commit, it.token.text, it.token.label).isNotEmpty()
                }
            }

            fun decode(node: Node.Mapping): TextKey = TextKey(
                width = node["width"]?.float ?: 0f,
                height = node["height"]?.float ?: 0f,
                roundCorner = node["round_corner"]?.float ?: -1f,
                keyBorder = node["key_border"]?.int ?: -1,
                label = node["label"]?.string ?: "",
                labelSymbol = node["label_symbol"]?.string ?: "",
                hint = node["hint"]?.string ?: "",
                sendBindings = node["send_bindings"]?.boolean ?: true,
                keyTextSize = node["key_text_size"]?.float ?: 0f,
                symbolTextSize = node["symbol_text_size"]?.float ?: 0f,
                keyTextOffsetX = node["key_text_offset_x"]?.float ?: 0f,
                keyTextOffsetY = node["key_text_offset_y"]?.float ?: 0f,
                keySymbolOffsetX = node["key_symbol_offset_x"]?.float ?: 0f,
                keySymbolOffsetY = node["key_symbol_offset_y"]?.float ?: 0f,
                keyHintOffsetX = node["key_hint_offset_x"]?.float ?: 0f,
                keyHintOffsetY = node["key_hint_offset_y"]?.float ?: 0f,
                keyPressOffsetX = node["key_press_offset_x"]?.float ?: 0f,
                keyPressOffsetY = node["key_press_offset_y"]?.float ?: 0f,
                keyTextColor = node["key_text_color"]?.string ?: "",
                keyBackColor = node["key_back_color"]?.string ?: "",
                keyBorderColor = node["key_border_color"]?.string ?: "",
                keySymbolColor = node["key_symbol_color"]?.string ?: "",
                hlKeyTextColor = node["hilited_key_text_color"]?.string ?: "",
                hlKeyBackColor = node["hilited_key_back_color"]?.string ?: "",
                hlKeyBorderColor = node["hilited_key_border_color"]?.string ?: "",
                hlKeySymbolColor = node["hilited_key_symbol_color"]?.string ?: "",
                popup = node["popup"]?.sequence?.mapNotNull(Node::string) ?: emptyList(),
                hideInLandscape = node["hide_in_landscape"]?.boolean ?: false,
                composing = decodeToken(node, KeyBehavior.COMPOSING),
                hasMenu = decodeToken(node, KeyBehavior.HAS_MENU),
                paging = decodeToken(node, KeyBehavior.PAGING),
                combo = decodeToken(node, KeyBehavior.COMBO),
                ascii = decodeToken(node, KeyBehavior.ASCII),
                click = decodeToken(node, KeyBehavior.CLICK),
                doubleClick = decodeToken(node, KeyBehavior.DOUBLE_CLICK),
                lazyDoubleClick = decodeToken(node, KeyBehavior.LAZY_DOUBLE_CLICK),
                swipeUp = decodeToken(node, KeyBehavior.SWIPE_UP),
                longClick = decodeToken(node, KeyBehavior.LONG_CLICK),
                swipeDown = decodeToken(node, KeyBehavior.SWIPE_DOWN),
                swipeLeft = decodeToken(node, KeyBehavior.SWIPE_LEFT),
                swipeRight = decodeToken(node, KeyBehavior.SWIPE_RIGHT),
                extra = decodeToken(node, KeyBehavior.EXTRA),
            )
        }
    }

    companion object {
        fun decode(node: Node.Mapping): TextKeyboard = TextKeyboard(
            name = node["name"]?.string ?: "",
            author = node["author"]?.string ?: "",
            width = node["width"]?.float ?: 0f,
            height = node["height"]?.float ?: 0f,
            keyboardHeight = node["keyboard_height"]?.int ?: 0,
            keyboardHeightLand = node["keyboard_height_land"]?.int ?: 0,
            autoHeightIndex = node["auto_height_index"]?.int ?: -1,
            horizontalGap = node["horizontal_gap"]?.int ?: 0,
            verticalGap = node["vertical_gap"]?.int ?: 0,
            roundCorner = node["round_corner"]?.float ?: -1f,
            keyBorder = node["key_border"]?.int ?: -1,
            columns = node["columns"]?.int ?: 30,
            asciiMode = (node["ascii_mode"]?.int ?: 1) == 1,
            resetAsciiMode = node["reset_ascii_mode"]?.boolean ?: false,
            labelTransform = node["label_transform"]?.enum<LabelTransform>() ?: LabelTransform.NONE,
            lock = node["lock"]?.boolean ?: false,
            asciiKeyboard = node["ascii_keyboard"]?.string ?: "",
            landscapeKeyboard = node["landscape_keyboard"]?.string ?: "",
            landscapeSplitPercent = node["landscape_split_percent"]?.int ?: 0,
            keyTextOffsetX = node["key_text_offset_x"]?.float ?: 0f,
            keyTextOffsetY = node["key_text_offset_y"]?.float ?: 0f,
            keySymbolOffsetX = node["key_symbol_offset_x"]?.float ?: 0f,
            keySymbolOffsetY = node["key_symbol_offset_y"]?.float ?: 0f,
            keyHintOffsetX = node["key_hint_offset_x"]?.float ?: 0f,
            keyHintOffsetY = node["key_hint_offset_y"]?.float ?: 0f,
            keyPressOffsetX = node["key_press_offset_x"]?.float ?: 0f,
            keyPressOffsetY = node["key_press_offset_y"]?.float ?: 0f,
            importPreset = node["import_preset"]?.string ?: "",
            keys = node["keys"]?.sequence?.mapNotNull {
                TextKey.decode(it.mapping!!)
            } ?: emptyList(),
        )
    }
}
