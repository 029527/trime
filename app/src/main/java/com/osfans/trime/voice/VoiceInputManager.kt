/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice

import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.util.toast
import com.osfans.trime.voice.audio.VoiceAudioSource
import com.osfans.trime.voice.postprocess.TranscriptPipeline
import com.osfans.trime.voice.postprocess.VocabularyMappingProcessor
import com.osfans.trime.voice.provider.FakeVoiceRecognitionProvider
import com.osfans.trime.voice.provider.VoiceRecognitionEvent
import com.osfans.trime.voice.provider.VoiceRecognitionProvider
import com.osfans.trime.voice.provider.VolcVoiceRecognitionProvider
import com.osfans.trime.voice.vocab.VoiceVocabulary
import com.osfans.trime.voice.vocab.VoiceVocabularyStore
import com.osfans.trime.voice.volc.VolcConfig
import com.osfans.trime.voice.volc.VolcRequestOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import timber.log.Timber

/** 语音输入当前处于什么状态，键盘上的状态条按它画。 */
sealed interface VoiceInputState {
    data object Idle : VoiceInputState

    /**
     * 正在录。[text] 是当前的中间结果（已经过后处理），
     * [latched] 表示这是"点一下开始"的长录，需要再点一下才停。
     */
    data class Listening(
        val text: String,
        val latched: Boolean,
    ) : VoiceInputState

    /** 松手了，在等服务端的最终结果。 */
    data class Finishing(
        val text: String,
    ) : VoiceInputState

    data class Error(
        val message: String,
    ) : VoiceInputState
}

/**
 * 语音输入的总调度：按键 → 录音 → 识别 → 后处理 → 上屏。
 *
 * 和 Rime 的关系：**语音文本完全不经过 Rime**，直接走 `InputConnection`。
 * 开始录音前会先把 Rime 里没上屏的编码 commit 掉再清空，免得两边抢同一段 composing 区。
 *
 * 生命周期：挂在 [TrimeInputMethodService] 上。松手、切走、息屏、输入框结束
 * 都会走到 [abort] 或 [onRelease]，录音协程一取消，`AudioRecord` 就在 `finally` 里释放。
 */
