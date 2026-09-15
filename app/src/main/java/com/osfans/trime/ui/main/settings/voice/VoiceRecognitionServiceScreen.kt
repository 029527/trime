/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.voice

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ui.compose.ComposeFragment
import com.osfans.trime.ui.compose.TrimeScreen
import com.osfans.trime.ui.compose.preference.PreferenceCategoryHeader
import com.osfans.trime.ui.compose.preference.PreferenceRow
import com.osfans.trime.ui.compose.preference.SingleChoiceDialog
import com.osfans.trime.ui.compose.preference.TextInputDialog
import com.osfans.trime.voice.VoiceCredentialStore
import com.osfans.trime.voice.volc.VolcConfig

private enum class RecognitionDialog {
    AUTH_MODE,
    RESOURCE,
    API_KEY,
    APP_KEY,
    ACCESS_KEY,
    BOOSTING_TABLE,
}

/**
 * 语音输入 › 识别服务：火山引擎的识别模型、云端热词表 ID 和凭证。清空凭证是破坏性操作，放在最后。
 *
 * 云端热词表 ID 虽然跟词库热词有关，但它是火山控制台里的一个 ID，属于服务配置，所以在这里。
 */
@Composable
fun VoiceRecognitionServiceScreen(onNavigateUp: () -> Unit) {
    val prefs = AppPrefs.defaultInstance().voice
    var revision by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<RecognitionDialog?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    @Suppress("UNUSED_EXPRESSION")
    revision

    val authMode = prefs.authMode.getValue()
    val resourceId = prefs.resourceId.getValue()
    val boostingTableId = prefs.boostingTableId.getValue()
    val authLabels = authModeLabels()
    val resourceLabels = resourceLabels()
    val unset = stringResource(R.string.voice_credential_unset)
    val apiKey = VoiceCredentialStore.masked(VoiceCredentialStore.KEY_API_KEY).ifEmpty { unset }
    val appKey = VoiceCredentialStore.masked(VoiceCredentialStore.KEY_APP_KEY).ifEmpty { unset }
    val accessKey = VoiceCredentialStore.masked(VoiceCredentialStore.KEY_ACCESS_KEY).ifEmpty { unset }

    TrimeScreen(
        title = stringResource(R.string.voice_recognition_service),
        onNavigateUp = onNavigateUp,
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                PreferenceCategoryHeader(stringResource(R.string.voice_section_recognition))
                PreferenceRow(
                    title = stringResource(R.string.voice_provider),
                    summary = stringResource(R.string.voice_provider_volcano),
                )
                PreferenceRow(
                    title = stringResource(R.string.voice_resource_id),
                    summary = resourceLabels.getOrNull(VolcConfig.RESOURCE_IDS.indexOf(resourceId)) ?: resourceLabels.first(),
                    onClick = { dialog = RecognitionDialog.RESOURCE },
                )
                PreferenceRow(
                    title = stringResource(R.string.voice_boosting_table_id),
                    summary = boostingTableId.ifEmpty { stringResource(R.string.voice_boosting_table_id_unset) },
                    onClick = { dialog = RecognitionDialog.BOOSTING_TABLE },
                )

                PreferenceCategoryHeader(stringResource(R.string.voice_credentials))
                NoteText(stringResource(R.string.voice_credentials_note))
                PreferenceRow(
                    title = stringResource(R.string.voice_auth_mode),
                    summary = authLabels.getOrNull(AuthModeValues.indexOf(authMode)) ?: authLabels.first(),
                    onClick = { dialog = RecognitionDialog.AUTH_MODE },
                )
                if (authMode == VolcConfig.AUTH_MODE_LEGACY) {
                    PreferenceRow(
                        title = stringResource(R.string.voice_app_key),
                        summary = appKey,
                        onClick = { dialog = RecognitionDialog.APP_KEY },
                    )
                    PreferenceRow(
                        title = stringResource(R.string.voice_access_key),
                        summary = accessKey,
                        onClick = { dialog = RecognitionDialog.ACCESS_KEY },
                    )
                } else {
                    PreferenceRow(
                        title = stringResource(R.string.voice_api_key),
                        summary = apiKey,
                        onClick = { dialog = RecognitionDialog.API_KEY },
                    )
                }
                PreferenceRow(
                    title = stringResource(R.string.voice_clear_credentials),
                    // 清掉就得重新去控制台抄一遍，先确认
                    onClick = { confirmClear = true },
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.voice_clear_credentials)) },
            text = { Text(stringResource(R.string.voice_clear_credentials_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        VoiceCredentialStore.clearAll()
                        revision++
                    },
                ) {
                    Text(stringResource(R.string.voice_clear_credentials_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    val closeDialog = {
        revision++
        dialog = null
    }

    when (dialog) {
        RecognitionDialog.AUTH_MODE -> SingleChoiceDialog(
            title = stringResource(R.string.voice_auth_mode),
            entries = authLabels,
            selectedIndex = AuthModeValues.indexOf(authMode).coerceAtLeast(0),
            onSelect = {
                prefs.authMode.setValue(AuthModeValues[it])
                closeDialog()
            },
            onDismiss = { dialog = null },
        )
        RecognitionDialog.RESOURCE -> SingleChoiceDialog(
            title = stringResource(R.string.voice_resource_id),
            entries = resourceLabels,
            selectedIndex = VolcConfig.RESOURCE_IDS.indexOf(resourceId).coerceAtLeast(0),
            onSelect = {
                prefs.resourceId.setValue(VolcConfig.RESOURCE_IDS[it])
                closeDialog()
            },
            onDismiss = { dialog = null },
        )
        RecognitionDialog.API_KEY -> CredentialDialog(
            title = stringResource(R.string.voice_api_key),
            storeKey = VoiceCredentialStore.KEY_API_KEY,
            onDone = closeDialog,
        )
        RecognitionDialog.APP_KEY -> CredentialDialog(
            title = stringResource(R.string.voice_app_key),
            storeKey = VoiceCredentialStore.KEY_APP_KEY,
            onDone = closeDialog,
        )
        RecognitionDialog.ACCESS_KEY -> CredentialDialog(
            title = stringResource(R.string.voice_access_key),
            storeKey = VoiceCredentialStore.KEY_ACCESS_KEY,
            onDone = closeDialog,
        )
        RecognitionDialog.BOOSTING_TABLE -> TextInputDialog(
            title = stringResource(R.string.voice_boosting_table_id),
            initialValue = boostingTableId,
            message = stringResource(R.string.voice_boosting_table_id_note),
            onConfirm = {
                prefs.boostingTableId.setValue(it.trim())
                closeDialog()
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

class VoiceRecognitionServiceFragment : ComposeFragment() {
    @Composable
    override fun Content() {
        VoiceRecognitionServiceScreen(onNavigateUp = ::navigateUp)
    }
}
