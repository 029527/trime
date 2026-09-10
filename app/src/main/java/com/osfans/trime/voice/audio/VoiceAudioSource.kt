/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.osfans.trime.util.appContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import timber.log.Timber

/** 麦克风打不开时抛这个，[message] 直接给用户看。 */
class VoiceAudioException(
    message: String,
) : Exception(message)

/**
 * 16 kHz 单声道 PCM16 的麦克风采集，约 100 ms 一包。
 *
 * 做成冷流：调用方取消协程 → `finally` 里 stop + release。
 * 输入法进程里松手、切走、息屏都是取消这条协程，所以录音一定会跟着停。
 */
object VoiceAudioSource {

    const val SAMPLE_RATE = 16000
    const val CHUNK_MS = 100

    /** 100 ms 的 16 kHz 单声道 16 bit = 1600 采样 = 3200 字节。 */
    const val CHUNK_BYTES = SAMPLE_RATE / 1000 * CHUNK_MS * 2

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun pcmFlow(): Flow<ByteArray> = flow {
        if (!hasPermission()) throw VoiceAudioException("没有录音权限")

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) throw VoiceAudioException("这台设备不支持 16kHz 录音")

        // 缓冲开大一点（至少 4 包），避免推流卡一下就丢音频
        val bufferSize = maxOf(minBuffer, CHUNK_BYTES * 4)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw VoiceAudioException("麦克风被占用，打不开")
        }

        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw VoiceAudioException("麦克风启动失败")
            }
            val buffer = ByteArray(CHUNK_BYTES)
            while (currentCoroutineContext().isActive) {
                val read = record.read(buffer, 0, buffer.size)
                when {
                    read > 0 -> emit(buffer.copyOf(read))
                    read == 0 -> Unit
                    else -> throw VoiceAudioException("录音中断（错误码 $read）")
                }
            }
        } finally {
            runCatching { record.stop() }.onFailure { Timber.w(it, "停止录音失败") }
            runCatching { record.release() }.onFailure { Timber.w(it, "释放录音失败") }
        }
    }.flowOn(Dispatchers.IO)
}
