/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.llm

import android.os.SystemClock
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 最终结果的纠错。
 *
 * 只管把文字发出去、把模型的原始输出拿回来；校验、兜底、超时都在
 * [com.osfans.trime.voice.postprocess.TranscriptPipeline.correct] 里统一做，
 * 这样真服务和 [FakeTranscriptCorrector] 走的是同一条路。
 */
interface TranscriptCorrector {
    val name: String

    /**
     * @return 模型的原始输出，还没校验
     * @throws LlmCorrectionException 请求失败，消息是给用户看的短句
     * @throws kotlinx.coroutines.CancellationException 被取消（用户按了别的键），请求随之取消
     */
    suspend fun correct(text: String): String
}

/**
 * OpenAI 兼容的 Chat Completions，非流式：纠错结果要整段替换，流式拿到半截也没法用。
 *
 * **日志和异常里绝不出现密钥、请求头和请求体**：失败只带状态码和服务端给的错误说明（先抹掉 key）。
 */
class OpenAiCompatibleCorrector(
    private val config: LlmCorrectionConfig,
    private val hotwords: List<String>,
    private val client: OkHttpClient = sharedClient,
) : TranscriptCorrector {
    override val name = "openai-compatible"

    override suspend fun correct(text: String): String {
        val endpoint = ChatCompletionProtocol.endpoint(config.baseUrl)
            ?: throw LlmCorrectionException("纠错服务地址不对")
        val body = ChatCompletionProtocol.buildRequestBody(
            preset = config.preset,
            model = config.model,
            systemPrompt = config.systemPrompt,
            userMessage = ChatCompletionProtocol.buildUserMessage(text, hotwords),
            maxTokens = ChatCompletionProtocol.maxTokensFor(text),
        )
        // OkHttp 校验 header 时抛的异常不带 Authorization 的值，但这里还是不挂 cause，一点风险都不留
        val request = runCatching {
            Request
                .Builder()
                .url(endpoint)
                .header("Authorization", "Bearer ${config.apiKey}")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        }.getOrElse { throw LlmCorrectionException("纠错的 API Key 或地址里有非法字符") }

        val (code, responseBody) = client.newCall(request).await()
        if (code !in 200..299) throw ChatCompletionProtocol.httpError(code, responseBody, config.apiKey)
        return ChatCompletionProtocol.parseContent(responseBody)
    }

    private suspend fun Call.await(): Pair<Int, String> = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    continuation.resumeWithException(describe(e))
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    runCatching { response.use { it.code to it.body?.string().orEmpty() } }
                        .onSuccess { continuation.resume(it) }
                        .onFailure { continuation.resumeWithException(describe(it)) }
                }
            },
        )
    }

    private fun describe(e: Throwable): LlmCorrectionException = when (e) {
        is UnknownHostException -> LlmCorrectionException("连不上纠错服务")
        is InterruptedIOException -> LlmCorrectionException("纠错超时")
        else -> LlmCorrectionException("连不上纠错服务", e.javaClass.simpleName)
    }

    companion object {
        /** 一次纠错最多等多久，从发请求算到读完响应。 */
        const val TIMEOUT_MS = 6000L

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** 共用一个客户端：连接池能复用，第二次纠错省掉 TLS 握手。 */
        private val sharedClient: OkHttpClient by lazy {
            OkHttpClient
                .Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()
        }

        /**
         * 设置页的「测试连接」：真发一个很短的请求，成功返回耗时（毫秒）。
         * 只在用户点按钮时调用。
         */
        suspend fun testConnection(config: LlmCorrectionConfig): Long {
            config.missingReason()?.let { throw LlmCorrectionException(it) }
            val start = SystemClock.elapsedRealtime()
            OpenAiCompatibleCorrector(config, emptyList()).correct("今天天气怎么样")
            return SystemClock.elapsedRealtime() - start
        }
    }
}

/**
 * 假纠错：不连网络，等一会儿给出固定的「纠正」，用来在模拟器上看纠错中、替换、打断和失败几种状态。
 *
 * 只在 debug 包的「开发者 → 模拟纠错」打开时使用。
 */
class FakeTranscriptCorrector(
    private val outcome: Outcome,
    private val delayMs: Long = DELAY_MS,
) : TranscriptCorrector {
    enum class Outcome(
        val id: String,
    ) {
        SUCCESS("success"),
        FAILURE("failure"),

        /** 一直不回，等管道的超时。 */
        TIMEOUT("timeout"),
        ;

        companion object {
            fun of(id: String) = entries.firstOrNull { it.id == id } ?: SUCCESS
        }
    }

    override val name = "fake"

    override suspend fun correct(text: String): String {
        if (outcome == Outcome.TIMEOUT) awaitCancellation()
        delay(delayMs)
        if (outcome == Outcome.FAILURE) throw LlmCorrectionException("模拟纠错失败")
        return polish(text)
    }

    companion object {
        const val DELAY_MS = 1200L

        /**
         * 一眼能看出变化、又能过校验的「纠正」：汉字和字母数字之间补空格，句末补句号。
         * 模拟识别的「我用 Qwen3.5 和Claude写代码」会变成「我用 Qwen3.5 和 Claude 写代码。」。
         */
        fun polish(text: String): String {
            val spaced = text
                .replace(Regex("(\\p{IsHan})([A-Za-z0-9])"), "$1 $2")
                .replace(Regex("([A-Za-z0-9])(\\p{IsHan})"), "$1 $2")
                .trim()
            return if (spaced.isEmpty() || spaced.last() in "。！？.!?") spaced else "$spaced。"
        }
    }
}
