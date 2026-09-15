/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.voice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ui.compose.TrimeScreen
import com.osfans.trime.ui.compose.preference.PreferenceCategoryHeader
import com.osfans.trime.ui.compose.preference.PreferenceDelegateRow
import com.osfans.trime.ui.compose.preference.PreferenceRow
import com.osfans.trime.ui.compose.preference.SingleChoiceDialog
import com.osfans.trime.ui.compose.preference.SliderPreferenceItem
import com.osfans.trime.ui.compose.preference.SwitchPreferenceItem
import com.osfans.trime.ui.compose.preference.TextInputDialog
import com.osfans.trime.util.toast
import com.osfans.trime.voice.VoiceCredentialStore
import com.osfans.trime.voice.audio.VoiceAudioSource
import com.osfans.trime.voice.llm.ChatCompletionProtocol
import com.osfans.trime.voice.llm.LlmCorrectionConfig
import com.osfans.trime.voice.llm.LlmCorrectionException
import com.osfans.trime.voice.llm.LlmCorrectionSettings
import com.osfans.trime.voice.llm.LlmPreset
import com.osfans.trime.voice.llm.OpenAiCompatibleCorrector
import com.osfans.trime.voice.vocab.VoiceVocabularyStore
import com.osfans.trime.voice.volc.VolcConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class VoiceDialog {
    AUTH_MODE,
    RESOURCE,
    API_KEY,
    APP_KEY,
    ACCESS_KEY,
    BOOSTING_TABLE,
    LLM_PRESET,
    LLM_BASE_URL,
    LLM_MODEL,
    LLM_API_KEY,
    LLM_PROMPT,
}

/**
 * 语音输入设置页。手写页面（不走 preference 渲染器），因为火山凭证和纠错的 API Key 不能存在
 * 普通的 SharedPreferences 里 —— 它们走 [VoiceCredentialStore]
 * （EncryptedSharedPreferences），跟 `PreferenceDelegate` 的存取模型对不上。
 *
 * 没有凭证时验证听写界面用的「模拟识别」「模拟纠错」在「开发者」页，只有 debug 包有。
 */
