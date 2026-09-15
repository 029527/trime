/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.vocab

import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.boolean
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.sequence
import com.osfans.trime.util.yaml.string

/**
 * 一条映射规则：把识别引擎听成的 [from] 换成 [to]。
 *
 * @param caseSensitive 默认 false，即 `queen 3.5` 和 `Queen 3.5` 都能命中。
 * @param wholeWord 默认 true。只在 [from] 的首/尾字符是 ASCII 字母数字时才检查那一侧的边界，
 *   免得 `in` 把 `point` 里的 in 也换掉；中文词条不受影响（中文没有词边界）。
 */
data class VoiceMappingRule(
    val from: String,
    val to: String,
    val caseSensitive: Boolean = false,
    val wholeWord: Boolean = true,
)

/**
 * 语音输入词库。两类内容：
 *
 * - [hotwords]：热词。开始听写时跟映射词的目标词合在一起传给识别引擎做偏置，见 [recognitionHotwords]。
 * - [rules]：映射词。识别结果上屏前做替换。
 *
 * 组织方式参考 type4me 的 `Services/HotwordStorage.swift`（那边也是热词和映射词两套）。
 * 文件放在 Rime 用户目录，跟着用户的配置仓库走 git 同步，电脑上能批量编辑。
 */
data class VoiceVocabulary(
    val hotwords: List<String> = emptyList(),
    val rules: List<VoiceMappingRule> = emptyList(),
) {

    /** 最长匹配优先，所以先排好序，避免每次替换都重排。 */
    private val sortedRules: List<VoiceMappingRule> by lazy {
        rules.filter { it.from.isNotEmpty() }.sortedByDescending { it.from.length }
    }

    private val loweredFrom: List<String> by lazy {
        sortedRules.map { it.from.lowercase() }
    }

    val isEmpty: Boolean get() = hotwords.isEmpty() && rules.isEmpty()

    /**
     * 传给识别引擎的热词：先 [hotwords]，再映射规则的目标词 —— [VoiceMappingRule.to]
     * 正是用户想让引擎直接认对的写法，没必要让人在两处各写一遍。
     *
     * 合并规则见 [mergeHotwords]。LLM 纠错也拿这份当参考词表。
     */
    val recognitionHotwords: List<String> by lazy {
        mergeHotwords(hotwords, rules.map { it.to })
    }

    /**
     * 从左到右扫一遍，每个位置试所有规则（长的先试）。命中就把 [VoiceMappingRule.to]
     * 写进结果，并在**原文**里跳过整段命中区间 —— 替换出来的文本不会被再扫一次，
     * 所以既不会重复替换，也不会因为 a→b、b→a 互相触发而死循环。
     */
    fun replace(text: String): String {
        if (text.isEmpty() || sortedRules.isEmpty()) return text
        val lowered = text.lowercase()
        val out = StringBuilder(text.length)
        var i = 0
        outer@ while (i < text.length) {
            for ((index, rule) in sortedRules.withIndex()) {
                val length = rule.from.length
                if (i + length > text.length) continue
                val hit = if (rule.caseSensitive) {
                    text.regionMatches(i, rule.from, 0, length)
                } else {
                    // 整串一次性 lowercase 过，比逐段 regionMatches(ignoreCase) 稳：
                    // 大小写折叠后长度不变的字符才会命中，长度会变的（比如 ẞ）不会误判位置。
                    lowered.length == text.length && lowered.regionMatches(i, loweredFrom[index], 0, length)
                }
                if (!hit) continue
                if (rule.wholeWord && !boundaryOk(text, i, length, rule.from)) continue
                out.append(rule.to)
                i += length
                continue@outer
            }
            out.append(text[i])
            i++
        }
        return out.toString()
    }

    private fun boundaryOk(
        text: String,
        start: Int,
        length: Int,
        from: String,
    ): Boolean {
        if (from.first().isAsciiWord() && start > 0 && text[start - 1].isAsciiWord()) return false
        val end = start + length
        if (from.last().isAsciiWord() && end < text.length && text[end].isAsciiWord()) return false
        return true
    }

    private fun Char.isAsciiWord(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '_'

    companion object {
        val EMPTY = VoiceVocabulary()

        const val FILE_NAME = "voice_vocab.yaml"

        /**
         * 一次请求最多带多少个热词。火山文档没写死内联热词的上限，这里取个保守值：
         * 热词太多偏置会互相稀释，识别反而变差；真要成百上千个词请用云端热词表。
         */
        const val MAX_RECOGNITION_HOTWORDS = 100

        /** 单个热词最多多少字（按码点算）。长句子不是「词」，塞进去只会干扰识别。 */
        const val MAX_HOTWORD_LENGTH = 20

        /**
         * 按顺序合并几份热词：去掉首尾空白，跳过空的、没有字母数字的（比如映射成标点的规则）
         * 和超过 [MAX_HOTWORD_LENGTH] 的；大小写不敏感去重，保留先出现的写法；
         * 最后截到 [MAX_RECOGNITION_HOTWORDS] 个。前面的来源优先占名额。
         */
        fun mergeHotwords(vararg sources: List<String>): List<String> {
            val seen = HashSet<String>()
            val merged = ArrayList<String>()
            for (source in sources) {
                for (raw in source) {
                    if (merged.size >= MAX_RECOGNITION_HOTWORDS) return merged
                    val word = raw.trim()
                    if (word.isEmpty() || word.none { it.isLetterOrDigit() }) continue
                    if (word.codePointCount(0, word.length) > MAX_HOTWORD_LENGTH) continue
                    if (seen.add(word.lowercase())) merged += word
                }
            }
            return merged
        }

        /**
         * 解析词库 yaml。解析不了（写错了）就返回 [EMPTY]，功能照常工作，
         * 只是不做替换 —— 词库坏了不该把语音输入本身弄挂。
         */
        fun parse(source: String): VoiceVocabulary {
            if (source.isBlank()) return EMPTY
            val root = runCatching { Yaml.parseToYamlNode(source).mapping }.getOrNull() ?: return EMPTY

            val hotwords = root["hotwords"]?.sequence
                ?.mapNotNull { it.string?.trim() }
                ?.filter { it.isNotEmpty() }
                ?.distinct()
                ?: emptyList()

            val rules = mutableListOf<VoiceMappingRule>()

            // 紧凑写法： mappings: { 听成的样子: 要的样子 }
            root["mappings"]?.mapping?.forEach { (key, value) ->
                val from = key.string?.trim().orEmpty()
                val to = value.string ?: return@forEach
                if (from.isNotEmpty()) rules += VoiceMappingRule(from, to)
            }

            // 展开写法： rules: [{ to: X, from: [a, b], case_sensitive: true }]
            root["rules"]?.sequence?.forEach { node ->
                val item = node.mapping ?: return@forEach
                val to = item["to"]?.string ?: return@forEach
                val caseSensitive = item["case_sensitive"]?.boolean ?: false
                val wholeWord = item["whole_word"]?.boolean ?: true
                val sources = item["from"]?.let { fromNode ->
                    fromNode.sequence?.mapNotNull { it.string } ?: listOfNotNull(fromNode.string)
                } ?: emptyList()
                sources.map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                    rules += VoiceMappingRule(it, to, caseSensitive, wholeWord)
                }
            }

            return VoiceVocabulary(hotwords, rules.distinct())
        }

        /** 第一次用的人拿这个当模板；[VoiceVocabularyStore] 会在文件不存在时写出去。 */
        val TEMPLATE: String = """
            # 语音输入词库
            #
            # 这个文件在 Rime 用户目录里，会跟着配置仓库一起 git 同步，
            # 所以可以在电脑上批量编辑。**不要把任何密钥写进这里。**
            #
            # 改完在「设置 → 语音输入 → 重新加载词库」点一下，或者直接开始下一次录音，
            # 都会重新读一遍。

            # 热词：开始听写时传给识别引擎，让这些词更容易被认对。
            # 下面映射词的目标写法（右边 / to）也会一起传过去，不用在这里再写一遍。
            # 合在一起最多传 100 个，超过 20 个字的词条会跳过；
            # 设置里填了「云端热词表 ID」时改用云端的表，这里的热词就不传了。
            hotwords:
              - Trime
              - 小鹤双拼

            # 映射词：识别结果上屏前替换。左边是听成的样子，右边是想要的样子。
            # 默认大小写不敏感、最长的词条优先命中。
            mappings:
              Queen 3.5: Qwen3.5
              克劳德: Claude

            # 一个目标词有好几种听错的样子时用这种写法。
            rules:
              - to: Claude Code
                from:
                  - 克劳德 code
                  - 克劳德扣的
              # case_sensitive: true 表示区分大小写；
              # whole_word: false 表示允许在词内部命中（默认 true，只在词条两端是
              # ASCII 字母数字时才检查那一侧的边界）。
              - to: iOS
                from: [ios]
                case_sensitive: true
        """.trimIndent() + "\n"
    }
}
