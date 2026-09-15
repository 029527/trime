/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice.dictation

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Energy-based voice activity detection, and the rule that ends dictation on its own.
 *
 * Feed one level per audio chunk ([pcm16RmsDbfs] of the chunk) with the time it was read.
 * Time is always passed in, never read from a clock, so tests drive it directly.
 *
 * - **Noise floor**: the quietest chunk of the last [Config.floorWindowChunks] chunks
 *   (minimum statistics). Speech has gaps between words, so the minimum stays at the room's
 *   noise even while someone talks; when the room gets louder for good, the minimum follows
 *   once the quiet chunks leave the window. The first [Config.calibrationMs] only collect
 *   the floor and never count as speech.
 * - **Speech**: a chunk louder than the floor by [Config.speechMarginDb], kept within
 *   [Config.minThresholdDb]..[Config.maxThresholdDb]. Speech starts after
 *   [Config.onsetChunks] loud chunks in a row, so the click of the key press is not a word;
 *   once started, every loud chunk counts.
 * - **Stop**: [Config.silenceTimeoutMs] after the last speech, or [Config.noSpeechTimeoutMs]
 *   after [start] when nobody spoke at all.
 *
 * Not thread-safe; one instance per dictation session, called from the audio thread.
 * Allocates nothing per chunk.
 */
class VoiceActivityDetector(
    private val config: Config = Config(),
) {
    data class Config(
        val calibrationMs: Long = 300,
        val silenceTimeoutMs: Long = 3000,
        val noSpeechTimeoutMs: Long = 3000,
        val speechMarginDb: Float = 10f,
        val minThresholdDb: Float = -55f,
        val maxThresholdDb: Float = -30f,
        val onsetChunks: Int = 2,
        val floorWindowChunks: Int = 50,
        /** Loudness above the threshold that fills [level] to 1. */
        val levelRangeDb: Float = 25f,
    )

    enum class Decision { CONTINUE, STOP_SILENCE, STOP_NO_SPEECH }

    private val window = FloatArray(config.floorWindowChunks)
    private var windowSize = 0
    private var windowNext = 0

    private var startedAt = 0L
    private var lastSpeechAt = 0L
    private var loudRun = 0

    var noiseFloorDb = SILENCE_DBFS
        private set

    var hasSpoken = false
        private set

    /** Smoothed loudness above the speech threshold, 0..1, for the UI. */
    var level = 0f
        private set

    val thresholdDb: Float
        get() = (noiseFloorDb + config.speechMarginDb).coerceIn(config.minThresholdDb, config.maxThresholdDb)

    fun start(nowMs: Long) {
        windowSize = 0
        windowNext = 0
        startedAt = nowMs
        lastSpeechAt = nowMs
        loudRun = 0
        noiseFloorDb = SILENCE_DBFS
        hasSpoken = false
        level = 0f
    }

    fun onAudio(
        levelDb: Float,
        nowMs: Long,
    ): Decision {
        pushFloorSample(levelDb)
        val threshold = thresholdDb
        val calibrating = nowMs - startedAt < config.calibrationMs
        if (!calibrating && levelDb >= threshold) {
            loudRun++
            if (hasSpoken || loudRun >= config.onsetChunks) {
                hasSpoken = true
                lastSpeechAt = nowMs
            }
        } else {
            loudRun = 0
        }
        val target = if (calibrating) 0f else ((levelDb - threshold) / config.levelRangeDb).coerceIn(0f, 1f)
        level += (target - level) * (if (target > level) ATTACK else RELEASE)
        return poll(nowMs)
    }

    /** The stop rule alone, for when no audio arrived to call [onAudio] with. */
    fun poll(nowMs: Long): Decision = when {
        hasSpoken && nowMs - lastSpeechAt >= config.silenceTimeoutMs -> Decision.STOP_SILENCE
        !hasSpoken && nowMs - startedAt >= config.noSpeechTimeoutMs -> Decision.STOP_NO_SPEECH
        else -> Decision.CONTINUE
    }

    private fun pushFloorSample(levelDb: Float) {
        window[windowNext] = levelDb
        windowNext = (windowNext + 1) % window.size
        if (windowSize < window.size) windowSize++
        var min = Float.MAX_VALUE
        for (i in 0 until windowSize) if (window[i] < min) min = window[i]
        noiseFloorDb = min
    }

    companion object {
        /** What an all-zero chunk measures as; `log10(0)` would be minus infinity. */
        const val SILENCE_DBFS = -96f

        private const val ATTACK = 0.6f
        private const val RELEASE = 0.3f
    }
}

/** RMS level of little-endian 16-bit mono PCM, in dB relative to full scale. */
fun pcm16RmsDbfs(
    pcm: ByteArray,
    length: Int = pcm.size,
): Float {
    val samples = length / 2
    if (samples == 0) return VoiceActivityDetector.SILENCE_DBFS
    var sum = 0.0
    var i = 0
    while (i + 1 < length) {
        val sample = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toDouble()
        sum += sample * sample
        i += 2
    }
    val rms = sqrt(sum / samples)
    if (rms < 1.0) return VoiceActivityDetector.SILENCE_DBFS
    return (20 * log10(rms / 32768.0)).toFloat().coerceAtLeast(VoiceActivityDetector.SILENCE_DBFS)
}
