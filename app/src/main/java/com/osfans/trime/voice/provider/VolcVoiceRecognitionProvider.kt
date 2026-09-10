/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.provider

import com.osfans.trime.voice.volc.VolcASRResult
import com.osfans.trime.voice.volc.VolcCompression
import com.osfans.trime.voice.volc.VolcConfig
import com.osfans.trime.voice.volc.VolcHeader
import com.osfans.trime.voice.volc.VolcMessageFlags
import com.osfans.trime.voice.volc.VolcMessageType
import com.osfans.trime.voice.volc.VolcProtocol
import com.osfans.trime.voice.volc.VolcProtocolException
import com.osfans.trime.voice.volc.VolcRequestOptions
import com.osfans.trime.voice.volc.VolcSerialization
import com.osfans.trime.voice.volc.VolcTranscriptAccumulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 火山引擎流式语音识别（`sauc/bigmodel_async`）。
 *
 * 一次会话：握手（凭证走 header）→ 发一帧 full client request → 连续发音频包 →
 * 最后发一个空的 last packet → 收中间结果/最终结果。
 * 编解码全在 [VolcProtocol] 里，这里只管连接和生命周期。
 */
class VolcVoiceRecognitionProvider(
    private val configProvider: () -> VolcConfig?,
    private val optionsProvider: () -> VolcRequestOptions = { VolcRequestOptions() },
) : VoiceRecognitionProvider {

    override val name = "volcano"

    override fun unavailableReason(): String? = if (configProvider() == null) "火山凭证还没填全，去「设置 → 语音输入」填一下" else null

    override fun recognize(audio: Flow<ByteArray>): Flow<VoiceRecognitionEvent> = callbackFlow {
        val config = configProvider()
        if (config == null) {
            trySend(
                VoiceRecognitionEvent.Failure(
                    "火山凭证还没填全，去「设置 → 语音输入」填一下",
                    recoverable = false,
                ),
            )
            trySend(VoiceRecognitionEvent.Completed)
            close()
            return@callbackFlow
        }

        val connectId = UUID.randomUUID().toString()
        val headers = VolcProtocol.authHeaders(config.authentication, config.resourceId, connectId)
        // 凭证里混进非法字符时 OkHttp 会直接抛，别让它变成一句看不懂的 Java 异常
        val request = runCatching {
            Request
                .Builder()
                .url(VolcProtocol.ENDPOINT)
                .apply { headers.forEach { (name, value) -> addHeader(name, value) } }
                .build()
        }.getOrElse {
            Timber.w(it, "构造火山请求失败")
            trySend(
                VoiceRecognitionEvent.Failure(
                    "凭证里有非法字符，去「设置 → 语音输入」重新填一次",
                    recoverable = false,
                    cause = it,
                ),
            )
            trySend(VoiceRecognitionEvent.Completed)
            close()
            return@callbackFlow
        }

        val client = OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            // WebSocket 不能按读超时算，靠 ping 保活
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

        val accumulator = VolcTranscriptAccumulator()
        var audioJob: Job? = null
        var sawFinal = false

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val payload = VolcProtocol.buildClientRequest(config.uid, options = optionsProvider())
                val message = VolcProtocol.encodeMessage(
                    header = VolcHeader(
                        messageType = VolcMessageType.FULL_CLIENT_REQUEST,
                        flags = VolcMessageFlags.NO_SEQUENCE,
                        serialization = VolcSerialization.JSON,
                        compression = VolcCompression.NONE,
                    ),
                    payload = payload,
                )
                if (!webSocket.send(message.toByteString())) {
                    trySend(VoiceRecognitionEvent.Failure("请求发不出去，网络断了？"))
                    close()
                    return
                }
                audioJob = launch {
                    runCatching {
                        audio.collect { chunk ->
                            webSocket.send(VolcProtocol.encodeAudioPacket(chunk, isLast = false).toByteString())
                        }
                        // 音频流正常走完（松手了），补一个空的结束包让服务端收尾
                        webSocket.send(VolcProtocol.encodeAudioPacket(ByteArray(0), isLast = true).toByteString())
                    }.onFailure {
                        Timber.w(it, "推送音频失败")
                        trySend(VoiceRecognitionEvent.Failure(it.message ?: "录音中断"))
                        close()
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                // server error 帧（0xF）既可能是真错，也可能是 bigmodel_async 的"会话结束"信号。
                // 靠帧里到底带没带东西区分，不能靠"发了多少音频"猜。
                if (VolcHeader.peekMessageType(data) == VolcMessageType.SERVER_ERROR.raw) {
                    val (code, message) = VolcProtocol.extractServerError(data)
                    if (code != null || message != null) {
                        trySend(
                            VoiceRecognitionEvent.Failure(
                                message?.let { "火山返回错误：$it" } ?: "火山返回错误（$code）",
                                recoverable = false,
                            ),
                        )
                    }
                    trySend(VoiceRecognitionEvent.Completed)
                    close()
                    return
                }

                runCatching { VolcProtocol.decodeServerResponse(data) }
                    .onSuccess { response ->
                        val isFinal = response.header.flags == VolcMessageFlags.ASYNC_FINAL
                        val text = accumulator.apply(response.result, isFinal)
                        if (isFinal) {
                            sawFinal = true
                            trySend(VoiceRecognitionEvent.Final(text))
                        } else if (text.isNotEmpty()) {
                            trySend(VoiceRecognitionEvent.Partial(text))
                        }
                    }.onFailure { error ->
                        val message = when (error) {
                            is VolcProtocolException.ServerError ->
                                error.serverMessage?.let { "火山返回错误：$it" } ?: "火山返回错误"
                            else -> "识别结果解析失败"
                        }
                        Timber.w(error, "解析火山响应失败")
                        trySend(
                            VoiceRecognitionEvent.Failure(
                                message,
                                recoverable = error !is VolcProtocolException.ServerError,
                                cause = error,
                            ),
                        )
                        trySend(VoiceRecognitionEvent.Completed)
                        close()
                    }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!sawFinal) {
                    val tail = accumulator.apply(VolcASRResult("", emptyList()), isFinal = true)
                    if (tail.isNotEmpty()) trySend(VoiceRecognitionEvent.Final(tail))
                }
                trySend(VoiceRecognitionEvent.Completed)
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Timber.w(t, "火山 WebSocket 失败")
                trySend(describeFailure(t, response))
                trySend(VoiceRecognitionEvent.Completed)
                close()
            }
        }

        val webSocket = client.newWebSocket(request, listener)

        awaitClose {
            audioJob?.cancel()
            webSocket.cancel()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }.buffer(Channel.UNLIMITED, BufferOverflow.SUSPEND)
        // 整条流跑在 IO 线程上：`awaitClose` 里的 `evictAll()` 会真的去关 socket，
        // 留在主线程就是 NetworkOnMainThreadException。事件照样投递回收集方（主线程）。
        .flowOn(Dispatchers.IO)

    /**
     * 握手被拒时把服务端的原话捞出来。
     *
     * 鉴权失败、额度用完这类问题，服务端是在 HTTP 响应体里说清楚的；
     * 只报一句"连接失败"等于把唯一有用的信息扔了。
     */
    private fun describeFailure(t: Throwable, response: Response?): VoiceRecognitionEvent.Failure {
        val code = response?.code
        if (code != null && code != 101) {
            val body = runCatching { response.body?.string() }.getOrNull().orEmpty()
            val detail = VolcProtocol.readableHttpError(body) ?: body.take(120).ifEmpty { null }
            val prefix = when (code) {
                401, 403 -> "鉴权失败（HTTP $code）"
                429 -> "被限流了（HTTP $code）"
                else -> "服务端拒绝了连接（HTTP $code）"
            }
            return VoiceRecognitionEvent.Failure(
                detail?.let { "$prefix：$it" } ?: prefix,
                recoverable = code == 429 || code >= 500,
                cause = t,
            )
        }
        return VoiceRecognitionEvent.Failure(
            "连不上火山：${t.message ?: t.javaClass.simpleName}",
            recoverable = true,
            cause = t,
        )
    }
}
