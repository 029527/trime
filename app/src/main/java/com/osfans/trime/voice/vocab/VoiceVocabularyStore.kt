/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.vocab

import com.osfans.trime.data.base.DataManager
import timber.log.Timber
import java.io.File

/**
 * 词库文件的读写。
 *
 * 文件位置是 **Rime 用户目录**下的 `voice_vocab.yaml`（[DataManager.userDataDir]），
 * 这样它跟着用户的配置仓库走 git 同步，电脑上也能批量编辑。
 *
 * **密钥绝不能放这里**：这个目录会被 push 到 git 远端。火山凭证走
 * [com.osfans.trime.voice.VoiceCredentialStore]（EncryptedSharedPreferences）。
 *
 * 重新加载策略：按文件的 `lastModified` + `length` 做缓存。
 * [load] 每次都 stat 一下文件，变了就重读 —— 所以每次录音前调一次 [load] 就够了，
 * 用户在电脑上改完同步过来立刻生效，不用重新部署。设置页那个「重新加载」按钮
 * 走 [reload]，无条件重读，用来确认文件确实被读到了。
 */
object VoiceVocabularyStore {

    val file: File get() = DataManager.userDataDir.resolve(VoiceVocabulary.FILE_NAME)

    private var cached: VoiceVocabulary = VoiceVocabulary.EMPTY
    private var cachedStamp: Pair<Long, Long>? = null

    /** 上次加载的结果说明，给设置页显示用。 */
    @Volatile
    var lastLoadSummary: String = ""
        private set

    @Synchronized
    fun load(): VoiceVocabulary {
        val f = file
        val stamp = if (f.isFile) f.lastModified() to f.length() else null
        if (stamp == cachedStamp) return cached
        return readAndCache(f, stamp)
    }

    /** 无条件重读。设置页的「重新加载词库」用它。 */
    @Synchronized
    fun reload(): VoiceVocabulary {
        val f = file
        return readAndCache(f, if (f.isFile) f.lastModified() to f.length() else null)
    }

    private fun readAndCache(f: File, stamp: Pair<Long, Long>?): VoiceVocabulary {
        cached = if (stamp == null) {
            lastLoadSummary = "未找到 ${f.absolutePath}"
            VoiceVocabulary.EMPTY
        } else {
            runCatching { VoiceVocabulary.parse(f.readText()) }
                .onFailure { Timber.w(it, "读取语音词库失败: ${f.absolutePath}") }
                .getOrDefault(VoiceVocabulary.EMPTY)
                .also {
                    lastLoadSummary = "映射词 ${it.rules.size} 条，热词 ${it.hotwords.size} 条，" +
                        "送识别 ${it.recognitionHotwords.size} 个"
                }
        }
        cachedStamp = stamp
        return cached
    }

    /** 文件不存在时写一份带注释的模板出去，给用户一个能照着改的起点。 */
    @Synchronized
    fun createTemplateIfAbsent(): Boolean {
        val f = file
        if (f.exists()) return false
        return runCatching {
            f.parentFile?.mkdirs()
            f.writeText(VoiceVocabulary.TEMPLATE)
            cachedStamp = null
            true
        }.onFailure { Timber.w(it, "写入语音词库模板失败") }.getOrDefault(false)
    }
}
