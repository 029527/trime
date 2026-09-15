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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ui.compose.TrimeScreen
import com.osfans.trime.ui.compose.preference.PreferenceCategoryHeader
import com.osfans.trime.ui.compose.preference.PreferenceRow
import com.osfans.trime.ui.compose.preference.SingleChoiceDialog
import com.osfans.trime.ui.compose.preference.SliderPreferenceItem
import com.osfans.trime.ui.compose.preference.SwitchPreferenceItem
import com.osfans.trime.ui.compose.preference.TextInputDialog
import com.osfans.trime.util.toast
import com.osfans.trime.voice.VoiceCredentialStore
import com.osfans.trime.voice.audio.VoiceAudioSource
import com.osfans.trime.voice.vocab.VoiceVocabularyStore
import com.osfans.trime.voice.volc.VolcConfig

private enum class VoiceDialog { AUTH_MODE, RESOURCE, API_KEY, APP_KEY, ACCESS_KEY, BOOSTING_TABLE }

/**
 * 语音输入设置页。手写页面（不走 preference 渲染器），因为火山凭证不能存在
 * 普通的 SharedPreferences 里 —— 它们走 [VoiceCredentialStore]
 * （EncryptedSharedPreferences），跟 `PreferenceDelegate` 的存取模型对不上。
 *
 * 没有凭证时验证听写界面用的「模拟识别」在「开发者」页，只有 debug 包有。
 */
@Composable
fun VoiceInputScreen(
    onNavigateUp: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = AppPrefs.defaultInstance().voice

    // 这些值不是 Compose 状态，改完要手动 bump 一下让整页重算
    var revision by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<VoiceDialog?>(null) }

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

    val unset = stringResource(R.string.voice_credential_unset)

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
                PreferenceRow(
                    title = stringResource(R.string.voice_theme_hint),
                    summary = stringResource(R.string.voice_theme_hint_summary),
                )

                PreferenceCategoryHeader(stringResource(R.string.voice_credentials))
                Text(
                    text = stringResource(R.string.voice_credentials_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
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

                PreferenceCategoryHeader(stringResource(R.string.voice_input))
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

    when (dialog) {
        VoiceDialog.AUTH_MODE -> SingleChoiceDialog(
            title = stringResource(R.string.voice_auth_mode),
            entries = authLabels,
            selectedIndex = authValues.indexOf(authMode).coerceAtLeast(0),
            onSelect = {
                prefs.authMode.setValue(authValues[it])
                revision++
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        VoiceDialog.RESOURCE -> SingleChoiceDialog(
            title = stringResource(R.string.voice_resource_id),
            entries = resourceLabels,
            selectedIndex = VolcConfig.RESOURCE_IDS.indexOf(resourceId).coerceAtLeast(0),
            onSelect = {
                prefs.resourceId.setValue(VolcConfig.RESOURCE_IDS[it])
                revision++
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        VoiceDialog.API_KEY -> CredentialDialog(
            title = stringResource(R.string.voice_api_key),
            storeKey = VoiceCredentialStore.KEY_API_KEY,
            onDone = {
                revision++
                dialog = null
            },
        )
        VoiceDialog.APP_KEY -> CredentialDialog(
            title = stringResource(R.string.voice_app_key),
            storeKey = VoiceCredentialStore.KEY_APP_KEY,
            onDone = {
                revision++
                dialog = null
            },
        )
        VoiceDialog.ACCESS_KEY -> CredentialDialog(
            title = stringResource(R.string.voice_access_key),
            storeKey = VoiceCredentialStore.KEY_ACCESS_KEY,
            onDone = {
                revision++
                dialog = null
            },
        )
        VoiceDialog.BOOSTING_TABLE -> TextInputDialog(
            title = stringResource(R.string.voice_boosting_table_id),
            initialValue = prefs.boostingTableId.getValue(),
            message = stringResource(R.string.voice_boosting_table_id_note),
            onConfirm = {
                prefs.boostingTableId.setValue(it.trim())
                revision++
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

/**
 * 凭证输入框。**故意不回显已存的值**（输入框从空开始）：
 * 密钥没有必要摊在屏幕上，留空确认就是清掉这一项。
 */
@Composable
private fun CredentialDialog(
    title: String,
    storeKey: String,
    onDone: () -> Unit,
) {
    TextInputDialog(
        title = title,
        initialValue = "",
        message = stringResource(R.string.voice_credentials_note),
        onConfirm = {
            VoiceCredentialStore.put(storeKey, it)
            onDone()
        },
        onDismiss = onDone,
    )
}
