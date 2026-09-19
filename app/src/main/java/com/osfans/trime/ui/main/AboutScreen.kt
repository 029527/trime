/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.osfans.trime.BuildConfig
import com.osfans.trime.R
import com.osfans.trime.ui.compose.TrimeScreen
import com.osfans.trime.ui.compose.preference.PreferenceCard
import com.osfans.trime.ui.compose.preference.PreferenceRow
import com.osfans.trime.util.Const
import com.osfans.trime.util.formatDateTime
import com.osfans.trime.util.toast
import com.osfans.trime.util.update.CheckUpdateResult
import com.osfans.trime.util.update.DownloadState
import com.osfans.trime.util.update.UpdateInfo
import com.osfans.trime.util.update.UpdateManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

private val DASH_G_PATTERN = Regex("^(.*-g)([0-9a-f]+)(.*)$")
private val COMMON_PATTERN = Regex("^([^-]*)(-.*)$")

/**
 * Both librime and OpenCC report a `git describe`-like version; the About page links
 * to the exact commit, so the hash has to be dug out of it again.
 */
private fun commitOfVersionName(versionName: String): String {
    val dashG = DASH_G_PATTERN.find(versionName)?.groupValues?.get(2)
    val common = COMMON_PATTERN.find(versionName)?.groupValues?.get(1)
    return dashG ?: common ?: versionName
}

