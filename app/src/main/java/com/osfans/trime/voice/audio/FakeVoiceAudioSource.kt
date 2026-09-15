/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.audio

import android.os.SystemClock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * A microphone for debug builds that "speaks" for a while and then stays silent, in the same
 * format and at the same pace as [VoiceAudioSource]. Lets the emulator, which has no voice,
 * exercise the automatic stop.
 */
object FakeVoiceAudioSource {
    fun pcmFlow(speechMs: Long): Flow<ByteArray> = flow {
        val signal = FakeSpeechSignal(speechMs)
        // timed by the clock, not by chunks: a busy main thread must not stretch the speech
        val startedAt = SystemClock.elapsedRealtime()
        while (currentCoroutineContext().isActive) {
            val chunk = ByteArray(VoiceAudioSource.CHUNK_BYTES)
            signal.fill(chunk, SystemClock.elapsedRealtime() - startedAt)
            emit(chunk)
            delay(VoiceAudioSource.CHUNK_MS.toLong())
        }
    }
}

/**
 * Room noise for [leadMs], then syllable-like bursts for [speechMs], then room noise for ever.
 * Deterministic for a given seed.
 */
class FakeSpeechSignal(
    private val speechMs: Long,
    private val leadMs: Long = 400,
    seed: Int = 7,
) {
    private val random = Random(seed)

    fun isSpeaking(elapsedMs: Long): Boolean = elapsedMs >= leadMs && elapsedMs < leadMs + speechMs

    /** Fills [chunk] (16-bit little-endian PCM) with the signal starting at [elapsedMs]. */
    fun fill(
        chunk: ByteArray,
        elapsedMs: Long,
    ) {
        val amplitude =
            if (isSpeaking(elapsedMs)) {
                SPEECH_BASE + SPEECH_SWING * abs(sin(2 * PI * elapsedMs / SYLLABLE_MS)).toFloat()
            } else {
                NOISE
            }
        var i = 0
        while (i + 1 < chunk.size) {
            val sample = (random.nextFloat() * 2 - 1) * amplitude
            val s = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            chunk[i] = (s and 0xFF).toByte()
            chunk[i + 1] = (s shr 8).toByte()
            i += 2
        }
    }

    private companion object {
        const val NOISE = 24f
        const val SPEECH_BASE = 2500f
        const val SPEECH_SWING = 6000f
        const val SYLLABLE_MS = 360.0
    }
}
