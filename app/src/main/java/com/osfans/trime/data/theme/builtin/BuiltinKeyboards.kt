/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.builtin

import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.data.theme.model.TextKeyboard.TextKey
import com.osfans.trime.data.theme.model.commit
import com.osfans.trime.data.theme.model.key
import com.osfans.trime.data.theme.model.text

private const val FUNC_KEY_BACK = "func_key_back_color"
private const val FUNC_KEY_HILITED_BACK = "func_key_hilited_back_color"

/**
 * Keyboard layouts of the built-in theme, keyed by the id schemas and `select:` refer to.
 *
 * Keys are laid out left to right and wrap once a row adds up to 100 (% of the keyboard width);
 * a key without `width` takes the keyboard's `width`, a key without `click` is an empty spacer.
 * Action slots take [key] (preset key name, keysym or text), [commit] or [text].
 */
object BuiltinKeyboards {
    /**
     * Last row shared by `default`, `english`, `t9`, `symbols`, `symbols2`, `symbols_en`, `symbols2_en`, `number`;
     * portrait only. Corners keep the old globe / mic cells, the middle three share the rest evenly.
     */
    private val bottomBar =
        listOf(
            // 左下角：方案选单，长按选系统输入法
            TextKey(click = key("ios_schema"), labelSymbol = " ", longClick = key("IME_switch"), width = 12f, height = 44f, keyTextSize = 24f, keyBackColor = "0x00000000", hlKeyBackColor = FUNC_KEY_HILITED_BACK, hideInLandscape = true),
            TextKey(width = 8f, hideInLandscape = true),
            // 中间：表情、剪贴板、编辑面板（方向键）
            TextKey(click = key("ios_emoji"), width = 20f, keyTextSize = 24f, keyBackColor = "0x00000000", hlKeyBackColor = FUNC_KEY_HILITED_BACK, hideInLandscape = true),
            TextKey(click = key("ios_clipboard"), width = 20f, keyTextSize = 24f, keyBackColor = "0x00000000", hlKeyBackColor = FUNC_KEY_HILITED_BACK, hideInLandscape = true),
            TextKey(click = key("ios_edit"), width = 20f, keyTextSize = 24f, keyBackColor = "0x00000000", hlKeyBackColor = FUNC_KEY_HILITED_BACK, hideInLandscape = true),
            TextKey(width = 8f, hideInLandscape = true),
            // 右下角：语音
            TextKey(click = key("ios_mic"), width = 12f, keyTextSize = 24f, keyBackColor = "0x00000000", hlKeyBackColor = FUNC_KEY_HILITED_BACK, hideInLandscape = true),
        )

