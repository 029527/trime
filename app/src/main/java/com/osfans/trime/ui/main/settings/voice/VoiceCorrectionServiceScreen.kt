/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.voice

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ui.compose.ComposeFragment
import com.osfans.trime.ui.compose.TrimeScreen
import com.osfans.trime.ui.compose.preference.PreferenceCategoryHeader
import com.osfans.trime.ui.compose.preference.PreferenceRow
import com.osfans.trime.ui.compose.preference.SingleChoiceDialog
import com.osfans.trime.ui.compose.preference.TextInputDialog
import com.osfans.trime.util.toast
import com.osfans.trime.voice.VoiceCredentialStore
import com.osfans.trime.voice.llm.ChatCompletionProtocol
import com.osfans.trime.voice.llm.LlmCorrectionException
import com.osfans.trime.voice.llm.LlmCorrectionSettings
import com.osfans.trime.voice.llm.LlmPreset
import com.osfans.trime.voice.llm.OpenAiCompatibleCorrector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class CorrectionDialog {
    PRESET,
    BASE_URL,
    MODEL,
    API_KEY,
    PROMPT,
}

/**
 * 语音输入 › 纠错服务：大模型的服务商、地址、模型、API Key，以及发什么提示词、测试连接。
 * 开关和「少于多少字不纠错」在主页面；这里的行不随开关置灰，没打开也能先配好、先测连接。
 */
@Composable
fun VoiceCorrectionServiceScreen(onNavigateUp: () -> Unit) {
    val context = LocalContext.current
    val prefs = AppPrefs.defaultInstance().voice
    val scope = rememberCoroutineScope()

    var revision by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<CorrectionDialog?>(null) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    @Suppress("UNUSED_EXPRESSION")
    revision

    val presetLabels = llmPresetLabels()
    val unset = stringResource(R.string.voice_credential_unset)
    val testingLabel = stringResource(R.string.voice_llm_test_running)
    val testOkFormat = stringResource(R.string.voice_llm_test_ok)
    val llmConfig = LlmCorrectionSettings.current()
    val llmPrompt = prefs.llmPrompt.getValue()
    val apiKey = VoiceCredentialStore.masked(VoiceCredentialStore.KEY_LLM_API_KEY).ifEmpty { unset }

    TrimeScreen(
        title = stringResource(R.string.voice_correction_service),
        onNavigateUp = onNavigateUp,
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                // 隐私提示说的是「超过下面字数的」，跟着开关和字数阈值留在主页面
                PreferenceCategoryHeader(stringResource(R.string.voice_section_endpoint))
                PreferenceRow(
                    title = stringResource(R.string.voice_llm_provider),
                    summary = presetLabels[llmConfig.preset.ordinal],
                    onClick = { dialog = CorrectionDialog.PRESET },
                )
                PreferenceRow(
                    title = stringResource(R.string.voice_llm_base_url),
                    summary = llmConfig.baseUrl.ifEmpty { unset },
                    // 预置服务的地址固定，只显示
                    enabled = llmConfig.preset == LlmPreset.CUSTOM,
                    onClick = { dialog = CorrectionDialog.BASE_URL },
                )
                PreferenceRow(
                    title = stringResource(R.string.voice_llm_model),
                    summary = llmConfig.model.ifEmpty { unset },
                    onClick = { dialog = CorrectionDialog.MODEL },
                )
                PreferenceRow(
                    title = stringResource(R.string.voice_llm_api_key),
                    summary = apiKey,
                    onClick = { dialog = CorrectionDialog.API_KEY },
                )

                PreferenceCategoryHeader(stringResource(R.string.voice_section_request))
                PreferenceRow(
                    title = stringResource(R.string.voice_llm_prompt),
                    summary = llmPrompt.lineSequence().firstOrNull()?.take(PROMPT_SUMMARY_LENGTH)?.ifEmpty { null }
                        ?: stringResource(R.string.voice_llm_prompt_default),
                    onClick = { dialog = CorrectionDialog.PROMPT },
                )
                PreferenceRow(
                    title = stringResource(R.string.voice_llm_test),
                    summary = testResult ?: stringResource(R.string.voice_llm_test_summary),
                    enabled = !testing,
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
            }
        }
    }

    val closeDialog = {
        revision++
        dialog = null
    }

    when (dialog) {
        CorrectionDialog.PRESET -> SingleChoiceDialog(
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
        CorrectionDialog.BASE_URL -> TextInputDialog(
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
        CorrectionDialog.MODEL -> TextInputDialog(
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
        CorrectionDialog.API_KEY -> CredentialDialog(
            title = stringResource(R.string.voice_llm_api_key),
            storeKey = VoiceCredentialStore.KEY_LLM_API_KEY,
            message = stringResource(R.string.voice_llm_api_key_note),
            onDone = {
                testResult = null
                closeDialog()
            },
        )
        CorrectionDialog.PROMPT -> TextInputDialog(
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

class VoiceCorrectionServiceFragment : ComposeFragment() {
    @Composable
    override fun Content() {
        VoiceCorrectionServiceScreen(onNavigateUp = ::navigateUp)
    }
}

private const val PROMPT_SUMMARY_LENGTH = 40