@Composable
fun VoiceInputScreen(
    onNavigateUp: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = AppPrefs.defaultInstance().voice
    val scope = rememberCoroutineScope()

    // 这些值不是 Compose 状态，改完要手动 bump 一下让整页重算
    var revision by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<VoiceDialog?>(null) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    @Suppress("UNUSED_EXPRESSION")
    revision

    val enabled = prefs.enabled.getValue()
    val authMode = prefs.authMode.getValue()
    val resourceId = prefs.resourceId.getValue()

    val authValues = listOf(VolcConfig.AUTH_MODE_API_KEY, VolcConfig.AUTH_MODE_LEGACY)
    val authLabels = listOf(
        stringResource(R.string.voice_auth_api_key),
        stringResource(R.string.voice_auth_legacy),
    )

    val resourceLabels = listOf(
        stringResource(R.string.voice_resource_seed),
        stringResource(R.string.voice_resource_big),
    )

    // 顺序跟 LlmPreset.entries 一致
    val presetLabels = listOf(
        stringResource(R.string.voice_llm_provider_ark),
        stringResource(R.string.voice_llm_provider_deepseek),
        stringResource(R.string.voice_llm_provider_bailian),
        stringResource(R.string.voice_llm_provider_custom),
    )

    val unset = stringResource(R.string.voice_credential_unset)
    val testingLabel = stringResource(R.string.voice_llm_test_running)
    val testOkFormat = stringResource(R.string.voice_llm_test_ok)

    val llmEnabled = prefs.llmEnabled.getValue()
    val llmActive = enabled && llmEnabled
    val llmConfig = LlmCorrectionSettings.current()
    val llmPrompt = prefs.llmPrompt.getValue()

    TrimeScreen(
        title = stringResource(R.string.voice_input),
        onNavigateUp = onNavigateUp,
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                SwitchPreferenceItem(
                    title = stringResource(R.string.voice_enabled),
                    summary = stringResource(R.string.voice_enabled_summary),
                    checked = enabled,
                    onCheckedChange = {
                        prefs.enabled.setValue(it)
                        revision++
                    },
                )
                PreferenceRow(
                    title = stringResource(R.string.voice_provider),
                    summary = stringResource(R.string.voice_provider_volcano),
                    enabled = enabled,
                )
                PreferenceRow(
                    title = stringResource(R.string.voice_auto_stop),
                    summary = stringResource(R.string.voice_auto_stop_summary),
                    enabled = enabled,
                )
                PreferenceRow(
                    title = stringResource(R.string.voice_permission),
                    summary = if (VoiceAudioSource.hasPermission()) {
                        stringResource(R.string.voice_permission_granted)
                    } else {
                        stringResource(R.string.voice_permission_missing)
                    },
                    onClick = {
                        onRequestPermission()
                        revision++
                    },
                )

                PreferenceCategoryHeader(stringResource(R.string.voice_credentials))
                NoteText(stringResource(R.string.voice_credentials_note))
                PreferenceRow(
                    title = stringResource(R.string.voice_auth_mode),
                    summary = authLabels.getOrNull(authValues.indexOf(authMode)) ?: authLabels.first(),
                    enabled = enabled,
                    onClick = { dialog = VoiceDialog.AUTH_MODE },
                )
                if (authMode == VolcConfig.AUTH_MODE_LEGACY) {
                    PreferenceRow(
                        title = stringResource(R.string.voice_app_key),
                        summary = VoiceCredentialStore.masked(VoiceCredentialStore.KEY_APP_KEY).ifEmpty { unset },
                        enabled = enabled,
                        onClick = { dialog = VoiceDialog.APP_KEY },
                    )
                    PreferenceRow(
                        title = stringResource(R.string.voice_access_key),
                        summary = VoiceCredentialStore.masked(VoiceCredentialStore.KEY_ACCESS_KEY).ifEmpty { unset },
                        enabled = enabled,
                        onClick = { dialog = VoiceDialog.ACCESS_KEY },
                    )
                } else {
                    PreferenceRow(
                        title = stringResource(R.string.voice_api_key),
                        summary = VoiceCredentialStore.masked(VoiceCredentialStore.KEY_API_KEY).ifEmpty { unset },
                        enabled = enabled,
                        onClick = { dialog = VoiceDialog.API_KEY },
                    )
                }
                PreferenceRow(
                    title = stringResource(R.string.voice_resource_id),
                    summary = resourceLabels.getOrNull(VolcConfig.RESOURCE_IDS.indexOf(resourceId)) ?: resourceLabels.first(),
                    enabled = enabled,
                    onClick = { dialog = VoiceDialog.RESOURCE },
                )
                PreferenceRow(
                    title = stringResource(R.string.voice_clear_credentials),
                    onClick = {
                        VoiceCredentialStore.clearAll()
                        revision++
                    },
                )

                PreferenceCategoryHeader(stringResource(R.string.voice_recording))
                val maxDuration = prefs.maxDuration.getValue()
                SliderPreferenceItem(
                    title = stringResource(R.string.voice_max_duration),
                    value = maxDuration,
                    min = 10,
                    max = 300,
                    step = 5,
                    valueLabel = "$maxDuration s",
                    unit = "s",
                    defaultValue = 60,
                    enabled = enabled,
                    onValueChangeFinished = {
                        prefs.maxDuration.setValue(it)
                        revision++
                    },
                )

                PreferenceCategoryHeader(stringResource(R.string.voice_llm))
                NoteText(stringResource(R.string.voice_llm_privacy))
                SwitchPreferenceItem(
                    title = stringResource(R.string.voice_llm_enabled),
                    summary = stringResource(R.string.voice_llm_enabled_summary),
                    checked = llmEnabled,
                    enabled = enabled,
                    onCheckedChange = {
                        prefs.llmEnabled.setValue(it)
                        revision++
                    },
                )
                PreferenceRow(
                    title = stringResource(R.string.voice_llm_provider),
                    summary = presetLabels[llmConfig.preset.ordinal],
                    enabled = llmActive,
                    onClick = { dialog = VoiceDialog.LLM_PRESET },
                )
                PreferenceRow(
                    title = stringResource(R.string.voice_llm_base_url),
                    summary = llmConfig.baseUrl.ifEmpty { unset },
                    // 预置服务的地址固定，只显示
                    enabled = llmActive && llmConfig.preset == LlmPreset.CUSTOM,
                    onClick = { dialog = VoiceDialog.LLM_BASE_URL },
                )
                PreferenceRow(
                    title = stringResource(R.string.voice_llm_model),
                    summary = llmConfig.model.ifEmpty { unset },
                    enabled = llmActive,
                    onClick = { dialog = VoiceDialog.LLM_MODEL },
                )
                PreferenceRow(
                    title = stringResource(R.string.voice_llm_api_key),
                    summary = VoiceCredentialStore.masked(VoiceCredentialStore.KEY_LLM_API_KEY).ifEmpty { unset },
                    enabled = llmActive,
                    onClick = { dialog = VoiceDialog.LLM_API_KEY },
                )
                PreferenceRow(
                    title = stringResource(R.string.voice_llm_prompt),
                    summary = llmPrompt.lineSequence().firstOrNull()?.take(PROMPT_SUMMARY_LENGTH)?.ifEmpty { null }
                        ?: stringResource(R.string.voice_llm_prompt_default),
                    enabled = llmActive,
                    onClick = { dialog = VoiceDialog.LLM_PROMPT },
                )
                val threshold = llmConfig.shortTextThreshold
                SliderPreferenceItem(
                    title = stringResource(R.string.voice_llm_short_text),
                    value = threshold,
                    min = 0,
                    max = LlmCorrectionConfig.MAX_SHORT_TEXT_THRESHOLD,
                    step = 1,
                    valueLabel = "$threshold",
                    defaultValue = LlmCorrectionConfig.DEFAULT_SHORT_TEXT_THRESHOLD,
                    enabled = llmActive,
                    onValueChangeFinished = {
                        prefs.llmShortTextThreshold.setValue(it)
                        revision++
                    },
                )
                PreferenceRow(
                    title = stringResource(R.string.voice_llm_test),
                    summary = testResult ?: stringResource(R.string.voice_llm_test_summary),
                    enabled = llmActive && !testing,
                    onClick = {
                        // 只有点这一下才真的发请求
                        testing = true
                        testResult = testingLabel
                        scope.launch {
                            val result = try {
                                val ms = OpenAiCompatibleCorrector.testConnection(LlmCorrectionSettings.current())
                                String.format(testOkFormat, ms)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: LlmCorrectionException) {
                                listOfNotNull(e.message, e.detail).joinToString("\n")
                            } catch (e: Exception) {
                                e.javaClass.simpleName
                            }
                            testResult = result
                            testing = false
                            context.toast(result)
                        }
                    },
                )

                // 按 VOICE_ASSIST 键（不是内置听写的麦克风键）时切到哪个系统语音输入法
                PreferenceCategoryHeader(stringResource(R.string.voice_system_ime))
                PreferenceDelegateRow(AppPrefs.defaultInstance().general, AppPrefs.General.PREFERRED_VOICE_INPUT)

                // 词库文件是数据，但云端热词表 ID 跟着识别走，整段留在这里
                PreferenceCategoryHeader(stringResource(R.string.voice_vocabulary))
                Column {
                    PreferenceRow(
                        title = stringResource(R.string.voice_vocabulary_path),
                        summary = VoiceVocabularyStore.file.absolutePath,
                    )
                    PreferenceRow(
                        title = stringResource(R.string.voice_vocabulary_reload),
                        summary = VoiceVocabularyStore.lastLoadSummary.ifEmpty { null },
                        onClick = {
                            VoiceVocabularyStore.reload()
                            context.toast(VoiceVocabularyStore.lastLoadSummary)
                            revision++
                        },
                    )
                    PreferenceRow(
                        title = stringResource(R.string.voice_boosting_table_id),
                        summary = prefs.boostingTableId.getValue().ifEmpty {
                            stringResource(R.string.voice_boosting_table_id_unset)
                        },
                        enabled = enabled,
                        onClick = { dialog = VoiceDialog.BOOSTING_TABLE },
                    )
                    PreferenceRow(
                        title = stringResource(R.string.voice_vocabulary_create),
                        onClick = {
                            val created = VoiceVocabularyStore.createTemplateIfAbsent()
                            context.toast(
                                if (created) R.string.voice_vocabulary_created else R.string.voice_vocabulary_exists,
                            )
                            revision++
                        },
                    )
                }
            }
        }
    }

    val closeDialog = {
        revision++
        dialog = null
    }

    when (dialog) {
        VoiceDialog.AUTH_MODE -> SingleChoiceDialog(
            title = stringResource(R.string.voice_auth_mode),
            entries = authLabels,
            selectedIndex = authValues.indexOf(authMode).coerceAtLeast(0),
            onSelect = {
                prefs.authMode.setValue(authValues[it])
                closeDialog()
            },
            onDismiss = { dialog = null },
        )
        VoiceDialog.RESOURCE -> SingleChoiceDialog(
            title = stringResource(R.string.voice_resource_id),
            entries = resourceLabels,
            selectedIndex = VolcConfig.RESOURCE_IDS.indexOf(resourceId).coerceAtLeast(0),
            onSelect = {
                prefs.resourceId.setValue(VolcConfig.RESOURCE_IDS[it])
                closeDialog()
            },
            onDismiss = { dialog = null },
        )
        VoiceDialog.API_KEY -> CredentialDialog(
            title = stringResource(R.string.voice_api_key),
            storeKey = VoiceCredentialStore.KEY_API_KEY,
            onDone = closeDialog,
        )
        VoiceDialog.APP_KEY -> CredentialDialog(
            title = stringResource(R.string.voice_app_key),
            storeKey = VoiceCredentialStore.KEY_APP_KEY,
            onDone = closeDialog,
        )
        VoiceDialog.ACCESS_KEY -> CredentialDialog(
            title = stringResource(R.string.voice_access_key),
            storeKey = VoiceCredentialStore.KEY_ACCESS_KEY,
            onDone = closeDialog,
        )
        VoiceDialog.BOOSTING_TABLE -> TextInputDialog(
            title = stringResource(R.string.voice_boosting_table_id),
            initialValue = prefs.boostingTableId.getValue(),
            message = stringResource(R.string.voice_boosting_table_id_note),
            onConfirm = {
                prefs.boostingTableId.setValue(it.trim())
                closeDialog()
            },
            onDismiss = { dialog = null },
        )
        VoiceDialog.LLM_PRESET -> SingleChoiceDialog(
            title = stringResource(R.string.voice_llm_provider),
            entries = presetLabels,
            selectedIndex = llmConfig.preset.ordinal,
            onSelect = {
                prefs.llmPreset.setValue(LlmPreset.entries[it].id)
                testResult = null
                closeDialog()
            },
            onDismiss = { dialog = null },
        )
        VoiceDialog.LLM_BASE_URL -> TextInputDialog(
            title = stringResource(R.string.voice_llm_base_url),
            initialValue = prefs.llmBaseUrl.getValue(),
            message = stringResource(R.string.voice_llm_base_url_note),
            onConfirm = {
                prefs.llmBaseUrl.setValue(it.trim())
                testResult = null
                closeDialog()
            },
            onDismiss = { dialog = null },
        )
        VoiceDialog.LLM_MODEL -> TextInputDialog(
            title = stringResource(R.string.voice_llm_model),
            initialValue = prefs.llmModel.getValue(),
            message = if (llmConfig.preset.modelHint.isEmpty()) {
                stringResource(R.string.voice_llm_model_note_custom)
            } else {
                stringResource(R.string.voice_llm_model_note, llmConfig.preset.modelHint)
            },
            onConfirm = {
                prefs.llmModel.setValue(it.trim())
                testResult = null
                closeDialog()
            },
            onDismiss = { dialog = null },
        )
        VoiceDialog.LLM_API_KEY -> CredentialDialog(
            title = stringResource(R.string.voice_llm_api_key),
            storeKey = VoiceCredentialStore.KEY_LLM_API_KEY,
            message = stringResource(R.string.voice_llm_api_key_note),
            onDone = {
                testResult = null
                closeDialog()
            },
        )
        VoiceDialog.LLM_PROMPT -> TextInputDialog(
            title = stringResource(R.string.voice_llm_prompt),
            // 没自定义过就把内置的放进去，改起来有个起点
            initialValue = llmPrompt.ifEmpty { ChatCompletionProtocol.DEFAULT_PROMPT },
            message = stringResource(R.string.voice_llm_prompt_note),
            singleLine = false,
            onConfirm = {
                val value = it.trim()
                prefs.llmPrompt.setValue(if (value == ChatCompletionProtocol.DEFAULT_PROMPT) "" else value)
                closeDialog()
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun NoteText(text: String) {
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
private fun CredentialDialog(
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

private const val PROMPT_SUMMARY_LENGTH = 40
