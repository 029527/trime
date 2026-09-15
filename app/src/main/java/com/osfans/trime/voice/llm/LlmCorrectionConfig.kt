/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.llm

/** 各家关掉「深度思考」的字段不一样；纠错要快，思考只会拖到超时。写法照 type4me 的 `LLMProvider.thinkingDisableField`。 */
enum class ThinkingSwitch {
    /** 不带任何字段。 */
    NONE,

    /** `"thinking": {"type": "disabled"}`，火山方舟、DeepSeek。 */
    THINKING_DISABLED,

    /** `"enable_thinking": false`，阿里云百炼的 Qwen。百炼非流式调用 Qwen3 时必须带。 */
    ENABLE_THINKING_FALSE,
}

/**
 * 预置的 OpenAI 兼容服务。只存地址和默认参数，名字在字符串资源里。
 *
 * [CUSTOM] 的请求体只带 `model` / `messages` / `stream`：自定义服务是什么都有可能，
 * 多带一个它不认的参数（比如新模型不收 `temperature`、`max_tokens`）就会直接 400。
 */
enum class LlmPreset(
    val id: String,
    val baseUrl: String,
    val modelHint: String,
    val thinking: ThinkingSwitch,
    val tuned: Boolean,
) {
    ARK("ark", "https://ark.cn-beijing.volces.com/api/v3", "doubao-seed-1-6-flash-250828", ThinkingSwitch.THINKING_DISABLED, true),
    DEEPSEEK("deepseek", "https://api.deepseek.com", "deepseek-chat", ThinkingSwitch.THINKING_DISABLED, true),
    BAILIAN("bailian", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-flash", ThinkingSwitch.ENABLE_THINKING_FALSE, true),
    CUSTOM("custom", "", "", ThinkingSwitch.NONE, false),
    ;

    companion object {
        fun of(id: String): LlmPreset = entries.firstOrNull { it.id == id } ?: ARK
    }
}

/**
 * 一次纠错要用的全部配置，每次听写开始时从偏好和 [com.osfans.trime.voice.VoiceCredentialStore] 现取。
 *
 * **[apiKey] 是密钥**：[toString] 故意不带它，这个对象进了日志或异常信息也不会漏。
 */
class LlmCorrectionConfig(
    val preset: LlmPreset,
    baseUrl: String,
    model: String,
    val apiKey: String,
    customPrompt: String = "",
    val shortTextThreshold: Int = DEFAULT_SHORT_TEXT_THRESHOLD,
) {
    val baseUrl: String = (if (preset == LlmPreset.CUSTOM) baseUrl else preset.baseUrl).trim().trimEnd('/')
    val model: String = model.trim()

    /** 系统提示词：用户没填就用 [ChatCompletionProtocol.DEFAULT_PROMPT]。 */
    val systemPrompt: String = customPrompt.trim().ifEmpty { ChatCompletionProtocol.DEFAULT_PROMPT }

    /** 缺什么，返回一句给人看的原因；齐了返回 null。 */
    fun missingReason(): String? = when {
        apiKey.isBlank() -> "纠错的 API Key 还没填"
        model.isEmpty() -> "纠错用的模型还没填"
        ChatCompletionProtocol.endpoint(baseUrl) == null -> "纠错服务地址不对（要 https://）"
        else -> null
    }

    val isComplete: Boolean get() = missingReason() == null

    /**
     * 这段文字要不要纠错：去掉空白后不到 [shortTextThreshold] 个字的不纠。
     * 「好的」「嗯」这种短句没什么可改的，等一趟网络不划算。
     */
    fun shouldCorrect(text: String): Boolean = shouldCorrect(text, shortTextThreshold)

    override fun toString(): String = "LlmCorrectionConfig(preset=${preset.id}, baseUrl=$baseUrl, model=$model, apiKey=${if (apiKey.isEmpty()) "<empty>" else "<redacted>"})"

    companion object {
        const val DEFAULT_SHORT_TEXT_THRESHOLD = 4

        /** 设置页滑块的上限。 */
        const val MAX_SHORT_TEXT_THRESHOLD = 20

        fun shouldCorrect(
            text: String,
            threshold: Int,
        ): Boolean {
            val length = text.codePoints().filter { !Character.isWhitespace(it) }.count()
            return length > 0 && length >= threshold
        }
    }
}
