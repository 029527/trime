/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.postprocess

/** 句子之间的句号换成什么。 */
enum class PeriodStyle(
    val id: String,
) {
    /** 「。」：识别结果原样，默认。 */
    CHINESE("chinese"),

    /** 「.」，后面还有字时补一个空格。 */
    ENGLISH("english"),

    /** 句号变成一个空格，句末的句号直接去掉。 */
    SPACE("space"),

    /** 句号直接去掉，句子连在一起（只在挨着英文字母数字时留一个空格）。 */
    NONE("none"),
    ;

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: CHINESE
    }
}

/** 标点规则，照 type4me 的 `OutputPunctuationMode`。 */
enum class PunctuationRule(
    val id: String,
) {
    PRESERVE("preserve"),

    /** 去掉末尾的句号（`。` `.`）。 */
    STRIP_PERIODS("strip_periods"),

    /** 去掉末尾的所有标点（问号、感叹号、逗号……），收尾的引号括号留着。 */
    STRIP_TRAILING("strip_trailing"),

    /** 全文只留问号和感叹号。 */
    QUESTIONS_AND_EXCLAMATIONS("questions_exclamations"),

    /** 全文去掉标点。 */
    REMOVE_ALL("remove_all"),
    ;

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: PRESERVE
    }
}

data class PunctuationOptions(
    val periodStyle: PeriodStyle = PeriodStyle.CHINESE,
    val rule: PunctuationRule = PunctuationRule.PRESERVE,
) {
    /** 默认组合：一个字都不改，跟没有这个功能时一样。 */
    val isIdentity: Boolean get() = periodStyle == PeriodStyle.CHINESE && rule == PunctuationRule.PRESERVE
}

/**
 * 语音识别结果的标点整理。纯函数，没有 Android 依赖。
 *
 * 两步，顺序固定：
 * 1. **句号**（[PeriodStyle]）：把句子之间的句号换掉。认的句号是 `。` `．`，以及落在句子边界上的 `.`：
 *    小数（`Qwen3.5`）、版本号、网址邮箱、`e.g.` 这类缩写、省略号 `...` 里的点都不算；
 *    最后一个句号后面没有字了，所以「空格」「不加」时它直接没了，「英文句点」时只换不补空格；
 * 2. **标点规则**（[PunctuationRule]）：在第 1 步的结果上删标点。「去掉全部标点」会连第 1 步换出来的 `.` 一起删，
 *    但换出来的空格留着 —— 想要「句子之间空一格、别的标点都不要」就选「空格」+「去掉全部标点」。
 *
 * 删掉标点后如果把英文字母数字和别的字粘在了一起（`Claude。它` → `Claude它`），补一个空格。
 *
 * 结果只取决于文字本身，跟是不是中间结果无关，所以边说边出的字在定稿时不会跳。
 * 格式化**只能对整段文字做**：末尾的句号在分段上屏时其实是句子之间的句号，见 [TranscriptJoiner]。
 */
object PunctuationFormatter {
    private const val PERIODS = "。．."
    private const val CLOSERS = "”’」』）】》〉〕］｝\"')]}"

    /** 句末会被「去掉句末标点」删的。 */
    private const val TRAILING_PUNCTUATION = "。．.，,、；;：:？?！!…～~·—"

    private const val CJK_PUNCTUATION = "。．，、；：？！…—～·「」『』【】（）《》〈〉〔〕［］｛｝“”‘’"
    private const val ASCII_PUNCTUATION = ".,;:?!\"'()[]{}"
    private const val QUESTION_AND_EXCLAMATION = "?？!！"

    /** `.` 前面是这些词时是缩写，不是句号（小写比较）。 */
    private val ABBREVIATIONS = setOf("mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc", "approx", "no", "fig", "inc", "ltd", "co")

    /** 网址、邮箱：里面的点和冒号斜杠一律不碰。末尾粘着的标点不算网址。 */
    private val PROTECTED = Regex(
        "(?i)(?:https?://|www\\.)[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+|[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)+",
    )

