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
import com.osfans.trime.BuildConfig
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

private enum class VoiceDialog { PROVIDER, TRIGGER, AUTH_MODE, RESOURCE, API_KEY, APP_KEY, ACCESS_KEY }

/**
 * 语音输入设置页。手写页面（不走 preference 渲染器），因为火山凭证不能存在
 * 普通的 SharedPreferences 里 —— 它们走 [VoiceCredentialStore]
 * （EncryptedSharedPreferences），跟 `PreferenceDelegate` 的存取模型对不上。
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
    val provider = prefs.provider.getValue()
    val triggerMode = prefs.triggerMode.getValue()
    val authMode = prefs.authMode.getValue()
    val resourceId = prefs.resourceId.getValue()
    val isVolcano = provider != AppPrefs.Voice.PROVIDER_FAKE

    val providerValues = buildList {
        add(AppPrefs.Voice.PROVIDER_VOLCANO)
        // 假识别只在 debug 包里露出来：它是用来在没有凭证时验证整条链路的
        if (BuildConfig.DEBUG) add(AppPrefs.Voice.PROVIDER_FAKE)
    }
    val providerLabels = providerValues.map {
        when (it) {
            AppPrefs.Voice.PROVIDER_FAKE -> stringResource(R.string.voice_provider_fake)
            else -> stringResource(R.string.voice_provider_volcano)
        }
    }

    val triggerValues = listOf(
        AppPrefs.Voice.TRIGGER_BOTH,
        AppPrefs.Voice.TRIGGER_HOLD,
        AppPrefs.Voice.TRIGGER_TOGGLE,
    )
    val triggerLabels = listOf(
        stringResource(R.string.voice_trigger_both),
        stringResource(R.string.voice_trigger_hold),
        stringResource(R.string.voice_trigger_toggle),
    )

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
                    summary = providerLabels.getOrNull(providerValues.indexOf(provider)) ?: providerLabels.first(),
                    enabled = enabled,
                    onClick = { dialog = VoiceDialog.PROVIDER },
                )
                PreferenceRow(
                    title = stringResource(R.string.voice_trigger_mode),
                    summary = triggerLabels.getOrNull(triggerValues.indexOf(triggerMode)) ?: triggerLabels.first(),
                    enabled = enabled,
                    onClick = { dialog = VoiceDialog.TRIGGER },
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
                    enabled = enabled && isVolcano,
                    onClick = { dialog = VoiceDialog.AUTH_MODE },
                )
                if (authMode == VolcConfig.AUTH_MODE_LEGACY) {
                    PreferenceRow(
                        title = stringResource(R.string.voice_app_key),
                        summary = VoiceCredentialStore.masked(VoiceCredentialStore.KEY_APP_KEY).ifEmpty { unset },
                        enabled = enabled && isVolcano,
                        onClick = { dialog = VoiceDialog.APP_KEY },
                    )
                    PreferenceRow(
                        title = stringResource(R.string.voice_access_key),
                        summary = VoiceCredentialStore.masked(VoiceCredentialStore.KEY_ACCESS_KEY).ifEmpty { unset },
                        enabled = enabled && isVolcano,
                        onClick = { dialog = VoiceDialog.ACCESS_KEY },
                    )
                } else {
                    PreferenceRow(
                        title = stringResource(R.string.voice_api_key),
                        summary = VoiceCredentialStore.masked(VoiceCredentialStore.KEY_API_KEY).ifEmpty { unset },
                        enabled = enabled && isVolcano,
                        onClick = { dialog = VoiceDialog.API_KEY },
                    )
                }
                PreferenceRow(
                    title = stringResource(R.string.voice_resource_id),
                    summary = resourceLabels.getOrNull(VolcConfig.RESOURCE_IDS.indexOf(resourceId)) ?: resourceLabels.first(),
                    enabled = enabled && isVolcano,
                    onClick = { dialog = VoiceDialog.RESOURCE },
                )
                PreferenceRow(
                    title = stringResource(R.string.voice_clear_credentials),
                    enabled = isVolcano,
                    onClick = {
                        VoiceCredentialStore.clearAll()
                        revision++
                    },
                )

                PreferenceCategoryHeader(stringResource(R.string.voice_input))
                val silence = prefs.silenceTimeout.getValue()
                SliderPreferenceItem(
                    title = stringResource(R.string.voice_silence_timeout),
                    value = silence,
                    min = 2,
                    max = 30,
                    step = 1,
                    valueLabel = "$silence s",
                    unit = "s",
                    defaultValue = 6,
                    enabled = enabled,
                    onValueChangeFinished = {
                        prefs.silenceTimeout.setValue(it)
                        revision++
                    },
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
        VoiceDialog.PROVIDER -> SingleChoiceDialog(
            title = stringResource(R.string.voice_provider),
            entries = providerLabels,
            selectedIndex = providerValues.indexOf(provider),
            onSelect = {
                prefs.provider.setValue(providerValues[it])
                revision++
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        VoiceDialog.TRIGGER -> SingleChoiceDialog(
            title = stringResource(R.string.voice_trigger_mode),
            entries = triggerLabels,
            selectedIndex = triggerValues.indexOf(triggerMode),
            onSelect = {
                prefs.triggerMode.setValue(triggerValues[it])
                revision++
                dialog = null
            },
            onDismiss = { dialog = null },
        )
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
