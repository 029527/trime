/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.llm

import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.voice.VoiceCredentialStore

/** 从偏好和 [VoiceCredentialStore] 拼出当前的纠错配置。听写开始时和设置页「测试连接」各取一次。 */
object LlmCorrectionSettings {
    fun current(): LlmCorrectionConfig {
        val prefs = AppPrefs.defaultInstance().voice
        return LlmCorrectionConfig(
            preset = LlmPreset.of(prefs.llmPreset.getValue()),
            baseUrl = prefs.llmBaseUrl.getValue(),
            model = prefs.llmModel.getValue(),
            apiKey = VoiceCredentialStore.get(VoiceCredentialStore.KEY_LLM_API_KEY),
            customPrompt = prefs.llmPrompt.getValue(),
            shortTextThreshold = prefs.llmShortTextThreshold.getValue().coerceIn(0, LlmCorrectionConfig.MAX_SHORT_TEXT_THRESHOLD),
        )
    }
}
