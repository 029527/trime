/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubRelease(
    @SerialName("tag_name")
    val tagName: String = "",
    @SerialName("name")
    val name: String? = null,
    @SerialName("body")
    val body: String? = null,
    @SerialName("prerelease")
    val prerelease: Boolean = false,
    @SerialName("draft")
    val draft: Boolean = false,
    @SerialName("published_at")
    val publishedAt: String? = null,
    @SerialName("assets")
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(
    @SerialName("name")
    val name: String = "",
    @SerialName("browser_download_url")
    val browserDownloadUrl: String = "",
    @SerialName("size")
    val size: Long = 0L,
    @SerialName("content_type")
    val contentType: String? = null,
)

data class UpdateChannel(
    val name: String,
    val proxyPrefix: String, // 直连为空字符串 ""，镜像如 "https://ghfast.top/"
)

data class UpdateInfo(
    val versionName: String,
    val displayTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val originalDownloadUrl: String,
    val fileName: String,
    val fileSize: Long,
    val currentVersion: String,
    val channelName: String = "",
    val latencyMs: Long = -1L,
    val fallbackDownloadUrls: List<String> = emptyList(),
)

sealed interface CheckUpdateResult {
    data class HasUpdate(val updateInfo: UpdateInfo) : CheckUpdateResult
    data class AlreadyLatest(val currentVersion: String) : CheckUpdateResult
    data class Error(val message: String, val cause: Throwable? = null) : CheckUpdateResult
}
