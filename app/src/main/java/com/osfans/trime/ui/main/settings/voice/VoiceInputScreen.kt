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
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ui.compose.TrimeScreen
import com.osfans.trime.ui.compose.preference.PreferenceCategoryHeader
import com.osfans.trime.ui.compose.preference.PreferenceDelegateRow
import com.osfans.trime.ui.compose.preference.PreferenceRow
import com.osfans.trime.ui.compose.preference.SingleChoiceDialog
import com.osfans.trime.ui.compose.preference.SliderPreferenceItem
import com.osfans.trime.ui.compose.preference.SwitchPreferenceItem
import com.osfans.trime.util.toast
import com.osfans.trime.voice.audio.VoiceAudioSource
import com.osfans.trime.voice.llm.LlmCorrectionConfig
import com.osfans.trime.voice.llm.LlmCorrectionSettings
import com.osfans.trime.voice.postprocess.PeriodStyle
import com.osfans.trime.voice.postprocess.PunctuationFormatter
import com.osfans.trime.voice.postprocess.PunctuationOptions
import com.osfans.trime.voice.postprocess.PunctuationRule
import com.osfans.trime.voice.vocab.VoiceVocabularyStore

private enum class VoiceDialog {
    PERIOD_STYLE,
    PUNCTUATION_RULE,
}

/**
 * 语音输入设置页（手写，不走 preference 渲染器）。
 *
 * 这一页只放「听写怎么工作」，顺序固定：总开关 → 听写 → 标点 → 大模型纠错（开关和字数阈值）
 * → 服务 → 识别词库（文件）。要填账号、密钥、地址、模型的都在「服务」下面的两个子页面：
 * [VoiceRecognitionServiceScreen]（火山）和 [VoiceCorrectionServiceScreen]（大模型），
 * 这里各留一行不带密钥的状态摘要。整理原则见 `docs/modern-ui-notes.md` §5。
 *
 * 密钥走 [com.osfans.trime.voice.VoiceCredentialStore]（EncryptedSharedPreferences），
 * 跟 `PreferenceDelegate` 的存取模型对不上，所以这几页都是手写的。
 * 没有凭证时验证听写界面用的「模拟识别」「模拟纠错」在「开发者」页，只有 debug 包有。
 */