@Composable
fun AboutScreen(
    onNavigateUp: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val context = LocalContext.current
    // `LocalClipboardManager` is deprecated; the replacement writes asynchronously.
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // QQ and Telegram links resolve to apps that may not be installed; the old
    // preference screen let that throw, here it simply does nothing.
    fun open(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    val buildInfo = stringResource(
        R.string.build_info_format,
        BuildConfig.BUILDER,
        BuildConfig.BUILD_COMMIT_HASH,
        formatDateTime(BuildConfig.BUILD_TIMESTAMP),
    )

    val updateManager = remember { UpdateManager(context) }
    var isChecking by remember { mutableStateOf(false) }
    var updateDialogInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadBytesRead by remember { mutableLongStateOf(0L) }
    var downloadTotalBytes by remember { mutableLongStateOf(0L) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    fun startDownload(info: UpdateInfo) {
        downloadJob?.cancel()
        isDownloading = true
        downloadError = null
        downloadProgress = 0f
        downloadBytesRead = 0L
        downloadTotalBytes = info.fileSize

        downloadJob = scope.launch {
            updateManager.downloadApk(info).collect { state ->
                when (state) {
                    is DownloadState.Idle -> {}
                    is DownloadState.Downloading -> {
                        downloadBytesRead = state.bytesRead
                        downloadTotalBytes = if (state.totalBytes > 0) state.totalBytes else info.fileSize
                        downloadProgress = state.progress
                    }
                    is DownloadState.Success -> {
                        isDownloading = false
                        updateDialogInfo = null
                        val installed = updateManager.installApk(state.file)
                        if (!installed && !updateManager.canRequestPackageInstalls()) {
                            context.toast(R.string.update_install_permission_denied)
                            updateManager.openInstallPermissionSettings()
                        }
                    }
                    is DownloadState.Error -> {
                        isDownloading = false
                        downloadError = state.message
                    }
                }
            }
        }
    }

    TrimeScreen(
        title = stringResource(R.string.about),
        onNavigateUp = onNavigateUp,
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item("header") { AppHeader() }
            item("version") {
                PreferenceCard {
                    PreferenceRow(
                        title = stringResource(R.string.current_version),
                        summary = Const.VERSION_NAME,
                        icon = R.drawable.ic_baseline_check_circle_24,
                        horizontalPadding = CardPadding,
                        onClick = {
                            open("${BuildConfig.BUILD_GIT_REPO}/commit/${BuildConfig.BUILD_COMMIT_HASH}")
                        },
                    )
                    PreferenceRow(
                        title = stringResource(R.string.check_update),
                        summary = if (isChecking) stringResource(R.string.checking_update) else null,
                        icon = R.drawable.ic_baseline_system_update_24,
                        enabled = !isChecking,
                        horizontalPadding = CardPadding,
                        trailing = if (isChecking) {
                            {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        } else null,
                        onClick = {
                            if (isChecking) return@PreferenceRow
                            isChecking = true
                            scope.launch {
                                try {
                                    when (val result = updateManager.checkUpdate()) {
                                        is CheckUpdateResult.AlreadyLatest -> {
                                            context.toast(R.string.update_already_latest)
                                        }
                                        is CheckUpdateResult.HasUpdate -> {
                                            updateDialogInfo = result.updateInfo
                                        }
                                        is CheckUpdateResult.Error -> {
                                            val errorMsg = context.getString(R.string.update_check_failed, result.message)
                                            context.toast(errorMsg)
                                        }
                                    }
                                } catch (e: Exception) {
                                    val errorMsg = context.getString(R.string.update_check_failed, e.localizedMessage ?: "Unknown error")
                                    context.toast(errorMsg)
                                } finally {
                                    isChecking = false
                                }
                            }
                        },
                    )
                    PreferenceRow(
                        title = stringResource(R.string.librime_version),
                        summary = BuildConfig.LIBRIME_VERSION,
                        horizontalPadding = CardPadding,
                        onClick = {
                            open("${Const.LIBRIME_URL}/commit/${commitOfVersionName(BuildConfig.LIBRIME_VERSION)}")
                        },
                    )
                    PreferenceRow(
                        title = stringResource(R.string.opencc_version),
                        summary = BuildConfig.OPENCC_VERSION,
                        horizontalPadding = CardPadding,
                        onClick = {
                            open("${Const.OPENCC_URL}/commit/${commitOfVersionName(BuildConfig.OPENCC_VERSION)}")
                        },
                    )
                    PreferenceRow(
                        title = stringResource(R.string.build_info),
                        summary = buildInfo,
                        icon = R.drawable.ic_baseline_content_copy_24,
                        horizontalPadding = CardPadding,
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("build info", buildInfo)),
                                )
                                context.toast(R.string.copy_done)
                            }
                        },
                    )
                }
            }
            item("links") {
                CardSpacer()
                PreferenceCard {
                    PreferenceRow(
                        title = stringResource(R.string.source_code),
                        summary = stringResource(R.string.git_repo),
                        icon = R.drawable.ic_baseline_link_24,
                        horizontalPadding = CardPadding,
                        onClick = { open(BuildConfig.BUILD_GIT_REPO) },
                    )
                    PreferenceRow(
                        title = stringResource(R.string.privacy_policy),
                        icon = R.drawable.ic_baseline_lock_24,
                        horizontalPadding = CardPadding,
                        onClick = { open(Const.PRIVACY_POLICY_URL) },
                    )
                    PreferenceRow(
                        title = stringResource(R.string.license),
                        summary = Const.LICENSE_SPDX_ID,
                        icon = R.drawable.ic_baseline_book_24,
                        horizontalPadding = CardPadding,
                        onClick = { open(Const.LICENSE_URL) },
                    )
                    PreferenceRow(
                        title = stringResource(R.string.open_source_licenses),
                        summary = stringResource(R.string.licenses_of_third_party_libraries),
                        icon = R.drawable.ic_baseline_list_alt_24,
                        horizontalPadding = CardPadding,
                        onClick = onOpenLicenses,
                    )
                }
            }
            item("community") {
                CardSpacer()
                PreferenceCard {
                    CommunityRow(R.string.qq_group_1, Const.QQ_GROUP_1_NUM, R.drawable.ic_baseline_star_24) {
                        open(Const.QQ_GROUP_1_URL)
                    }
                    CommunityRow(R.string.qq_group_2, Const.QQ_GROUP_2_NUM, R.drawable.ic_baseline_star_24) {
                        open(Const.QQ_GROUP_2_URL)
                    }
                    CommunityRow(R.string.rime_qq_group, Const.RIME_QQ_GROUP_NUM, R.drawable.ic_baseline_star_24) {
                        open(Const.RIME_QQ_GROUP_URL)
                    }
                    CommunityRow(R.string.telegram, Const.TELEGRAM_NAME, R.drawable.ic_baseline_share_24) {
                        open(Const.TELEGRAM_URL)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        val currentUpdate = updateDialogInfo
        if (currentUpdate != null) {
            if (!isDownloading && downloadError == null) {
                AlertDialog(
                    onDismissRequest = { updateDialogInfo = null },
                    title = {
                        Text(
                            text = stringResource(R.string.update_dialog_title, currentUpdate.displayTitle),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            if (currentUpdate.releaseNotes.isNotBlank()) {
                                Text(
                                    text = currentUpdate.releaseNotes,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            if (currentUpdate.fileSize > 0) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = formatFileSize(currentUpdate.fileSize),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (currentUpdate.channelName.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                val channelText = if (currentUpdate.latencyMs >= 0) {
                                    stringResource(R.string.update_channel_with_latency, currentUpdate.channelName, currentUpdate.latencyMs)
                                } else {
                                    stringResource(R.string.update_channel_format, currentUpdate.channelName)
                                }
                                Text(
                                    text = channelText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { startDownload(currentUpdate) }) {
                            Text(stringResource(R.string.update_dialog_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { updateDialogInfo = null }) {
                            Text(stringResource(R.string.update_dialog_cancel))
                        }
                    },
                )
            } else {
                AlertDialog(
                    onDismissRequest = {},
                    title = {
                        Text(
                            text = if (downloadError != null) {
                                stringResource(R.string.check_update)
                            } else {
                                stringResource(R.string.update_downloading, (downloadProgress * 100).toInt())
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (downloadError != null) {
                                Text(
                                    text = stringResource(R.string.update_download_failed, downloadError ?: ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                if (downloadTotalBytes > 0) {
                                    LinearProgressIndicator(
                                        progress = { downloadProgress },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(
                                            R.string.update_download_progress,
                                            formatFileSize(downloadBytesRead),
                                            formatFileSize(downloadTotalBytes),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = formatFileSize(downloadBytesRead),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        if (downloadError != null) {
                            TextButton(onClick = { startDownload(currentUpdate) }) {
                                Text(stringResource(R.string.update_retry))
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                downloadJob?.cancel()
                                downloadJob = null
                                isDownloading = false
                                downloadError = null
                                updateDialogInfo = null
                            },
                        ) {
                            Text(stringResource(R.string.update_dialog_cancel))
                        }
                    },
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, 3)
    return String.format(Locale.getDefault(), "%.1f %s", bytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}

private val CardPadding = 20.dp

@Composable
private fun CardSpacer() {
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun CommunityRow(
    title: Int,
    summary: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
) {
    PreferenceRow(
        title = stringResource(title),
        summary = summary,
        icon = icon,
        horizontalPadding = CardPadding,
        onClick = onClick,
    )
}

/** App icon, name and version, drawn above the cards. */
@Composable
private fun AppHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_app_icon_foreground),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(88.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.trime_app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = Const.VERSION_NAME,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.trime_app_slogan),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
    }
}
