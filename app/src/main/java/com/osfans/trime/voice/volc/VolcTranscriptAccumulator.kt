/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.volc

/**
 * 把火山每一帧响应折成「当前完整文本」的快照。
 *
 * 火山会修订之前给过的文本（`bigmodel_async` 尤其如此），所以服务端的新文本是
 * **替换**上一版假设，而不是追加。照抄 type4me 的 `VolcTranscriptAccumulator`。
 */
class VolcTranscriptAccumulator {
    private var lastCanonicalText = ""

    fun reset() {
        lastCanonicalText = ""
    }

    /** 空文本的中间帧不覆盖已有结果（火山偶尔会吐空帧）。 */
    fun apply(
        result: VolcASRResult,
        isFinal: Boolean,
    ): String {
        val resultText = result.text.takeIf { it.isNotBlank() } ?: ""
        val utteranceText = result.utterances.map { it.text }.filter { it.isNotEmpty() }.joinToString("")
        val incoming = resultText.ifEmpty { utteranceText }

        if (incoming.isEmpty() && !isFinal && lastCanonicalText.isNotEmpty()) {
            return lastCanonicalText
        }
        val canonical = incoming.ifEmpty { lastCanonicalText }
        if (canonical.isNotEmpty()) lastCanonicalText = canonical
        return canonical
    }
}
