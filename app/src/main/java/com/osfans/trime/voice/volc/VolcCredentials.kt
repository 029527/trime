/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.volc

/** 火山的两种鉴权方式，对应控制台的新版 / 旧版。 */
sealed class VolcAuthentication {
    /** 新版控制台：一把 API Key。 */
    data class ApiKey(
        val apiKey: String,
    ) : VolcAuthentication()

    /** 旧版控制台：App ID + Access Token。 */
    data class Legacy(
        val appKey: String,
        val accessKey: String,
    ) : VolcAuthentication()
}

/**
 * 一次识别要用到的全部火山配置。
 *
 * 注意：这个对象里带着密钥，**只能**来自 [com.osfans.trime.voice.VoiceCredentialStore]
 * （EncryptedSharedPreferences），绝不能落到 Rime 用户目录里 —— 那个目录会被 push 到 git 远端。
 */
data class VolcConfig(
    val authentication: VolcAuthentication,
    val resourceId: String,
    val uid: String,
) {
    companion object {
        /** 豆包流式语音识别模型 2.0 */
        const val RESOURCE_ID_SEED_ASR = "volc.seedasr.sauc.duration"

        /** 豆包流式语音识别大模型 1.0 */
        const val RESOURCE_ID_BIG_ASR = "volc.bigasr.sauc.duration"

        const val AUTH_MODE_API_KEY = "apiKey"
        const val AUTH_MODE_LEGACY = "legacy"

        val RESOURCE_IDS = listOf(RESOURCE_ID_SEED_ASR, RESOURCE_ID_BIG_ASR)

        /**
         * 从一组字符串凭证里推出鉴权方式；缺字段就返回 null，
         * 调用方据此给出「凭证没填全」的提示。
         */
        fun authenticationOf(
            authMode: String,
            apiKey: String,
            appKey: String,
            accessKey: String,
        ): VolcAuthentication? = when (authMode) {
            AUTH_MODE_LEGACY -> {
                val app = appKey.trim()
                val access = accessKey.trim()
                if (app.isEmpty() || access.isEmpty()) null else VolcAuthentication.Legacy(app, access)
            }
            else -> apiKey.trim().takeIf { it.isNotEmpty() }?.let { VolcAuthentication.ApiKey(it) }
        }
    }
}