class VoiceInputManager(
    private val service: TrimeInputMethodService,
) {
    private val prefs get() = AppPrefs.defaultInstance().voice

    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private var sessionJob: Job? = null
    private var pressStartAt = 0L
    private var suppressNextRelease = false
    private var latched = false

    /** 当前会话用的词库快照。每次开始录音前取一次，所以外部改了文件下一次录音就生效。 */
    private var vocabulary: VoiceVocabulary = VoiceVocabulary.EMPTY

    private val pipeline = TranscriptPipeline.of(VocabularyMappingProcessor { vocabulary })

    val isActive: Boolean get() = sessionJob?.isActive == true

    // MARK: - 按键入口

    /** 麦克风键按下。 */
    fun onPress() {
        if (isActive) {
            // 长录状态下再按一次 = 停
            suppressNextRelease = true
            finish()
            return
        }
        suppressNextRelease = false
        pressStartAt = SystemClock.elapsedRealtime()
        start()
    }

    /** 麦克风键松开（或者手指滑走被取消）。 */
    fun onRelease() {
        if (suppressNextRelease) {
            suppressNextRelease = false
            return
        }
        if (!isActive) return
        when (prefs.triggerMode.getValue()) {
            AppPrefs.Voice.TRIGGER_TOGGLE -> {
                // 只点按开关：松手不停，等下一次按
                latch()
            }
            AppPrefs.Voice.TRIGGER_HOLD -> finish()
            else -> {
                // both：按住说话；按得很短（轻点）就转成长录
                if (SystemClock.elapsedRealtime() - pressStartAt < TAP_THRESHOLD_MS) latch() else finish()
            }
        }
    }

    private fun latch() {
        if (!isActive || latched) return
        latched = true
        (_state.value as? VoiceInputState.Listening)?.let {
            _state.value = it.copy(latched = true)
        } ?: run { _state.value = VoiceInputState.Listening("", true) }
    }

    /** 正常收尾：停止送音频，等最终结果。 */
    fun finish() {
        if (!isActive) return
        stopAudio = true
        _state.value = VoiceInputState.Finishing(currentText)
    }

    /** 异常收尾：切走、息晕、输入框结束。已经出的字保留（commit 掉），不留半截 composing。 */
    fun abort() {
        val job = sessionJob ?: return
        sessionJob = null
        job.cancel()
        commitPending()
        reset()
    }

    // MARK: - 会话

    @Volatile
    private var stopAudio = false
    private var currentText = ""

    private fun start() {
        val provider = createProvider()
        if (!prefs.enabled.getValue()) {
            fail("语音输入还没打开，去「设置 → 语音输入」开一下")
            return
        }
        provider.unavailableReason()?.let {
            fail(it)
            return
        }
        if (provider.requiresAudio && !VoiceAudioSource.hasPermission()) {
            requestPermission()
            return
        }

        vocabulary = VoiceVocabularyStore.load()
        stopAudio = false
        latched = false
        currentText = ""
        _state.value = VoiceInputState.Listening("", false)

        sessionJob = service.lifecycleScope.launch {
            try {
                // 先把 Rime 里没上屏的编码结掉，免得跟语音的 composing 区打架
                service.postRimeJob {
                    if (statusCached.isComposing) commitComposition()
                    clearComposition()
                }.join()

                runSession(provider)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "语音输入会话异常")
                fail("语音输入出错了：${e.message ?: e.javaClass.simpleName}")
            } finally {
                sessionJob = null
            }
        }
    }

    private suspend fun runSession(provider: VoiceRecognitionProvider) {
        // 不需要麦克风的 provider（假识别）也要拿到一条流：这条流在"松手"时正常结束，
        // provider 靠它感知用户说完了，跟真的识别是同一条语义。
        val audio = if (provider.requiresAudio) gatedAudio() else idleGate()
        var lastUpdateAt = SystemClock.elapsedRealtime()
        val startedAt = lastUpdateAt
        val silenceTimeoutMs = prefs.silenceTimeout.getValue().coerceIn(2, 60) * 1000L
        val maxDurationMs = prefs.maxDuration.getValue().coerceIn(5, 600) * 1000L

        val watchdog = service.lifecycleScope.launch {
            var stopRequestedAt = 0L
            while (isActive) {
                delay(500)
                val now = SystemClock.elapsedRealtime()
                if (stopAudio) {
                    // 已经松手，在等服务端定稿。等太久就别等了，把已有的字落下来，
                    // 免得状态条一直挂在那儿、用户以为键盘卡死了。
                    if (stopRequestedAt == 0L) stopRequestedAt = now
                    if (now - stopRequestedAt > FINALIZE_TIMEOUT_MS) {
                        service.toast("识别超时，先把已有的字上屏了")
                        sessionJob?.cancel()
                        break
                    }
                    continue
                }
                if (now - startedAt > maxDurationMs) {
                    service.toast("录得太久了，先停了")
                    stopAudio = true
                    continue
                }
                if (now - lastUpdateAt > silenceTimeoutMs) {
                    service.toast("没听到声音，先停了")
                    stopAudio = true
                    continue
                }
            }
        }

        try {
            provider.recognize(audio).collect { event ->
                when (event) {
                    is VoiceRecognitionEvent.Partial -> {
                        lastUpdateAt = SystemClock.elapsedRealtime()
                        val text = pipeline.process(event.text, isFinal = false)
                        currentText = text
                        setComposing(text)
                        _state.value = if (stopAudio) {
                            VoiceInputState.Finishing(text)
                        } else {
                            VoiceInputState.Listening(text, latched)
                        }
                    }
                    is VoiceRecognitionEvent.Final -> {
                        val text = pipeline.process(event.text, isFinal = true)
                        currentText = text
                        commitText(text)
                        currentText = ""
                        _state.value = VoiceInputState.Idle
                    }
                    is VoiceRecognitionEvent.Failure -> {
                        Timber.w(event.cause, "语音识别失败: ${event.message}")
                        commitPending()
                        fail(event.message)
                    }
                    VoiceRecognitionEvent.Completed -> {
                        commitPending()
                        if (_state.value !is VoiceInputState.Error) _state.value = VoiceInputState.Idle
                    }
                }
            }
        } finally {
            watchdog.cancel()
            // 会话结束（含被取消）时兜底：绝不留下半截 composing 文本
            commitPending()
            latched = false
            stopAudio = false
        }
    }

    /**
     * 把 PCM 流包一层：[stopAudio] 一置位就**正常结束**（不是抛异常），
     * 这样 provider 那边能走到"补一个空的结束包"的分支，服务端才会给最终结果。
     * 上游取消后 `AudioRecord` 在 `finally` 里 stop + release。
     */
    private fun gatedAudio(): Flow<ByteArray> = VoiceAudioSource.pcmFlow().takeWhile { !stopAudio }

    /** 不开麦克风时的替身：什么都不吐，[stopAudio] 一置位就正常结束。 */
    private fun idleGate(): Flow<ByteArray> = flow {
        while (!stopAudio) delay(50)
    }

    // MARK: - 上屏

    private fun setComposing(text: String) {
        val ic = service.currentInputConnection ?: return
        ic.setComposingText(text, 1)
    }

    private fun commitText(text: String) {
        val ic = service.currentInputConnection ?: return
        if (text.isEmpty()) {
            ic.finishComposingText()
            return
        }
        ic.setComposingText(text, 1)
        ic.finishComposingText()
    }

    /** 有没上屏的 composing 就直接定下来，别让它被下一次输入吃掉。 */
    private fun commitPending() {
        if (currentText.isEmpty()) return
        service.currentInputConnection?.finishComposingText()
        currentText = ""
    }

    // MARK: - 杂项

    private fun createProvider(): VoiceRecognitionProvider = when (prefs.provider.getValue()) {
        AppPrefs.Voice.PROVIDER_FAKE -> FakeVoiceRecognitionProvider()
        else -> VolcVoiceRecognitionProvider(
            configProvider = ::volcConfigOrNull,
            optionsProvider = {
                // 热词这期只存不用，先不往请求里塞
                VolcRequestOptions(enablePunc = true)
            },
        )
    }

    private fun volcConfigOrNull(): VolcConfig? {
        val authentication = VolcConfig.authenticationOf(
            authMode = prefs.authMode.getValue(),
            apiKey = VoiceCredentialStore.get(VoiceCredentialStore.KEY_API_KEY),
            appKey = VoiceCredentialStore.get(VoiceCredentialStore.KEY_APP_KEY),
            accessKey = VoiceCredentialStore.get(VoiceCredentialStore.KEY_ACCESS_KEY),
        ) ?: return null
        return VolcConfig(
            authentication = authentication,
            resourceId = prefs.resourceId.getValue().ifEmpty { VolcConfig.RESOURCE_ID_SEED_ASR },
            uid = VoiceCredentialStore.uid(),
        )
    }

    private fun requestPermission() {
        // 输入法服务自己弹不了运行时权限，必须借一个 Activity。
        service.toast("语音输入需要录音权限")
        runCatching {
            service.startActivity(
                Intent(service, VoicePermissionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                },
            )
        }.onFailure { Timber.w(it, "拉起录音权限界面失败") }
        reset()
    }

    private fun fail(message: String) {
        _state.value = VoiceInputState.Error(message)
        service.toast(message)
        service.lifecycleScope.launch {
            delay(ERROR_DISPLAY_MS)
            if (_state.value is VoiceInputState.Error) _state.value = VoiceInputState.Idle
        }
    }

    private fun reset() {
        latched = false
        stopAudio = false
        currentText = ""
        _state.value = VoiceInputState.Idle
    }

    companion object {
        /** 按得比这短就当"轻点"，转成长录。 */
        const val TAP_THRESHOLD_MS = 400L

        private const val ERROR_DISPLAY_MS = 3000L

        /** 松手之后最多再等服务端多久给最终结果。 */
        private const val FINALIZE_TIMEOUT_MS = 8000L
    }
}
