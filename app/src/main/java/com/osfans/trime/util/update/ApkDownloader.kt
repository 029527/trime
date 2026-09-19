/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util.update

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(
        val bytesRead: Long,
        val totalBytes: Long,
        val progress: Float, // 0.0f .. 1.0f
    ) : DownloadState
    data class Success(val file: File) : DownloadState
    data class Error(val message: String, val cause: Throwable? = null) : DownloadState
}

class ApkDownloader(
    private val context: Context,
    private val client: OkHttpClient = defaultClient,
) {

    /**
     * 下载 APK 文件并以 Flow 形式返回实时下载进度状态
     */
    fun downloadApk(
        downloadUrl: String,
        fileName: String,
        fallbackUrls: List<String> = emptyList(),
        fallbackUrl: String? = null,
    ): Flow<DownloadState> = channelFlow {
        send(DownloadState.Downloading(0L, -1L, 0f))

        val cacheDir = context.externalCacheDir ?: context.cacheDir
        val updatesDir = File(cacheDir, "updates").apply { mkdirs() }
        val targetFile = File(updatesDir, fileName)
        val tempFile = File(updatesDir, "$fileName.download.tmp")

        if (tempFile.exists()) {
            tempFile.delete()
        }

        val urlsToTry = buildList {
            add(downloadUrl)
            addAll(fallbackUrls)
            if (!fallbackUrl.isNullOrBlank()) {
                add(fallbackUrl)
            }
        }.distinct()

        var downloadedFile: File? = null
        var lastError: Throwable? = null

        for (url in urlsToTry) {
            try {
                Timber.d("Downloading APK from $url to ${tempFile.absolutePath}")
                downloadFile(url, tempFile) { bytesRead, totalBytes, progress ->
                    send(DownloadState.Downloading(bytesRead, totalBytes, progress))
                }

                if (tempFile.exists() && tempFile.length() > 0) {
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    if (tempFile.renameTo(targetFile)) {
                        downloadedFile = targetFile
                    } else {
                        // 重命名失败时做文件拷贝
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                        downloadedFile = targetFile
                    }
                    break
                }
            } catch (e: CancellationException) {
                Timber.i("Download cancelled by user")
                tempFile.delete()
                throw e
            } catch (e: Throwable) {
                Timber.w(e, "Failed to download APK from $url")
                tempFile.delete()
                lastError = e
            }
        }

        if (downloadedFile != null && downloadedFile.exists()) {
            send(DownloadState.Success(downloadedFile))
        } else {
            send(DownloadState.Error(
                message = lastError?.localizedMessage ?: "Failed to download APK",
                cause = lastError,
            ))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun downloadFile(
        url: String,
        destination: File,
        onProgress: suspend (bytesRead: Long, totalBytes: Long, progress: Float) -> Unit,
    ) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Trime-App-UpdateDownloader")
            .build()

        val call = client.newCall(request)
        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                throw IOException("HTTP error: ${response.code} ${response.message}")
            }

            val body = response.body ?: throw IOException("Empty response body")
            val totalBytes = body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesRead = 0L
                    var lastReportTime = 0L
                    var read: Int

                    while (input.read(buffer).also { read = it } != -1) {
                        if (!coroutineContext.isActive) {
                            throw CancellationException("Download job was cancelled")
                        }
                        output.write(buffer, 0, read)
                        bytesRead += read

                        val now = System.currentTimeMillis()
                        // 控制进度发送频率，每隔 80ms 或下载完成时更新
                        if (now - lastReportTime > 80 || bytesRead == totalBytes) {
                            lastReportTime = now
                            val progress = if (totalBytes > 0) {
                                (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            onProgress(bytesRead, totalBytes, progress)
                        }
                    }
                    output.flush()
                }
            }
        } finally {
            if (!call.isCanceled()) {
                call.cancel()
            }
        }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 8192

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }
}