    private val default =
        TextKeyboard(
            name = "iOS 中文",
            author = "generated",
            width = 10f,
            height = 53f,
            resetAsciiMode = true,
            lock = true,
            asciiKeyboard = "english",
            asciiMode = false,
            keys =
            listOf(
                // 第 1 行：q w e r t y u i o p
                TextKey(click = key("q"), longClick = key("1"), swipeUp = key("1")),
                TextKey(click = key("w"), longClick = key("2"), swipeUp = key("2")),
                TextKey(click = key("e"), longClick = key("3"), swipeUp = key("3")),
                TextKey(click = key("r"), longClick = key("4"), swipeUp = key("4")),
                TextKey(click = key("t"), longClick = key("5"), swipeUp = key("5")),
                TextKey(click = key("y"), longClick = key("6"), swipeUp = key("6")),
                TextKey(click = key("u"), longClick = key("7"), swipeUp = key("7")),
                TextKey(click = key("i"), longClick = key("8"), swipeUp = key("8")),
                TextKey(click = key("o"), longClick = key("9"), swipeUp = key("9")),
                TextKey(click = key("p"), longClick = key("0"), swipeUp = key("0")),
                // 第 2 行：_ a s d f g h j k l _
                TextKey(width = 5f),
                TextKey(click = key("a"), labelSymbol = "ic@select-all", longClick = key("select_all")),
                TextKey(click = key("s"), labelSymbol = "-", longClick = key("-"), swipeUp = key("-")),
                TextKey(click = key("d"), labelSymbol = "/", longClick = commit("/"), swipeUp = commit("/")),
                TextKey(click = key("f"), labelSymbol = "：", longClick = text("："), swipeUp = text("：")),
                TextKey(click = key("g"), labelSymbol = "；", longClick = text("；"), swipeUp = text("；")),
                TextKey(click = key("h"), labelSymbol = "（", longClick = text("（"), swipeUp = text("（")),
                TextKey(click = key("j"), labelSymbol = "）", longClick = text("）"), swipeUp = text("）")),
                TextKey(click = key("k"), labelSymbol = "“”", longClick = text("“”{Left}", label = "“”"), swipeUp = text("“”{Left}", label = "“”")),
                TextKey(click = key("l"), labelSymbol = "@", longClick = key("@"), swipeUp = key("@")),
                TextKey(width = 5f),
                // 第 3 行：ios_shift z x c v b n m ios_backspace
                TextKey(click = key("ios_shift"), width = 15f, keyTextSize = 22f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("z"), labelSymbol = "、", longClick = text("、"), swipeUp = text("、")),
                TextKey(click = key("x"), labelSymbol = "ic@content-cut", longClick = key("cut")),
                TextKey(click = key("c"), labelSymbol = "ic@content-copy", longClick = key("copy")),
                TextKey(click = key("v"), labelSymbol = "ic@content-paste", longClick = key("paste")),
                TextKey(click = key("b"), labelSymbol = "？", longClick = text("？"), swipeUp = text("？")),
                TextKey(click = key("n"), labelSymbol = "！", longClick = text("！"), swipeUp = text("！")),
                TextKey(click = key("m"), labelSymbol = "…", longClick = text("……"), swipeUp = text("……")),
                TextKey(click = key("ios_backspace"), swipeLeft = key("Escape"), width = 15f, keyTextSize = 22f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                // 第 4 行：ios_symbols ， 空格 。 ios_to_en ios_return
                TextKey(click = key("ios_symbols"), labelSymbol = " ", longClick = key("ios_number"), swipeUp = key("liquid_keyboard_switch"), width = 12f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key(","), label = "，", width = 10f),
                TextKey(click = key("ios_space"), label = "空格", width = 34f),
                TextKey(click = key("."), label = "。", width = 10f),
                TextKey(click = key("ios_to_en"), labelSymbol = " ", longClick = key("Menu"), width = 12f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("ios_return"), width = 22f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
            ) + bottomBar,
        )

    private val english =
        TextKeyboard(
            name = "iOS 英文",
            author = "generated",
            width = 10f,
            height = 53f,
            resetAsciiMode = true,
            lock = true,
            asciiKeyboard = "english",
            keys =
            listOf(
                // 第 1 行：q w e r t y u i o p
                TextKey(click = key("q"), longClick = key("1"), swipeUp = key("1")),
                TextKey(click = key("w"), longClick = key("2"), swipeUp = key("2")),
                TextKey(click = key("e"), longClick = key("3"), swipeUp = key("3")),
                TextKey(click = key("r"), longClick = key("4"), swipeUp = key("4")),
                TextKey(click = key("t"), longClick = key("5"), swipeUp = key("5")),
                TextKey(click = key("y"), longClick = key("6"), swipeUp = key("6")),
                TextKey(click = key("u"), longClick = key("7"), swipeUp = key("7")),
                TextKey(click = key("i"), longClick = key("8"), swipeUp = key("8")),
                TextKey(click = key("o"), longClick = key("9"), swipeUp = key("9")),
                TextKey(click = key("p"), longClick = key("0"), swipeUp = key("0")),
                // 第 2 行：_ a s d f g h j k l _
                TextKey(width = 5f),
                TextKey(click = key("a"), labelSymbol = "ic@select-all", longClick = key("select_all")),
                TextKey(click = key("s"), labelSymbol = "-", longClick = commit("-"), swipeUp = commit("-")),
                TextKey(click = key("d"), labelSymbol = "/", longClick = commit("/"), swipeUp = commit("/")),
                TextKey(click = key("f"), labelSymbol = ":", longClick = commit(":"), swipeUp = commit(":")),
                TextKey(click = key("g"), labelSymbol = ";", longClick = commit(";"), swipeUp = commit(";")),
                TextKey(click = key("h"), labelSymbol = "(", longClick = commit("("), swipeUp = commit("(")),
                TextKey(click = key("j"), labelSymbol = ")", longClick = commit(")"), swipeUp = commit(")")),
                TextKey(click = key("k"), labelSymbol = "\"", longClick = commit("\""), swipeUp = commit("\"")),
                TextKey(click = key("l"), labelSymbol = "@", longClick = commit("@"), swipeUp = commit("@")),
                TextKey(width = 5f),
                // 第 3 行：ios_shift z x c v b n m ios_backspace
                TextKey(click = key("ios_shift"), width = 15f, keyTextSize = 22f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("z"), labelSymbol = "'", longClick = commit("'"), swipeUp = commit("'")),
                TextKey(click = key("x"), labelSymbol = "ic@content-cut", longClick = key("cut")),
                TextKey(click = key("c"), labelSymbol = "ic@content-copy", longClick = key("copy")),
                TextKey(click = key("v"), labelSymbol = "ic@content-paste", longClick = key("paste")),
                TextKey(click = key("b"), labelSymbol = "?", longClick = commit("?"), swipeUp = commit("?")),
                TextKey(click = key("n"), labelSymbol = "!", longClick = commit("!"), swipeUp = commit("!")),
                TextKey(click = key("m"), labelSymbol = "#", longClick = commit("#"), swipeUp = commit("#")),
                TextKey(click = key("ios_backspace"), swipeLeft = key("Escape"), width = 15f, keyTextSize = 22f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                // 第 4 行：ios_symbols_en , ios_space . ios_to_zh ios_return
                TextKey(click = key("ios_symbols_en"), labelSymbol = " ", longClick = key("ios_number"), swipeUp = key("liquid_keyboard_switch"), width = 12f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = commit(","), width = 10f),
                TextKey(click = key("ios_space"), width = 34f),
                TextKey(click = commit("."), width = 10f),
                TextKey(click = key("ios_to_zh"), labelSymbol = " ", longClick = key("Menu"), width = 12f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("ios_return"), width = 22f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
            ) + bottomBar,
        )

    private val t9 =
        TextKeyboard(
            name = "iOS 九宫格",
            author = "generated",
            width = 20f,
            height = 53f,
            resetAsciiMode = true,
            lock = true,
            asciiKeyboard = "english",
            asciiMode = false,
            keys =
            listOf(
                // 第 1 行：, 分词 ABC DEF ios_backspace
                TextKey(click = key(","), width = 14f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("'"), label = "分词", width = 22f, keyTextSize = 16f),
                TextKey(click = key("2"), label = "ABC", width = 22f, keyTextSize = 20f),
                TextKey(click = key("3"), label = "DEF", width = 22f, keyTextSize = 20f),
                TextKey(click = key("ios_backspace"), swipeLeft = key("Escape"), width = 20f, keyTextSize = 22f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                // 第 2 行：. GHI JKL MNO 重输
                TextKey(click = key("."), width = 14f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("4"), label = "GHI", width = 22f, keyTextSize = 20f),
                TextKey(click = key("5"), label = "JKL", width = 22f, keyTextSize = 20f),
                TextKey(click = key("6"), label = "MNO", width = 22f, keyTextSize = 20f),
                TextKey(click = key("Escape"), label = "重输", width = 20f, keyTextSize = 16f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                // 第 3 行：? PQRS TUV WXYZ 0
                TextKey(click = key("?"), width = 14f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("7"), label = "PQRS", width = 22f, keyTextSize = 20f),
                TextKey(click = key("8"), label = "TUV", width = 22f, keyTextSize = 20f),
                TextKey(click = key("9"), label = "WXYZ", width = 22f, keyTextSize = 20f),
                TextKey(click = key("0"), width = 20f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                // 第 4 行：! 符 123 空格 ZH ios_return
                TextKey(click = key("!"), width = 14f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("ios_symbols"), label = "符", width = 14f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("ios_number"), label = "123", width = 14f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("ios_space"), label = "空格", width = 30f),
                TextKey(click = key("ios_to_en"), label = "ZH", width = 14f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("ios_return"), width = 14f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
            ) + bottomBar,
        )

    private val t9Land =
        TextKeyboard(
            name = "iOS 九宫格（横屏）",
            author = "generated",
            width = 20f,
            height = 53f,
            resetAsciiMode = true,
            lock = true,
            asciiKeyboard = "english",
            asciiMode = false,
            keys =
            listOf(
                // 第 1 行：+ 1 2 3 , 分词 ABC DEF ios_backspace
                TextKey(click = commit("+"), width = 10f),
                TextKey(click = commit("1"), width = 10f),
                TextKey(click = commit("2"), width = 10f),
                TextKey(click = commit("3"), width = 10f),
                TextKey(click = key(","), width = 9f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("'"), label = "分词", width = 13f, keyTextSize = 16f),
                TextKey(click = key("2"), label = "ABC", width = 13f, keyTextSize = 20f),
                TextKey(click = key("3"), label = "DEF", width = 13f, keyTextSize = 20f),
                TextKey(click = key("ios_backspace"), swipeLeft = key("Escape"), width = 12f, keyTextSize = 22f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                // 第 2 行：- 4 5 6 . GHI JKL MNO 重输
                TextKey(click = commit("-"), width = 10f),
                TextKey(click = commit("4"), width = 10f),
                TextKey(click = commit("5"), width = 10f),
                TextKey(click = commit("6"), width = 10f),
                TextKey(click = key("."), width = 9f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("4"), label = "GHI", width = 13f, keyTextSize = 20f),
                TextKey(click = key("5"), label = "JKL", width = 13f, keyTextSize = 20f),
                TextKey(click = key("6"), label = "MNO", width = 13f, keyTextSize = 20f),
                TextKey(click = key("Escape"), label = "重输", width = 12f, keyTextSize = 16f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                // 第 3 行：* 7 8 9 ? PQRS TUV WXYZ @
                TextKey(click = commit("*"), width = 10f),
                TextKey(click = commit("7"), width = 10f),
                TextKey(click = commit("8"), width = 10f),
                TextKey(click = commit("9"), width = 10f),
                TextKey(click = key("?"), width = 9f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("7"), label = "PQRS", width = 13f, keyTextSize = 20f),
                TextKey(click = key("8"), label = "TUV", width = 13f, keyTextSize = 20f),
                TextKey(click = key("9"), label = "WXYZ", width = 13f, keyTextSize = 20f),
                TextKey(click = commit("@"), width = 12f),
                // 第 4 行：/ 符 0 . ! 空格 ZH ios_return
                TextKey(click = commit("/"), width = 10f),
                TextKey(click = key("ios_symbols"), label = "符", width = 10f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = commit("0"), width = 10f),
                TextKey(click = commit("."), width = 10f),
                TextKey(click = key("!"), width = 9f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("ios_space"), label = "空格", width = 25f),
                TextKey(click = key("ios_to_en"), label = "ZH", width = 13f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("ios_return"), width = 13f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
            ),
        )

    private val symbols =
        TextKeyboard(
            name = "iOS 数字符号",
            author = "generated",
            width = 10f,
            height = 53f,
            asciiMode = false,
            keys =
            listOf(
                // 第 1 行：1 2 3 4 5 6 7 8 9 0
                TextKey(click = key("KP_1"), label = "1"),
                TextKey(click = key("KP_2"), label = "2"),
                TextKey(click = key("KP_3"), label = "3"),
                TextKey(click = key("KP_4"), label = "4"),
                TextKey(click = key("KP_5"), label = "5"),
                TextKey(click = key("KP_6"), label = "6"),
                TextKey(click = key("KP_7"), label = "7"),
                TextKey(click = key("KP_8"), label = "8"),
                TextKey(click = key("KP_9"), label = "9"),
                TextKey(click = key("KP_0"), label = "0"),
                // 第 2 行：- / ： ； （ ） ￥ @ “” 「」
                TextKey(click = key("-")),
                TextKey(click = key("/"), label = "/"),
                TextKey(click = key(":"), label = "："),
                TextKey(click = key(";"), label = "；"),
                TextKey(click = key("("), label = "（"),
                TextKey(click = key(")"), label = "）"),
                TextKey(click = key("\$"), label = "￥"),
                TextKey(click = key("@")),
                TextKey(click = key("\""), label = "“”"),
                TextKey(click = key("'"), label = "「」"),
                // 第 3 行：ios_symbols2 。 ， 、 ？ ！ . ios_backspace
                TextKey(click = key("ios_symbols2"), width = 15f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("."), label = "。", width = 11.66f),
                TextKey(click = key(","), label = "，", width = 11.66f),
                TextKey(click = key("\\"), label = "、", width = 11.66f),
                TextKey(click = key("?"), label = "？", width = 11.66f),
                TextKey(click = key("!"), label = "！", width = 11.66f),
                TextKey(click = commit("."), width = 11.66f),
                TextKey(click = key("ios_backspace"), swipeLeft = key("Escape"), width = 15f, keyTextSize = 22f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                // 第 4 行：ios_abc 空格 ios_return
                TextKey(click = key("ios_abc"), width = 12f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("ios_space"), label = "空格", width = 66f),
                TextKey(click = key("ios_return"), width = 22f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
            ) + bottomBar,
        )

    private val symbols2 =
        TextKeyboard(
            name = "iOS 更多符号",
            author = "generated",
            width = 10f,
            height = 53f,
            asciiMode = false,
            keys =
            listOf(
                // 第 1 行：[ ] { } # % ^ * + =
                TextKey(click = commit("[")),
                TextKey(click = commit("]")),
                TextKey(click = commit("{")),
                TextKey(click = commit("}")),
                TextKey(click = commit("#")),
                TextKey(click = commit("%")),
                TextKey(click = commit("^")),
                TextKey(click = commit("*")),
                TextKey(click = commit("+")),
                TextKey(click = commit("=")),
                // 第 2 行：_ \ | ~ 《 》 € & · …
                TextKey(click = commit("_")),
                TextKey(click = commit("\\")),
                TextKey(click = commit("|")),
                TextKey(click = commit("~")),
                TextKey(click = commit("《")),
                TextKey(click = commit("》")),
                TextKey(click = commit("€")),
                TextKey(click = commit("&")),
                TextKey(click = commit("·")),
                TextKey(click = commit("…")),
                // 第 3 行：ios_symbols 。 ， ？ ！ 「」 ios_backspace
                TextKey(click = key("ios_symbols"), width = 15f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("."), label = "。", width = 14f),
                TextKey(click = key(","), label = "，", width = 14f),
                TextKey(click = key("?"), label = "？", width = 14f),
                TextKey(click = key("!"), label = "！", width = 14f),
                TextKey(click = key("'"), label = "「」", width = 14f),
                TextKey(click = key("ios_backspace"), swipeLeft = key("Escape"), width = 15f, keyTextSize = 22f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                // 第 4 行：ios_abc 空格 ios_return
                TextKey(click = key("ios_abc"), width = 12f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("ios_space"), label = "空格", width = 66f),
                TextKey(click = key("ios_return"), width = 22f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
            ) + bottomBar,
        )

    private val symbolsEn =
        TextKeyboard(
            name = "iOS 英文数字符号",
            author = "generated",
            width = 10f,
            height = 53f,
            keys =
            listOf(
                // 第 1 行：1 2 3 4 5 6 7 8 9 0
                TextKey(click = key("KP_1"), label = "1"),
                TextKey(click = key("KP_2"), label = "2"),
                TextKey(click = key("KP_3"), label = "3"),
                TextKey(click = key("KP_4"), label = "4"),
                TextKey(click = key("KP_5"), label = "5"),
                TextKey(click = key("KP_6"), label = "6"),
                TextKey(click = key("KP_7"), label = "7"),
                TextKey(click = key("KP_8"), label = "8"),
                TextKey(click = key("KP_9"), label = "9"),
                TextKey(click = key("KP_0"), label = "0"),
                // 第 2 行：- / : ; ( ) $ & @ "
                TextKey(click = commit("-")),
                TextKey(click = commit("/")),
                TextKey(click = commit(":")),
                TextKey(click = commit(";")),
                TextKey(click = commit("(")),
                TextKey(click = commit(")")),
                TextKey(click = commit("\$")),
                TextKey(click = commit("&")),
                TextKey(click = commit("@")),
                TextKey(click = commit("\"")),
                // 第 3 行：ios_symbols2_en . , ? ! ' ios_backspace
                TextKey(click = key("ios_symbols2_en"), width = 15f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = commit("."), width = 14f),
                TextKey(click = commit(","), width = 14f),
                TextKey(click = commit("?"), width = 14f),
                TextKey(click = commit("!"), width = 14f),
                TextKey(click = commit("'"), width = 14f),
                TextKey(click = key("ios_backspace"), swipeLeft = key("Escape"), width = 15f, keyTextSize = 22f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                // 第 4 行：ios_abc ios_space ios_return
                TextKey(click = key("ios_abc"), width = 12f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("ios_space"), width = 66f),
                TextKey(click = key("ios_return"), width = 22f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
            ) + bottomBar,
        )

    private val symbols2En =
        TextKeyboard(
            name = "iOS 英文更多符号",
            author = "generated",
            width = 10f,
            height = 53f,
            keys =
            listOf(
                // 第 1 行：[ ] { } # % ^ * + =
                TextKey(click = commit("[")),
                TextKey(click = commit("]")),
                TextKey(click = commit("{")),
                TextKey(click = commit("}")),
                TextKey(click = commit("#")),
                TextKey(click = commit("%")),
                TextKey(click = commit("^")),
                TextKey(click = commit("*")),
                TextKey(click = commit("+")),
                TextKey(click = commit("=")),
                // 第 2 行：_ \ | ~ < > € £ ¥ •
                TextKey(click = commit("_")),
                TextKey(click = commit("\\")),
                TextKey(click = commit("|")),
                TextKey(click = commit("~")),
                TextKey(click = commit("<")),
                TextKey(click = commit(">")),
                TextKey(click = commit("€")),
                TextKey(click = commit("£")),
                TextKey(click = commit("¥")),
                TextKey(click = commit("•")),
                // 第 3 行：ios_symbols_en . , ? ! ' ios_backspace
                TextKey(click = key("ios_symbols_en"), width = 15f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = commit("."), width = 14f),
                TextKey(click = commit(","), width = 14f),
                TextKey(click = commit("?"), width = 14f),
                TextKey(click = commit("!"), width = 14f),
                TextKey(click = commit("'"), width = 14f),
                TextKey(click = key("ios_backspace"), swipeLeft = key("Escape"), width = 15f, keyTextSize = 22f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                // 第 4 行：ios_abc ios_space ios_return
                TextKey(click = key("ios_abc"), width = 12f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("ios_space"), width = 66f),
                TextKey(click = key("ios_return"), width = 22f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
            ) + bottomBar,
        )

    private val number =
        TextKeyboard(
            name = "iOS 数字键盘",
            author = "generated",
            width = 25f,
            height = 53f,
            keys =
            listOf(
                // 第 1 行：1 2 3 ios_backspace
                TextKey(click = key("KP_1"), label = "1"),
                TextKey(click = key("KP_2"), label = "2"),
                TextKey(click = key("KP_3"), label = "3"),
                TextKey(click = key("ios_backspace"), keyTextSize = 22f, keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                // 第 2 行：4 5 6 -
                TextKey(click = key("KP_4"), label = "4"),
                TextKey(click = key("KP_5"), label = "5"),
                TextKey(click = key("KP_6"), label = "6"),
                TextKey(click = commit("-")),
                // 第 3 行：7 8 9 .
                TextKey(click = key("KP_7"), label = "7"),
                TextKey(click = key("KP_8"), label = "8"),
                TextKey(click = key("KP_9"), label = "9"),
                TextKey(click = commit(".")),
                // 第 4 行：ios_abc 0 , ios_return
                TextKey(click = key("ios_abc"), keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
                TextKey(click = key("KP_0"), label = "0"),
                TextKey(click = commit(",")),
                TextKey(click = key("ios_return"), keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK),
            ) + bottomBar,
        )

    val keyboards =
        mapOf(
            "default" to default,
            "english" to english,
            "t9" to t9,
            "t9_land" to t9Land,
            "symbols" to symbols,
            "symbols2" to symbols2,
            "symbols_en" to symbolsEn,
            "symbols2_en" to symbols2En,
            "number" to number,
        )
}
