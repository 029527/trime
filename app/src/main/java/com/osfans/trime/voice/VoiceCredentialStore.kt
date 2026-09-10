/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.osfans.trime.util.appContext
import timber.log.Timber
import java.util.UUID

/**
 * 火山凭证的存放处。
 *
 * **为什么不放 Rime 用户目录**：那个目录（`DataManager.userDataDir`）是用户的配置仓库，
 * 会被 `git push` 到远端。密钥写进去等于公开泄露。所以凭证一律走
 * `EncryptedSharedPreferences`（androidx.security-crypto），落在应用私有目录里、
 * 用 Android Keystore 里的主密钥加密，既不进仓库也不进备份明文。
 *
 * 词库（`voice_vocab.yaml`）反过来 —— 那个**要**跟着仓库同步，所以放用户目录。
 * 两者不要弄混。
 */
object VoiceCredentialStore {

    private const val FILE_NAME = "voice_credentials"

    const val KEY_API_KEY = "volc_api_key"
    const val KEY_APP_KEY = "volc_app_key"
    const val KEY_ACCESS_KEY = "volc_access_key"
    private const val KEY_UID = "volc_uid"

    /** Keystore 坏掉时的兜底：留在内存里，至少这次会话能用，不至于把输入法弄崩。 */
    private val fallback = mutableMapOf<String, String>()

    private val prefs: SharedPreferences? by lazy { createPrefs(appContext) }

    private fun createPrefs(context: Context): SharedPreferences? {
        fun build(): SharedPreferences {
            val masterKey = MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
        return runCatching { build() }
            .recoverCatching {
                // 主密钥被换过（重装、恢复出厂）时旧文件解不开，扔掉重来。
                Timber.w(it, "语音凭证存储打不开，重建")
                context.deleteSharedPreferences(FILE_NAME)
                build()
            }.onFailure { Timber.e(it, "语音凭证存储不可用，退回内存") }
            .getOrNull()
    }

    fun get(key: String): String = prefs?.getString(key, null) ?: fallback[key] ?: ""

    /**
     * 只保留可打印 ASCII。
     *
     * 凭证是直接拼进 HTTP header 的，粘贴时带进来的换行、不间断空格、
     * 甚至输入法自己的软光标字符都会让 OkHttp 直接抛
     * `Unexpected char ... in X-Api-Key value`，把一次录音变成一个看不懂的崩溃。
     * 与其在发请求时才炸，不如存的时候就洗干净。
     */
    private fun sanitize(value: String): String = value.filter { it.code in 0x20..0x7E }.trim()

    fun put(key: String, value: String) {
        val trimmed = sanitize(value)
        val store = prefs
        if (store == null) {
            if (trimmed.isEmpty()) fallback.remove(key) else fallback[key] = trimmed
            return
        }
        store.edit().apply {
            if (trimmed.isEmpty()) remove(key) else putString(key, trimmed)
        }.apply()
    }

    /** 火山要求每次会话带一个稳定的用户标识，第一次用的时候生成并记住。 */
    fun uid(): String {
        get(KEY_UID).takeIf { it.isNotEmpty() }?.let { return it }
        val generated = UUID.randomUUID().toString()
        put(KEY_UID, generated)
        return generated
    }

    fun clearAll() {
        listOf(KEY_API_KEY, KEY_APP_KEY, KEY_ACCESS_KEY).forEach { put(it, "") }
    }

    /** 给设置页显示用：只说填没填、有多长，绝不回显原文。 */
    fun masked(key: String): String {
        val value = get(key)
        return when {
            value.isEmpty() -> ""
            value.length <= 4 -> "••••"
            else -> value.take(2) + "••••••" + value.takeLast(2)
        }
    }
}