@Composable
fun VoiceInputScreen(
    onNavigateUp: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenRecognitionService: () -> Unit,
    onOpenCorrectionService: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = AppPrefs.defaultInstance().voice

    // 这些值不是 Compose 状态，改完要手动 bump 一下让整页重算
    var revision by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<VoiceDialog?>(null) }

    @Suppress("UNUSED_EXPRESSION")
    revision

    val enabled = prefs.enabled.getValue()
    val llmEnabled = prefs.llmEnabled.getValue()
    val llmActive = enabled && llmEnabled
    val llmConfig = LlmCorrectionSettings.current()
    val recognitionSummary = recognitionServiceSummary()
    val correctionSummary = correctionServiceSummary()

    val periodStyle = PeriodStyle.of(prefs.periodStyle.getValue())
    val punctuationRule = PunctuationRule.of(prefs.punctuationRule.getValue())
    // 顺序跟 PeriodStyle.entries / PunctuationRule.entries 一致
    val periodLabels = listOf(
        stringResource(R.string.voice_period_chinese),
        stringResource(R.string.voice_period_english),
        stringResource(R.string.voice_period_space),
        stringResource(R.string.voice_period_none),
    )
    val ruleLabels = listOf(
        stringResource(R.string.voice_punctuation_preserve),
        stringResource(R.string.voice_punctuation_strip_periods),
        stringResource(R.string.voice_punctuation_strip_trailing),
        stringResource(R.string.voice_punctuation_questions),
        stringResource(R.string.voice_punctuation_remove_all),
    )
    val punctuationSample = stringResource(R.string.voice_punctuation_sample)
    val punctuationExample = stringResource(R.string.voice_punctuation_example, punctuationSample)
    val punctuationDialogMessage = stringResource(R.string.voice_punctuation_note) + "\n\n" + punctuationExample

    /** 示例句子按 [options] 整理后的样子，设置页和选项对话框里实时显示。 */
    fun punctuationPreview(options: PunctuationOptions) = "→ " + PunctuationFormatter.format(punctuationSample, options)

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
                // 按 VOICE_ASSIST 键（不是内置听写的麦克风键）时切到哪个系统语音输入法
                PreferenceDelegateRow(AppPrefs.defaultInstance().general, AppPrefs.General.PREFERRED_VOICE_INPUT)

                PreferenceCategoryHeader(stringResource(R.string.voice_dictation))
                PreferenceRow(
                    title = stringResource(R.string.voice_auto_stop),
                    summary = stringResource(R.string.voice_auto_stop_summary),
                    enabled = enabled,
                )
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

                PreferenceCategoryHeader(stringResource(R.string.voice_punctuation))
                PreferenceRow(
                    title = stringResource(R.string.voice_period_style),
                    summary = periodLabels[periodStyle.ordinal],
                    enabled = enabled,
                    onClick = { dialog = VoiceDialog.PERIOD_STYLE },
                )
                PreferenceRow(
                    title = stringResource(R.string.voice_punctuation_rule),
                    summary = ruleLabels[punctuationRule.ordinal],
                    enabled = enabled,
                    onClick = { dialog = VoiceDialog.PUNCTUATION_RULE },
                )
                NoteText(punctuationExample + "\n" + punctuationPreview(PunctuationOptions(periodStyle, punctuationRule)))

                // 开关和阈值是「怎么纠」，放这里；服务商、地址、模型、key、提示词在「纠错服务」子页面
                PreferenceCategoryHeader(stringResource(R.string.voice_llm))
                NoteText(stringResource(R.string.voice_llm_privacy))
                SwitchPreferenceItem(
                    title = stringResource(R.string.voice_llm_enabled),
                    summary = if (llmEnabled && !llmConfig.isComplete) {
                        stringResource(R.string.voice_llm_enabled_incomplete)
                    } else {
                        stringResource(R.string.voice_llm_enabled_summary)
                    },
                    checked = llmEnabled,
                    enabled = enabled,
                    onCheckedChange = {
                        prefs.llmEnabled.setValue(it)
                        revision++
                    },
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

                // 服务配置：入口不随开关置灰，先配好再打开是正常用法
                PreferenceCategoryHeader(stringResource(R.string.settings_section_services))
                PreferenceRow(
                    title = stringResource(R.string.voice_recognition_service),
                    summary = recognitionSummary,
                    onClick = onOpenRecognitionService,
                )
                PreferenceRow(
                    title = stringResource(R.string.voice_correction_service),
                    summary = correctionSummary,
                    onClick = onOpenCorrectionService,
                )

                PreferenceCategoryHeader(stringResource(R.string.voice_vocabulary))
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

    val closeDialog = {
        revision++
        dialog = null
    }

    when (dialog) {
        VoiceDialog.PERIOD_STYLE -> SingleChoiceDialog(
            title = stringResource(R.string.voice_period_style),
            message = punctuationDialogMessage,
            entries = periodLabels,
            entrySummaries = PeriodStyle.entries.map { punctuationPreview(PunctuationOptions(it, punctuationRule)) },
            selectedIndex = periodStyle.ordinal,
            onSelect = {
                prefs.periodStyle.setValue(PeriodStyle.entries[it].id)
                closeDialog()
            },
            onDismiss = { dialog = null },
        )
        VoiceDialog.PUNCTUATION_RULE -> SingleChoiceDialog(
            title = stringResource(R.string.voice_punctuation_rule),
            message = punctuationDialogMessage,
            entries = ruleLabels,
            entrySummaries = PunctuationRule.entries.map { punctuationPreview(PunctuationOptions(periodStyle, it)) },
            selectedIndex = punctuationRule.ordinal,
            onSelect = {
                prefs.punctuationRule.setValue(PunctuationRule.entries[it].id)
                closeDialog()
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}