    fun format(
        text: String,
        options: PunctuationOptions,
    ): String {
        if (text.isEmpty() || options.isIdentity) return text
        val periods = applyPeriodStyle(text, options.periodStyle)
        return applyRule(periods, options.rule)
    }

    // MARK: - 句号

    private fun applyPeriodStyle(
        text: String,
        style: PeriodStyle,
    ): String {
        if (style == PeriodStyle.CHINESE) return text
        val protected = protectedMask(text)
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (!isSentencePeriod(text, i, protected)) {
                out.append(text[i])
                i++
                continue
            }
            // 连着的几个句号当一个
            var j = i + 1
            while (j < text.length && text[j] in "。．") j++
            val closersStart = j
            while (j < text.length && text[j] in CLOSERS) j++
            val closers = text.substring(closersStart, j)
            var k = j
            while (k < text.length && isHorizontalSpace(text[k])) k++
            val hadSpace = k > j
            // 同一行后面还有字：句号在句子之间；否则是末尾（或换行前）
            val more = k < text.length && text[k] != '\n' && text[k] != '\r'
            when (style) {
                PeriodStyle.ENGLISH -> {
                    out.append('.').append(closers)
                    if (more) out.append(' ')
                }
                PeriodStyle.SPACE -> {
                    out.append(closers)
                    if (more && endsWithText(out)) out.append(' ')
                }
                PeriodStyle.NONE -> {
                    out.append(closers)
                    if (more && endsWithText(out) && (hadSpace || needsSeam(out.last(), text[k]))) out.append(' ')
                }
                PeriodStyle.CHINESE -> Unit
            }
            i = k
        }
        return out.toString()
    }

    /**
     * [i] 处是不是句号。`。` `．` 永远是；`.` 要前面紧挨着字、后面是结尾/空白/汉字/收尾的引号括号，
     * 并且不在网址里、不是缩写、不是省略号。
     */
    private fun isSentencePeriod(
        text: String,
        i: Int,
        protected: BooleanArray,
    ): Boolean {
        val c = text[i]
        if (c == '。' || c == '．') return true
        if (c != '.' || protected[i]) return false
        val prev = text.getOrNull(i - 1) ?: return false
        val next = text.getOrNull(i + 1)
        if (prev == '.' || next == '.' || prev.isWhitespace()) return false
        // 火山把英文分句直接拼在一起（`world.How`）：小写字母后面跟大写字母也算句子边界
        val sentenceStart = next != null && next in 'A'..'Z' && prev in 'a'..'z'
        if (next != null && !sentenceStart && !(next.isWhitespace() || isCjk(next) || next in CJK_PUNCTUATION || next in CLOSERS)) return false
        // 网址邮箱后面的点：前面那个「词」本身带点，但它不是缩写
        if (protected[i - 1]) return true
        // 前面那个词：从上一个空白或汉字之后算起
        var start = i
        while (start > 0 && !text[start - 1].isWhitespace() && !isCjk(text[start - 1]) && text[start - 1] !in CJK_PUNCTUATION) start--
        val word = text.substring(start, i).trimStart(*CLOSERS.toCharArray(), '(', '[', '{')
        if (word.isEmpty()) return true
        val isNumber = word.all { it.isDigit() || it == '.' || it == ',' }
        if ('.' in word && !isNumber) return false // e.g. / i.e. / U.S.
        if (word.length == 1 && word[0] in 'A'..'Z') return false // 人名缩写 J. K.
        return word.lowercase() !in ABBREVIATIONS
    }

    // MARK: - 标点规则

    private fun applyRule(
        text: String,
        rule: PunctuationRule,
    ): String = when (rule) {
        PunctuationRule.PRESERVE -> text
        PunctuationRule.STRIP_PERIODS -> stripTrailing(text, PERIODS)
        PunctuationRule.STRIP_TRAILING -> stripTrailing(text, TRAILING_PUNCTUATION)
        PunctuationRule.QUESTIONS_AND_EXCLAMATIONS -> removePunctuation(text, keep = QUESTION_AND_EXCLAMATION)
        PunctuationRule.REMOVE_ALL -> removePunctuation(text, keep = "")
    }

    /** 去掉末尾一串 [marks]；收尾的引号括号跳过、保留（`他说“好的。”` → `他说“好的”`）。 */
    private fun stripTrailing(
        text: String,
        marks: String,
    ): String {
        var end = text.length
        while (end > 0 && text[end - 1] in CLOSERS) end--
        var start = end
        while (start > 0 && text[start - 1] in marks) start--
        if (start == end) return text
        return text.substring(0, start) + text.substring(end)
    }

    /**
     * 删掉标点，[keep] 里的留下。英文的点、逗号、冒号、撇号夹在两个字母数字中间时是词的一部分
     * （`3.5` `1,000` `10:30` `don't`），不删；网址邮箱整个不碰。
     */
    private fun removePunctuation(
        text: String,
        keep: String,
    ): String {
        val protected = protectedMask(text)
        val out = StringBuilder(text.length)
        var removedSinceText = false
        for (i in text.indices) {
            val c = text[i]
            if (c !in keep && !protected[i] && isRemovable(text, i)) {
                removedSinceText = true
                continue
            }
            if (removedSinceText && endsWithText(out) && !c.isWhitespace() && needsSeam(out.last(), c)) out.append(' ')
            removedSinceText = false
            out.append(c)
        }
        return out.toString()
    }

    private fun isRemovable(
        text: String,
        i: Int,
    ): Boolean {
        val c = text[i]
        if (c in CJK_PUNCTUATION) return true
        if (c !in ASCII_PUNCTUATION) return false
        if (c in ".,:'") {
            val prev = text.getOrNull(i - 1)
            val next = text.getOrNull(i + 1)
            if (prev != null && next != null && isAsciiAlphanumeric(prev) && isAsciiAlphanumeric(next)) return false
        }
        return true
    }

    // MARK: - 字符

    private fun protectedMask(text: String): BooleanArray {
        val mask = BooleanArray(text.length)
        PROTECTED.findAll(text).forEach { match ->
            // 网址后面紧跟的句末标点不算网址（`访问 www.example.com.`）
            var last = match.range.last
            while (last > match.range.first && text[last] in ".,;:!?)]}'\"") last--
            for (index in match.range.first..last) mask[index] = true
        }
        return mask
    }

    private fun endsWithText(out: StringBuilder) = out.isNotEmpty() && !out.last().isWhitespace()

    /** 两边有一边是英文字母数字，粘在一起就读不通了，要一个空格。 */
    private fun needsSeam(
        left: Char,
        right: Char,
    ) = isAsciiAlphanumeric(left) || isAsciiAlphanumeric(right)

    private fun isHorizontalSpace(c: Char) = c == ' ' || c == '\t' || c == '　'

    private fun isAsciiAlphanumeric(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'

    private fun isCjk(c: Char): Boolean {
        val script = Character.UnicodeScript.of(c.code)
        return script == Character.UnicodeScript.HAN ||
            script == Character.UnicodeScript.HIRAGANA ||
            script == Character.UnicodeScript.KATAKANA ||
            script == Character.UnicodeScript.HANGUL
    }
}

