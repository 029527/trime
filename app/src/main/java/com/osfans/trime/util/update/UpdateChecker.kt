/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util.update

import android.os.Build
import com.osfans.trime.BuildConfig
import com.osfans.trime.util.Const
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class UpdateChecker(
    private val client: OkHttpClient = defaultClient,
    private val channelSelector: UpdateChannelSelector = UpdateChannelSelector(),
    private val targetRepo: String = extractRepoFromGitUrl(BuildConfig.BUILD_GIT_REPO),
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * 检查更新（先检测延迟选择最优通道）
     *
     * @param currentVersion 本地当前版本，默认采用 Const.VERSION_NAME
     * @return CheckUpdateResult (HasUpdate, AlreadyLatest, Error)
     */
    suspend fun checkUpdate(
        currentVersion: String = Const.VERSION_NAME,
    ): CheckUpdateResult = withContext(Dispatchers.IO) {
        // 1. 先并发检测网络延迟，选出最优通道（有代理时 GitHub 直连胜出，无代理时国内镜像胜出）
        val channelResult = channelSelector.selectBestChannel(targetRepo)
        val bestChannel = channelResult.channel
        Timber.i("Selected update channel: ${bestChannel.name}, latency: ${channelResult.latencyMs}ms")

        val releasesUrl = "https://api.github.com/repos/$targetRepo/releases"
        
        // 尝试拉取 Releases 列表
        val urlsToTry = buildList {
            if (bestChannel.proxyPrefix.isNotBlank()) {
                add("${bestChannel.proxyPrefix.trimEnd('/')}/$releasesUrl")
            }
            add(releasesUrl)
            UpdateChannelSelector.DEFAULT_CHANNELS
                .filter { it != bestChannel && it.proxyPrefix.isNotBlank() }
                .forEach { add("${it.proxyPrefix.trimEnd('/')}/$releasesUrl") }
        }.distinct()

        var lastException: Throwable? = null
        var responseJson: String? = null

        for (url in urlsToTry) {
            try {
                Timber.d("Checking update from URL: $url")
                responseJson = fetchString(url)
                if (!responseJson.isNullOrBlank()) {
                    break
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch releases from $url")
                lastException = e
            }
        }

        if (responseJson.isNullOrBlank()) {
            return@withContext CheckUpdateResult.Error(
                message = lastException?.localizedMessage ?: "Failed to fetch release info",
                cause = lastException,
            )
        }

        val release = runCatching { parseRelease(responseJson) }.getOrElse { e ->
            Timber.e(e, "Failed to parse release JSON")
            return@withContext CheckUpdateResult.Error("Failed to parse release response", e)
        }

        if (release == null) {
            return@withContext CheckUpdateResult.Error("No valid releases found")
        }

        val apkAsset = findBestApkAsset(release.assets)
        if (apkAsset == null) {
            return@withContext CheckUpdateResult.Error("No APK file found in the latest release")
        }

        val hasNewVersion = VersionComparator.isNewerVersion(
            currentVersion = currentVersion,
            remoteTag = release.tagName,
            remoteName = release.name,
            remoteAssetName = apkAsset.name,
        )

        val rawDownloadUrl = apkAsset.browserDownloadUrl
        val primaryDownloadUrl = if (bestChannel.proxyPrefix.isNotBlank()) {
            "${bestChannel.proxyPrefix.trimEnd('/')}/$rawDownloadUrl"
        } else {
            rawDownloadUrl
        }

        // 构建备用下载通道（排好序的其他通道，如果主选失败可重试）
        val fallbackUrls = channelResult.sortedChannels
            .map { it.first }
            .filter { it != bestChannel }
            .map { channel ->
                if (channel.proxyPrefix.isNotBlank()) {
                    "${channel.proxyPrefix.trimEnd('/')}/$rawDownloadUrl"
                } else {
                    rawDownloadUrl
                }
            }
            .distinct()

        val displayVersion = release.name?.takeIf { it.isNotBlank() } ?: release.tagName
        val updateInfo = UpdateInfo(
            versionName = release.tagName,
            displayTitle = displayVersion,
            releaseNotes = release.body.orEmpty(),
            downloadUrl = primaryDownloadUrl,
            originalDownloadUrl = rawDownloadUrl,
            fileName = apkAsset.name,
            fileSize = apkAsset.size,
            currentVersion = currentVersion,
            channelName = bestChannel.name,
            latencyMs = channelResult.latencyMs,
            fallbackDownloadUrls = fallbackUrls,
        )

        if (hasNewVersion) {
            CheckUpdateResult.HasUpdate(updateInfo)
        } else {
            CheckUpdateResult.AlreadyLatest(currentVersion)
        }
    }

    private fun parseRelease(responseJson: String): GithubRelease? {
        val trimmed = responseJson.trim()
        return if (trimmed.startsWith("[")) {
            // 是 Releases 数组，取第一个未处于草稿(draft)状态的 Release
            val releases = json.decodeFromString<List<GithubRelease>>(trimmed)
            releases.firstOrNull { !it.draft }
        } else {
            // 是单个 Release 对象
            json.decodeFromString<GithubRelease>(trimmed)
        }
    }

    /**
     * 根据设备当前支持的 ABI 优先匹配最合适的 APK
     */
    fun findBestApkAsset(assets: List<GithubAsset>): GithubAsset? {
        val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apkAssets.isEmpty()) return null
        if (apkAssets.size == 1) return apkAssets.first()

        val supportedAbis = Build.SUPPORTED_ABIS ?: emptyArray()
        for (abi in supportedAbis) {
            val matched = apkAssets.firstOrNull { it.name.contains(abi, ignoreCase = true) }
            if (matched != null) {
                return matched
            }
        }

        // 查找 universal 或不带具体架构的通用包
        val universal = apkAssets.firstOrNull {
            it.name.contains("universal", ignoreCase = true) ||
                it.name.contains("all", ignoreCase = true)
        }
        return universal ?: apkAssets.first()
    }

    private suspend fun fetchString(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "Trime-App-UpdateChecker")
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use { resp ->
                            if (!resp.isSuccessful) {
                                continuation.resumeWithException(
                                    IOException("HTTP error: ${resp.code} ${resp.message}"),
                                )
                                return
                            }
                            val bodyString = resp.body?.string().orEmpty()
                            continuation.resume(bodyString)
                        }
                    }
                },
            )
        }
    }

    companion object {
        const val DEFAULT_PROXY_PREFIX = "https://ghfast.top/"
        private const val DEFAULT_REPO = "029527/trime"

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }

        fun extractRepoFromGitUrl(url: String?): String {
            if (url.isNullOrBlank()) return DEFAULT_REPO
            // 匹配 github.com/(owner)/(repo) 或 git@github.com:(owner)/(repo)
            val regex = Regex("""github\.com[/:]([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\.git)?$""")
            val match = regex.find(url.trim()) ?: return DEFAULT_REPO
            val owner = match.groupValues[1]
            val repo = match.groupValues[2]
            return "$owner/$repo"
        }
    }
}
