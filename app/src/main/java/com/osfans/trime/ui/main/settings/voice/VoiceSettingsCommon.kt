/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.voice

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ui.compose.preference.TextInputDialog
import com.osfans.trime.voice.VoiceCredentialStore
import com.osfans.trime.voice.llm.LlmCorrectionSettings
import com.osfans.trime.voice.volc.VolcConfig

/** 鉴权方式的取值，顺序跟 [authModeLabels] 一致。 */
internal val AuthModeValues = listOf(VolcConfig.AUTH_MODE_API_KEY, VolcConfig.AUTH_MODE_LEGACY)

@Composable
internal fun authModeLabels() = listOf(
    stringResource(R.string.voice_auth_api_key),
    stringResource(R.string.voice_auth_legacy),
)

/** 顺序跟 [VolcConfig.RESOURCE_IDS] 一致。 */
@Composable
internal fun resourceLabels() = listOf(
    stringResource(R.string.voice_resource_seed),
    stringResource(R.string.voice_resource_big),
)

/** 顺序跟 `LlmPreset.entries` 一致。 */
@Composable
internal fun llmPresetLabels() = listOf(
    stringResource(R.string.voice_llm_provider_ark),
    stringResource(R.string.voice_llm_provider_deepseek),
    stringResource(R.string.voice_llm_provider_bailian),
    stringResource(R.string.voice_llm_provider_custom),
)

/**
 * 「识别服务」一行的摘要，比如「API Key 已配置 · 流式识别 2.0」。
 * **只说配没配**：密钥只拿来判断是否齐全，不进摘要，打码后的样子也不放。
 */
@Composable
internal fun recognitionServiceSummary(): String {
    val prefs = AppPrefs.defaultInstance().voice
    val authMode = prefs.authMode.getValue()
    val configured = VolcConfig.authenticationOf(
        authMode = authMode,
        apiKey = VoiceCredentialStore.get(VoiceCredentialStore.KEY_API_KEY),
        appKey = VoiceCredentialStore.get(VoiceCredentialStore.KEY_APP_KEY),
        accessKey = VoiceCredentialStore.get(VoiceCredentialStore.KEY_ACCESS_KEY),
    ) != null
    val credential = if (authMode == VolcConfig.AUTH_MODE_LEGACY) {
        stringResource(R.string.voice_auth_legacy_short)
    } else {
        stringResource(R.string.voice_api_key)
    }
    val state = stringResource(
        if (configured) R.string.voice_credentials_configured else R.string.voice_credentials_missing,
        credential,
    )
    val models = listOf(
        stringResource(R.string.voice_resource_seed_short),
        stringResource(R.string.voice_resource_big_short),
    )
    val model = models[VolcConfig.RESOURCE_IDS.indexOf(prefs.resourceId.getValue()).coerceAtLeast(0)]
    return "$state · $model"
}

/** 「纠错服务」一行的摘要：配齐了是「DeepSeek · deepseek-chat」，否则「DeepSeek · 未配置」。 */
@Composable
internal fun correctionServiceSummary(): String {
    val config = LlmCorrectionSettings.current()
    val preset = llmPresetLabels()[config.preset.ordinal]
    return if (config.isComplete) "$preset · ${config.model}" else stringResource(R.string.voice_correction_unconfigured, preset)
}

@Composable
internal fun NoteText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/**
 * 凭证输入框。**故意不回显已存的值**（输入框从空开始），输入时也打码：
 * 密钥没有必要摊在屏幕上，留空确认就是清掉这一项。
 */
@Composable
internal fun CredentialDialog(
    title: String,
    storeKey: String,
    onDone: () -> Unit,
    message: String = stringResource(R.string.voice_credentials_note),
) {
    TextInputDialog(
        title = title,
        initialValue = "",
        message = message,
        password = true,
        onConfirm = {
            VoiceCredentialStore.put(storeKey, it)
            onDone()
        },
        onDismiss = onDone,
    )
}