/**
 * 最后一道加工：按 [optionsProvider] 整理标点。每次现取，所以改完设置下一次听写就生效。
 * 中间结果和最终结果一样处理（[isFinal] 不影响结果）。
 */

/**
 * 两次分开的听写之间补的分隔符。
 *
 * 「空格」「不加」「去掉句末句号」这类设置会把上一次听写末尾的句号去掉，下一次听写接着说时
 * 两句就粘在一起了。[tail] 是上一次听写上屏后留在光标前的文字（调用方负责确认光标没动过、
 * 中间没打别的字），返回的分隔符等于「这两句在同一次听写里说出来」时句号会变成的样子。
 *
 * - 默认组合（中文句号、保留标点）一个字都不补，跟没有这个功能时一样；
 * - [tail] 末尾是空白，或是中文标点（`。` `？` `」` ……）：不补；
 * - 末尾是英文标点（`.` `?` `,` ……）：补一个空格；
 * - 末尾是字：按当前设置算出句号的样子（英文句点是 `. `，空格是 ` `，不加是空，挨着英文字母数字时留一个空格）。
 */
fun PunctuationFormatter.separatorAfter(
    tail: String,
    options: PunctuationOptions,
): String {
    if (options.isIdentity) return ""
    val last = tail.lastOrNull() ?: return ""
    if (last.isWhitespace()) return ""
    if (last in ASCII_PUNCTUATION) return " "
    if (!last.isLetterOrDigit()) return ""
    val stub = if (last.code < 0x80) "a" else "字"
    val probe = format(stub + "。字", options)
    if (!probe.startsWith(stub) || !probe.endsWith("字") || probe.length < stub.length + 1) return ""
    return probe.substring(stub.length, probe.length - 1)
}

