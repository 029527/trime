/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util.update

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit

class UpdateChannelSelector(
    private val client: OkHttpClient = pingClient,
    private val channels: List<UpdateChannel> = DEFAULT_CHANNELS,
) {

    data class SelectedChannelResult(
        val channel: UpdateChannel,
        val latencyMs: Long,
        val sortedChannels: List<Pair<UpdateChannel, Long>>,
    )

    /**
     * 并发对所有候选通道测速，返回延迟最低的通道
     * 有代理时直连低延迟胜出，无代理时国内镜像胜出
     */
    suspend fun selectBestChannel(
        repo: String,
        branch: String = "custom",
    ): SelectedChannelResult = coroutineScope {
        val testPath = "https://raw.githubusercontent.com/$repo/$branch/README.md"

        val latencyResults = channels.map { channel ->
            async(Dispatchers.IO) {
                val pingUrl = if (channel.proxyPrefix.isBlank()) {
                    testPath
                } else {
                    "${channel.proxyPrefix.trimEnd('/')}/$testPath"
                }

                val start = SystemClock.elapsedRealtime()
                try {
                    val request = Request.Builder()
                        .url(pingUrl)
                        .head()
                        .header("User-Agent", "Trime-Latency-Detector")
                        .build()

                    client.newCall(request).execute().use { response ->
                        val elapsed = SystemClock.elapsedRealtime() - start
                        if (response.isSuccessful || response.code in 200..399) {
                            Timber.d("Channel [${channel.name}] ping success: ${elapsed}ms")
                            channel to elapsed
                        } else {
                            Timber.w("Channel [${channel.name}] ping HTTP ${response.code}, elapsed ${elapsed}ms")
                            channel to Long.MAX_VALUE
                        }
                    }
                } catch (e: Exception) {
                    val elapsed = SystemClock.elapsedRealtime() - start
                    Timber.d("Channel [${channel.name}] ping failed in ${elapsed}ms: ${e.message}")
                    channel to Long.MAX_VALUE
                }
            }
        }.awaitAll()

        // 筛选出成功的通道按延迟由低到高排序
        val sorted = latencyResults.sortedBy { it.second }
        val best = sorted.firstOrNull { it.second < Long.MAX_VALUE }

        if (best != null) {
            Timber.i("Selected best update channel: ${best.first.name} (latency: ${best.second}ms)")
            SelectedChannelResult(
                channel = best.first,
                latencyMs = best.second,
                sortedChannels = sorted,
            )
        } else {
            // 所有探测均失败（如完全无网），默认回退到第一个镜像源
            val fallback = channels.firstOrNull { it.proxyPrefix.isNotBlank() } ?: channels.first()
            Timber.w("All channel pings failed, falling back to: ${fallback.name}")
            SelectedChannelResult(
                channel = fallback,
                latencyMs = -1L,
                sortedChannels = sorted,
            )
        }
    }

    companion object {
        val DEFAULT_CHANNELS = listOf(
            UpdateChannel(name = "GitHub 直连", proxyPrefix = ""),
            UpdateChannel(name = "ghfast.top 镜像", proxyPrefix = "https://ghfast.top/"),
            UpdateChannel(name = "ghproxy.net 镜像", proxyPrefix = "https://ghproxy.net/"),
        )

        private val pingClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(2500, TimeUnit.MILLISECONDS)
                .readTimeout(2500, TimeUnit.MILLISECONDS)
                .callTimeout(3000, TimeUnit.MILLISECONDS)
                .build()
        }
    }
}
