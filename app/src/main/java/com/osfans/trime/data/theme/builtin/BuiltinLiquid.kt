/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.builtin

import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.data.theme.model.LiquidKeyboard.KeyItem
import com.osfans.trime.data.theme.model.LiquidKeyboard.Keyboard
import com.osfans.trime.ime.symbol.LiquidData
import com.osfans.trime.util.splitWithSurrogates

/** One key per character (surrogate pairs kept together). */
private fun singles(chars: String) = chars.splitWithSurrogates().map { KeyItem(it) }

/** The symbol / emoji panel ("liquid keyboard"): its tabs in display order and the bottom key bar. */
object BuiltinLiquid {
    private val keyboards =
        listOf(
            Keyboard(
                id = "emoji",
                type = LiquidData.Type.SINGLE,
                keys = singles("🙂😂🤣😆🙃😅🥺😎👀🙈🙉🙊☹😑😄🤐😨😱🌚🌝🤔❤💔🌹💣👌👍😣😥😮🙄😏😕😯😪😫😴😌🤑😉😋😎😍😘😚😛😜😝😒😓😔😲😷🤒😇🤓🤗🤕🙁😖😞😟😤😢😭😦😧😨😩😬😰😳😵😡😠☝✌🖕👎🙏🤘👏💪💋☘🍀🌸☕🍵🍺🍻🍦🍬🍚🍜🍲🍖🎂💤"),
            ),
            Keyboard(
                id = "math",
                type = LiquidData.Type.SINGLE,
                name = "数学",
                keys = singles("≈＝≠≌<>≤≥≡()[]{}-+±×*/÷&∥%‰‱°′″∫∮∯∬∭∰∞∑∧∏∈∵∴⊥∝∨∪•√〒∝∽∈∩∧⊙⌒∥∟∣∂∆∞≌∉∪∨⊕⊿⊥∠∫∬∭"),
            ),
            Keyboard(
                id = "ascii",
                type = LiquidData.Type.SINGLE,
                name = "英文",
                keys = singles(",.?!:;/\\|*-+=\$`'\"^~@#%&()[]{}_"),
            ),
            Keyboard(
                id = "cn",
                type = LiquidData.Type.SINGLE,
                name = "中文",
                keys =
                listOf(
                    KeyItem("，"), KeyItem("。"), KeyItem("？"), KeyItem("！"), KeyItem("："), KeyItem("、"),
                    KeyItem("“"), KeyItem("”"), KeyItem("‘"), KeyItem("···"), KeyItem("……"),
                    KeyItem("-", ""), KeyItem("——", "破折"), KeyItem("", ""), KeyItem("（"), KeyItem("）"),
                    KeyItem("【"), KeyItem("】"), KeyItem("《"), KeyItem("》"), KeyItem("［"), KeyItem("］"),
                    KeyItem("｛"), KeyItem("｝"), KeyItem("「"), KeyItem("」"), KeyItem("『"), KeyItem("』"),
                    KeyItem("～"),
                ),
            ),
            Keyboard(
                id = "history",
                type = LiquidData.Type.HISTORY,
                name = "常用",
            ),
            Keyboard(
                id = "symbollist",
                type = LiquidData.Type.SYMBOL,
                name = "符号表",
                keys =
                listOf(
                    KeyItem("符号", "/fh"), KeyItem("电脑", "/dn"), KeyItem("象棋", "/xq"), KeyItem("麻将", "/mj"),
                    KeyItem("骰子", "/sz"), KeyItem("扑克", "/pk"), KeyItem("天气", "/tq"), KeyItem("音乐", "/yy"),
                    KeyItem("八卦", "/bg"), KeyItem("易经", "/lssg"), KeyItem("天体", "/tt"), KeyItem("星座", "/xz"),
                    KeyItem("星号", "/xh"), KeyItem("方块", "/fk"), KeyItem("几何", "/jh"), KeyItem("箭头", "/jt"),
                    KeyItem("数学", "/sx"), KeyItem("上标", "/sb"), KeyItem("下标", "/xb"), KeyItem("单位", "/dw"),
                    KeyItem("货币", "/hb"), KeyItem("拼音", "/py"), KeyItem("注音", "/zy"), KeyItem("假名", "/jm"),
                    KeyItem("片假", "/pjm"), KeyItem("韩文", "/hw"), KeyItem("希腊", "/xl"), KeyItem("希大", "/xld"),
                    KeyItem("罗马", "/lm"), KeyItem("罗大", "/lmd"), KeyItem("俄语", "/ey"), KeyItem("俄大", "/eyd"),
                    KeyItem("表情", "/bq"), KeyItem("一", "/1"), KeyItem("二", "/2"), KeyItem("三", "/3"),
                    KeyItem("四", "/4"), KeyItem("五", "/5"), KeyItem("六", "/6"), KeyItem("七", "/7"),
                    KeyItem("八", "/8"), KeyItem("九", "/9"), KeyItem("零", "/0"), KeyItem("十", "/10"),
                    KeyItem("分数", "/fs"), KeyItem("标点", "/bd"), KeyItem("偏旁", "/pp"), KeyItem("竖标", "/bdz"),
                ),
            ),
            Keyboard(
                id = "list",
                type = LiquidData.Type.SINGLE,
                name = "列表",
                keys = singles("①②③④⑤⑥⑦⑧⑨⑩⒈⒉⒊⒋⒌⒍⒎⒏⒐⒑⒒⒓⒔⒕⒖⒗⒘⒙⒚⒛⑴⑵⑶⑷⑸⑹⑺⑻⑼⑽⑾⑿⒀⒁⒂⒃⒄⒅⒆⒇㈠㈡㈢㈣㈤㈥㈦㈧㈨㈩➊➋➌➍➎➏➐➑➒➓㊀㊁㊂㊃㊄㊅㊆㊇㊈㊉ⅰⅱⅲⅳⅴⅵⅶⅷⅸⅹⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ"),
            ),
            Keyboard(
                id = "ids",
                type = LiquidData.Type.SINGLE,
                name = "IDS",
                keys = singles("⿰⿱⿲⿳⿴⿵⿶⿷⿸⿹⿺⿻↷↔"),
            ),
            Keyboard(
                id = "symbol",
                type = LiquidData.Type.SINGLE,
                name = "特殊",
                keys = singles("△▽○◇□☆▲▼●◆■★▷◁▶◀♻♲†⚝✡⚹✦✸✹￼�×⌫☑☒✅❎✔✘✓✗☀☼☽☾◑◐㏂㏘☭♀♂☹☻☠☜☝☞☚☟☛▪•‥…∷※♩♪♫♬§°♭♯♮‖¶№◎¤۞℗®©卍卐℡™㏇Φ⇦⇧⇨⇩⇪↖↑↗←↔→↙↓↘⇄⇅⇆⇤↩⇥▸◂▴▾◤◥◣◢㊤㊧㊥㊨㊦❏❐◲〼▢▣↶✁↷✍⏍ϟ📝✎✆☱☰☴⚿⛮⚙☲☯☵⛶☩☐☳☷☶💬🗨⟲ღ✈☂🎤🌐🔍"),
            ),
            Keyboard(
                id = "tabs",
                type = LiquidData.Type.TABS,
                name = "更多",
            ),
            Keyboard(
                id = "pinyin",
                type = LiquidData.Type.SINGLE,
                name = "拼音",
                keys = singles("āáǎàōóēéěèǒòīíǐìūúǖǘǚǜǔùê\ue7c7üńň\ue7c8ㄚㄛㄜㄧㄨㄩㄝㄞㄟㄠㄡㄢㄣㄤㄥㄦㄅㄆㄇㄈㄉㄊㄋㄌㄍㄎㄏㄐㄑㄒㄓㄔㄕㄖㄗㄘㄙ"),
            ),
            Keyboard(
                id = "script_symbols",
                type = LiquidData.Type.SINGLE,
                name = "𝒶𝒜",
                keys = singles("𝒶𝒷𝒸𝒹ℯ𝒻ℊ𝒽𝒾𝒿𝓀𝓁𝓂𝓃ℴ𝓅𝓆𝓇𝓈𝓉𝓊𝓋𝓌𝓍𝓎𝓏𝒜ℬ𝒞𝒟ℰℱ𝒢ℋℐ𝒥𝒦ℒℳ𝒩𝒪𝒫𝒬ℛ𝒮𝒯𝒰𝒱𝒲𝒳𝒴𝒵"),
            ),
            Keyboard(
                id = "jp",
                type = LiquidData.Type.SINGLE,
                name = "假名",
                keys = singles("あいうえおかがきぎくぐけげこごさざしじすずせぜそぞただちぢつづてでとどなにぬねのはばぱひびぴふぶぷへべぺほぼぽまみむめもゃやゅゆょよらりるれろわをんアィイウェエオカガキギクグケゲコゴサザシジスズセゼソゾタダチヂツヅテデトドナニヌネノハバパヒビピフブプヘベペホボポマミムメモャヤュユョヨラリルレロワヲン"),
            ),
            Keyboard(
                id = "grease",
                type = LiquidData.Type.SINGLE,
                name = "希腊",
                keys = singles("ΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩαβγδεζηθικλμνξοπρστυφχψω"),
            ),
            Keyboard(
                id = "rusa",
                type = LiquidData.Type.SINGLE,
                name = "俄语",
                keys = singles("АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя"),
            ),
            Keyboard(
                id = "korea",
                type = LiquidData.Type.SINGLE,
                name = "韩文",
                keys = singles("dㅏㅑㅓㅕㅗㅛㅜㅠㅡㅣㅐㅒㅔㅖㅘㅙㅚㅝㅞㅟㅢㄱㄴㄷㄹㅁㅂㅅㅇㅈㅊㅋㅌㅍㅎㄲㄸㅚㅆㅉ㉠㉡㉢㉣㉤㉥㉦㉧㉨㉩㉪㉫㉬㉭㉮㉯㉰㉱㉲㉳㉴㉵㉶㉷㉸㉹㉺㉻㈀㈁㈂㈃㈄㈅㈆㈇㈈㈉㈊㈋㈌㈍㈎㈏㈐㈑㈒㈓㈔㈕㈖㈗㈘㈙㈚㈛"),
            ),
            Keyboard(
                id = "lation",
                type = LiquidData.Type.SINGLE,
                name = "拉丁",
                keys = singles("ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞŠŸŒàáâãäåæçèéêëìíîïðñòóõôöøùúûüýþšÿœ"),
            ),
            Keyboard(
                id = "yinbiao",
                type = LiquidData.Type.SINGLE,
                name = "音标",
                keys =
                listOf(
                    KeyItem("a:"), KeyItem("ɔ:"), KeyItem("ɜː"), KeyItem("i:"), KeyItem("u:"), KeyItem("ʌ"),
                    KeyItem("ɒ"), KeyItem("ə"), KeyItem("ɪ"), KeyItem("ʊ"), KeyItem("e"), KeyItem("æ"),
                    KeyItem("eɪ"), KeyItem("aɪ"), KeyItem("ɔɪ"), KeyItem("ɪə"), KeyItem("eə"), KeyItem("ʊə"),
                    KeyItem("əʊ"), KeyItem("aʊ"), KeyItem("p"), KeyItem("t"), KeyItem("k"), KeyItem("f"),
                    KeyItem("θ"), KeyItem("s"), KeyItem("b"), KeyItem("d"), KeyItem("g"), KeyItem("v"),
                    KeyItem("ð"), KeyItem("z"), KeyItem("ʃ"), KeyItem("h"), KeyItem("ts"), KeyItem("tʃ"),
                    KeyItem("j"), KeyItem("tr"), KeyItem("ʒ"), KeyItem("r"), KeyItem("dz"), KeyItem("dʒ"),
                    KeyItem("dr"), KeyItem("w"), KeyItem("m"), KeyItem("n"), KeyItem("ŋ"), KeyItem("l"),
                ),
            ),
            Keyboard(
                id = "unit",
                type = LiquidData.Type.SINGLE,
                name = "单位",
                keys = singles("℃¥\$€฿￡㎡m³℉￥£￠₠¹²³⁴⁵ⁿ⁶⁷⁸⁹⁰ˣ⁺⁻⁼⁽⁾½⅓¼⅔¾₁₂₃₄₅ₙ₆₇₈₉₀ₓ₊₋₌₍₎℅"),
            ),
            Keyboard(
                id = "yanwenzi",
                type = LiquidData.Type.SINGLE,
                name = "颜文字",
                keys =
                listOf(
                    KeyItem("⎛⎝≥⏝⏝≤⎠⎞"), KeyItem("^_^"), KeyItem("^ω^"), KeyItem("^o^"),
                    KeyItem("~\\(≧▽≦)/~"), KeyItem("*^_^*"), KeyItem("↖(^ω^)↗"), KeyItem("(^o^)／"),
                    KeyItem("(=^▽^=)"), KeyItem("=^_^="), KeyItem("(*^ω^*)"), KeyItem("٩(๑^o^๑)۶"),
                    KeyItem("o(￣▽￣)o"), KeyItem("Y(^_^)Y"), KeyItem("٩( 'ω' )و"), KeyItem("╰(*´︶`*)╯"),
                    KeyItem("*罒▽罒*"), KeyItem("ヾ ^_^♪"), KeyItem("=￣ω￣="), KeyItem("︿(￣︶￣)︿"),
                    KeyItem("(´▽｀)ノ♪"), KeyItem("乁( ˙ ω˙乁)"), KeyItem("✧*｡٩(ˊωˋ*)و✧*｡"),
                    KeyItem("～(￣▽￣～)(～￣▽￣)～"), KeyItem("QwQ"), KeyItem("(●—●)"), KeyItem("(๑• . •๑)"),
                    KeyItem("ヾ(≧O≦)〃嗷~"), KeyItem("罒ω罒"), KeyItem("(｡ì _ í｡)"),
                    KeyItem("(๑•\u0e35_เ•\u0e35๑)"), KeyItem("ㄟ(≧◇≦)ㄏ"), KeyItem("(*/ω＼*)"), KeyItem("●▽●"),
                    KeyItem("٩(๑òωó๑)۶"), KeyItem("✺◟(∗❛\u0e31ᴗ❛\u0e31∗)◞✺"), KeyItem("( σ'ω')σ"),
                    KeyItem("♡＾▽＾♡"), KeyItem("(๑•\u0300ㅂ•\u0301)و✧"), KeyItem("(ง •\u0300_•\u0301)ง"),
                    KeyItem("(｡･ω･｡)ﾉ♡"), KeyItem("(☆_☆)"), KeyItem("(๑°3°๑)"),
                    KeyItem("_(•\u0300ω•\u0301 」∠)_"), KeyItem("♪～(´ε｀\u3000)"), KeyItem("～(^з^)-☆"),
                    KeyItem("(´∀｀)♡"), KeyItem("ლ(´ڡ`ლ)"), KeyItem("(＞﹏＜)"), KeyItem("T_T"), KeyItem("⊙︿⊙"),
                    KeyItem("〒▽〒"), KeyItem("⊙﹏⊙"), KeyItem("π_π"), KeyItem("(｡•\u0301︿•\u0300｡)"),
                    KeyItem("(ToT)/~~~"), KeyItem("╯﹏╰"), KeyItem("ಥ_ಥ"), KeyItem("(╥╯^╰╥)"),
                    KeyItem("(〃′o`)"), KeyItem("●﹏●"), KeyItem("( •\u0325\u0301 ˍ •\u0300\u0942 )"),
                    KeyItem("(つд⊂)"), KeyItem("心塞(´-ωก`)"), KeyItem("(╥﹏╥)"), KeyItem("┭┮﹏┭┮"),
                    KeyItem("（；´д｀）ゞ"), KeyItem("(´;︵;`)"), KeyItem("(。﹏。)"), KeyItem("┗( T﹏T )┛"),
                    KeyItem("QAQ"), KeyItem("ヘ(_ _ヘ)"), KeyItem("╰（‵□′）╯"), KeyItem("(*￣︿￣)"),
                    KeyItem(">o<"), KeyItem("(-`ェ´-怒)"), KeyItem("ヽ(‘⌒´メ)ノ"), KeyItem("(*｀Ω´*)v"),
                    KeyItem("(｡•ˇ‸ˇ•｡)"), KeyItem("(怒｀Д´怒)"), KeyItem("٩(๑`^´๑)۶"), KeyItem("(;｀O´)o"),
                    KeyItem("╰_╯"), KeyItem("(#｀皿´)<怒怒怒!!!"), KeyItem("<(｀^´)>"), KeyItem("（｀Δ´）！"),
                    KeyItem("ψ(｀∇´)ψ"), KeyItem("(；′⌒`)"), KeyItem("s(・｀ヘ´・;)ゞ"), KeyItem("(▼皿▼#)"),
                    KeyItem("￣へ￣"), KeyItem("←_←"), KeyItem("（╯‵□′）╯︵┴─┴"), KeyItem("（▼へ▼メ）"),
                    KeyItem("☄\u0e3a(◣д◢)☄\u0e3a"), KeyItem("→_→"), KeyItem("⊙_⊙"), KeyItem("d(ŐдŐ๑)"),
                    KeyItem("Σ( ° △ °|||)︴"), KeyItem("(((φ(◎ロ◎;)φ)))"), KeyItem("⊙▽⊙"), KeyItem("(๑ŐдŐ)b"),
                    KeyItem("╭(°A°`)╮"), KeyItem("(๑òᆺó๑)"), KeyItem("⊙ω⊙"), KeyItem("Σ(っ °Д °;)っ"),
                    KeyItem(" (ﾟДﾟ≡ﾟдﾟ)!?"), KeyItem("( *・ω・)✄╰ひ╯"), KeyItem("(⊙x⊙;)"), KeyItem("┌(。Д。)┐"),
                    KeyItem("(°ー°〃)"), KeyItem("︽⊙_⊙︽"), KeyItem("!!!∑(°Д°ノ)ノ"), KeyItem("(๑ŐдŐ)b☆d(ŐдŐ๑)"),
                    KeyItem("Σ⊙▃⊙川"), KeyItem("ヽ(*。>Д<)o゜"), KeyItem("━((*′д｀)爻(′д｀*))━!!!!"),
                    KeyItem("=_="), KeyItem("╮(╯_╰)╭"), KeyItem("（︶︿︶）"), KeyItem("_(:з」∠)_"),
                    KeyItem("@_@"), KeyItem("╮(╯▽╰)╭"), KeyItem("(＠￣ー￣＠)"), KeyItem("_(:3」∠❀)_"),
                    KeyItem("눈_눈"), KeyItem("╭(╯ε╰)╮"), KeyItem("(ー_ー)!!"), KeyItem("_(:D)∠)_"),
                    KeyItem("o_O"), KeyItem("╭(╯^╰)╮"), KeyItem("(；一_一)"), KeyItem("´_>`"), KeyItem("-_-#"),
                    KeyItem("┑(￣Д ￣)┍"), KeyItem("≡￣﹏￣≡"), KeyItem("○|￣|_"), KeyItem("-_-||"),
                    KeyItem("ㄟ( ▔, ▔ )ㄏ"), KeyItem("( ＿ ＿)ノ｜壁"), KeyItem("▄█▀█●"),
                ),
            ),
            Keyboard(
                id = "combing",
                type = LiquidData.Type.SINGLE,
                name = "组合",
                keys =
                listOf(
                    KeyItem("\u20e2"),
                    KeyItem("\uabed"),
                    KeyItem("\u0323"),
                    KeyItem("\u0332"),
                ),
            ),
            Keyboard(
                id = "emoji_full",
                type = LiquidData.Type.SINGLE,
                name = "全emoji",
                keys = singles("😀😃😄😁😆😅🤣😂🙂🙃🫠😉😊😇🥰😍🤩😘😗☺😚😙🥲😋😛😜🤪😝🤑🤗🤭🫢🫣🤫🤔🫡🤐🤨😐😑😶🫥😏😒🙄😬🤥😌😔😪🤤😴😷🤒🤕🤢🤮🤧🥵🥶🥴😵🤯🤠🥳🥸😎🤓🧐😕🫤😟🙁☹😮😯😲😳🥺🥹😦😧😨😰😥😢😭😱😖😣😞😓😩😫🥱😤😡😠🤬😈👿💀☠💩🤡👹👺👻👽👾🤖😺😸😹😻😼😽🙀😿😾🙈🙉🙊💌💘💝💖💗💓💞💕💟❣💔❤🧡💛💚💙💜🤎🖤🤍💋💯💢💥💫💦💨🕳💬🗨🗯💭💤👋🤚🖐✋🖖🫱🫲🫳🫴👌🤌🤏✌🤞🫰🤟🤘🤙👈👉👆🖕👇☝🫵👍👎✊👊🤛🤜👏🙌🫶👐🤲🤝🙏✍💅🤳💪🦾🦿🦵🦶👂🦻👃🧠🫀🫁🦷🦴👀👁👅👄🫦👶🧒👦👧🧑👱👨🧔👩🧓👴👵🙍🙎🙅🙆💁🙋🧏🙇🤦🤷👮🕵💂🥷👷🫅🤴👸👳👲🧕🤵👰🤰🫃🫄🤱👼🎅🤶🦸🦹🧙🧚🧛🧜🧝🧞🧟🧌💆💇🚶🧍🧎🏃💃🕺🕴👯🧖🧗🤺🏇⛷🏂🏌🏄🚣🏊⛹🏋🚴🚵🤸🤼🤽🤾🤹🧘🛀🛌👭👫👬💏💑👪🗣👤👥🫂👣🏻🏼🏽🏾🏿🦰🦱🦳🦲🐵🐒🦍🦧🐶🐕🦮🐩🐺🦊🦝🐱🐈🦁🐯🐅🐆🐴🐎🦄🦓🦌🦬🐮🐂🐃🐄🐷🐖🐗🐽🐏🐑🐐🐪🐫🦙🦒🐘🦣🦏🦛🐭🐁🐀🐹🐰🐇🐿🦫🦔🦇🐻🐨🐼🦥🦦🦨🦘🦡🐾🦃🐔🐓🐣🐤🐥🐦🐧🕊🦅🦆🦢🦉🦤🪶🦩🦚🦜🐸🐊🐢🦎🐍🐲🐉🦕🦖🐳🐋🐬🦭🐟🐠🐡🦈🐙🐚🪸🐌🦋🐛🐜🐝🪲🐞🦗🪳🕷🕸🦂🦟🪰🪱🦠💐🌸💮🪷🏵🌹🥀🌺🌻🌼🌷🌱🪴🌲🌳🌴🌵🌾🌿☘🍀🍁🍂🍃🪹🪺🍄🍇🍈🍉🍊🍋🍌🍍🥭🍎🍏🍐🍑🍒🍓🫐🥝🍅🫒🥥🥑🍆🥔🥕🌽🌶🫑🥒🥬🥦🧄🧅🥜🫘🌰🍞🥐🥖🫓🥨🥯🥞🧇🧀🍖🍗🥩🥓🍔🍟🍕🌭🥪🌮🌯🫔🥙🧆🥚🍳🥘🍲🫕🥣🥗🍿🧈🧂🥫🍱🍘🍙🍚🍛🍜🍝🍠🍢🍣🍤🍥🥮🍡🥟🥠🥡🦀🦞🦐🦑🦪🍦🍧🍨🍩🍪🎂🍰🧁🥧🍫🍬🍭🍮🍯🍼🥛☕🫖🍵🍶🍾🍷🍸🍹🍺🍻🥂🥃🫗🥤🧋🧃🧉🧊🥢🍽🍴🥄🔪🫙🏺🌍🌎🌏🌐🗺🗾🧭🏔⛰🌋🗻🏕🏖🏜🏝🏞🏟🏛🏗🧱🪨🪵🛖🏘🏚🏠🏡🏢🏣🏤🏥🏦🏨🏩🏪🏫🏬🏭🏯🏰💒🗼🗽⛪🕌🛕🕍⛩🕋⛲⛺🌁🌃🏙🌄🌅🌆🌇🌉♨🎠🛝🎡🎢💈🎪🚂🚃🚄🚅🚆🚇🚈🚉🚊🚝🚞🚋🚌🚍🚎🚐🚑🚒🚓🚔🚕🚖🚗🚘🚙🛻🚚🚛🚜🏎🏍🛵🦽🦼🛺🚲🛴🛹🛼🚏🛣🛤🛢⛽🛞🚨🚥🚦🛑🚧⚓🛟⛵🛶🚤🛳⛴🛥🚢✈🛩🛫🛬🪂💺🚁🚟🚠🚡🛰🚀🛸🛎🧳⌛⏳⌚⏰⏱⏲🕰🕛🕧🕐🕜🕑🕝🕒🕞🕓🕟🕔🕠🕕🕡🕖🕢🕗🕣🕘🕤🕙🕥🕚🕦🌑🌒🌓🌔🌕🌖🌗🌘🌙🌚🌛🌜🌡☀🌝🌞🪐⭐🌟🌠🌌☁⛅⛈🌤🌥🌦🌧🌨🌩🌪🌫🌬🌀🌈🌂☂☔⛱⚡❄☃⛄☄🔥💧🌊🎃🎄🎆🎇🧨✨🎈🎉🎊🎋🎍🎎🎏🎐🎑🧧🎀🎁🎗🎟🎫🎖🏆🏅🥇🥈🥉⚽⚾🥎🏀🏐🏈🏉🎾🥏🎳🏏🏑🏒🥍🏓🏸🥊🥋🥅⛳⛸🎣🤿🎽🎿🛷🥌🎯🪀🪁🎱🔮🪄🎮🕹🎰🎲🧩🧸🪅🪩🪆♠♥♦♣♟🃏🀄🎴🔫🎭🖼🎨🧵🪡🧶🪢👓🕶🥽🥼🦺👔👕👖🧣🧤🧥🧦👗👘🥻🩱🩲🩳👙👚👛👜👝🛍🎒🩴👞👟🥾🥿👠👡🩰👢👑👒🎩🎓🧢🪖⛑📿💄💍💎🔇🔈🔉🔊📢📣📯🔔🔕🎼🎵🎶🎙🎚🎛🎤🎧📻🎷🪗🎸🎹🎺🎻🪕🥁🪘📱📲☎📞📟📠🔋🪫🔌💻🖥🖨⌨🖱🖲💽💾💿📀🧮🎥🎞📽🎬📺📷📸📹📼🔍🔎🕯💡🔦🏮🪔📔📕📖📗📘📙📚📓📒📃📜📄📰🗞📑🔖🏷💰🪙💴💵💶💷💸💳🧾💹✉📧📨📩📤📥📦📫📪📬📭📮🗳✏✒🖋🖊🖌🖍📝💼📁📂🗂📅📆🗒🗓📇📈📉📊📋📌📍📎🖇📏📐✂🗃🗄🗑🔒🔓🔏🔐🔑🗝💣🔨🪓⛏⚒🛠🗡⚔🪃🏹🛡🪚🔧🪛🔩⚙🗜⚖🦯🔗⛓🪝🧰🧲🪜⚗🧪🧫🧬🔬🔭📡💉🩸💊🩹🩼🩺🩻🚪🛗🪞🪟🛏🛋🪑🚽🪠🚿🛁🪤🪒🧴🧷🧹🧺🧻🪣🧼🫧🪥🧽🧯🛒🧿🪬🚬⚰🪦⚱🗿🪧🪪🏧🚮🚰♿🚹🚺🚻🚼🚾🛂🛃🛄🛅⚠🚸⛔🚫🚳🚭🚯🚱🚷📵🔞☢☣⬆↗➡↘⬇↙⬅↖↕↔↩↪⤴⤵🔃🔄🔙🔚🔛🔜🔝🛐⚛🕉✡☸☯✝☦☪☮🕎🔯♈♉♊♋♌♍♎♏♐♑♒♓⛎🔀🔁🔂▶⏩⏭⏯◀⏪⏮🔼⏫🔽⏬⏸⏹⏺⏏🎦🔅🔆📶📳📴♀♂⚧✖➕➖➗🟰♾‼⁉❓❔❕❗〰💱💲⚕♻⚜🔱📛🔰⭕✅☑✔❌❎➰➿〽✳✴❇©®™🔟🔠🔡🔢🔣🔤🅰🆎🅱🆑🆒🆓ℹ🆔Ⓜ🆕🆖🅾🆗🅿🆘🆙🆚🈁🈂🈷🈶🈯🉐🈹🈚🈲🉑🈸🈴🈳㊗㊙🈺🈵🔴🟠🟡🟢🔵🟣🟤⚫⚪🟥🟧🟨🟩🟦🟪🟫⬛⬜◼◻◾◽▪▫🔶🔷🔸🔹🔺🔻💠🔘🔳🔲🏁🚩🎌🏴🏳"),
            ),
        )

    val liquidKeyboard =
        LiquidKeyboard(
            singleWidth = 60,
            keyHeight = 40,
            marginX = 5f,
            fixedKeyBar = LiquidKeyboard.KeyBar(keys = listOf("liquid_keyboard_exit", "space1", "BackSpace", "Return2", "clipboard_window", "liquid_keyboard_switch")),
            keyboards = keyboards,
        )
}