private const val ASCII_PUNCTUATION = ".?!,;:"

class PunctuationProcessor(
    private val optionsProvider: () -> PunctuationOptions,
) : TranscriptProcessor {
    override val name = "punctuation"

    override fun process(
        text: String,
        isFinal: Boolean,
    ): String = PunctuationFormatter.format(text, optionsProvider())
}

/**
 * 分段上屏时的格式化。
 *
 * 不纠错时，火山每定稿一句就上屏一句。单看这一句，末尾的句号是「句末」（会被去掉），可跟下一句连起来
 * 它其实是句子之间的句号（该换成空格、英文句点……）。已经上屏的字改不了，所以：
 * 每次都把**这次听写的全部原文**整段格式化，减掉已经上屏的那部分，剩下的就是该显示/上屏的。
 * 句号和规则的设计保证「当末尾处理」的结果总是「当中间处理」的前缀（只会少、不会变），
 * 所以接缝处的分隔符会出现在下一段的开头：`今天天气不错` + ` 我们出去走走吧`。
 *
 * 万一对不上（后面的字改变了前面的判断，比如 `Hello.` + `5 apples` 里的点就不是句号了），退回单独格式化这一段，
 * 并去掉开头多余的空白。「对得上」要两条都成立：整段结果以已上屏的文字开头，也以「已上屏原文后面接一句普通的话」
 * 时的样子开头 —— 只看前一条会被 `Hello` + `.5 apples` 这种巧合骗过。
 */
class TranscriptJoiner(
    private val format: (String) -> String,
) {
    private var rawCommitted = ""
    private var shownCommitted = ""

    /** 这次听写第一段上屏前补的分隔符，见 [separatorAfter]；只加在第一段前面，不参与整段格式化。 */
    private var leading = ""

    fun reset(leading: String = "") {
        rawCommitted = ""
        shownCommitted = ""
        this.leading = leading
    }

    private fun withLeading(body: String): String = when {
        rawCommitted.isNotEmpty() || body.isEmpty() || leading.isEmpty() -> body
        leading.last().isWhitespace() -> leading + body.trimStart()
        else -> leading + body
    }

    /** [raw] 是还没上屏的原文（已映射、未整理标点），返回该显示成待定文字的样子。 */
    fun display(raw: String): String = withLeading(body(raw))

    private fun body(raw: String): String {
        val whole = format(rawCommitted + raw)
        if (rawCommitted.isEmpty()) return whole
        val continued = format(rawCommitted + CONTINUATION).removeSuffix(CONTINUATION)
        if (whole.startsWith(shownCommitted) && whole.startsWith(continued)) return whole.substring(shownCommitted.length)
        val alone = format(raw)
        return if (shownCommitted.isEmpty() || shownCommitted.last().isWhitespace()) alone.trimStart() else alone
    }

    /** 上屏 [raw]，返回实际上屏的文字。 */
    fun commit(raw: String): String {
        val body = body(raw)
        val shown = withLeading(body)
        rawCommitted += raw
        shownCommitted += body
        return shown
    }

    private companion object {
        /** 探测用的「下一句」：一个普通汉字，不会被格式化改掉。 */
        const val CONTINUATION = "字"
    }
}
