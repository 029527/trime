/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.inputmethod.CursorAnchorInfo
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.BuildConfig
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ime.compose.voice.DictationIndicator
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.util.toast
import com.osfans.trime.voice.audio.FakeVoiceAudioSource
import com.osfans.trime.voice.audio.VoiceAudioSource
import com.osfans.trime.voice.dictation.DictationEffect
import com.osfans.trime.voice.dictation.DictationEvent
import com.osfans.trime.voice.dictation.DictationMachine
import com.osfans.trime.voice.dictation.DictationPillPlacement
import com.osfans.trime.voice.dictation.DictationState
import com.osfans.trime.voice.dictation.StopReason
import com.osfans.trime.voice.dictation.TranscriptSegmenter
import com.osfans.trime.voice.dictation.VoiceActivityDetector
import com.osfans.trime.voice.dictation.pcm16RmsDbfs
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.abs

/**
 * 语音听写的总调度，交互照 iOS：点麦克风键开始，再点一下结束，3 秒没声音自动结束。
 *
 * - 状态怎么走由纯逻辑的 [DictationMachine] 决定，这里只负责执行它给出的副作用
 *   （开关麦克风和识别、写 `InputConnection`）并把状态画到 [indicator] 上；
 * - 识别中的文字是输入框里的待定文字（`setComposingText`），定稿时 commit；
 *   **语音文本完全不经过 Rime**，开始前先把 Rime 里没上屏的编码按首选上屏再清空；
 * - 什么时候结束、已识别的字怎么处理，规则都写在 [DictationMachine] 的 KDoc 里。
 *
 * 挂在 [TrimeInputMethodService] 上，跟输入法进程同生命周期；所有入口都在主线程执行。
 */
class VoiceInputManager(
    private val service: TrimeInputMethodService,
) {
    private val prefs get() = AppPrefs.defaultInstance().voice

    /** 胶囊和麦克风键读的状态。 */
    val indicator = DictationIndicator()

    private val handler = Handler(Looper.getMainLooper())

    private var state: DictationState = DictationState.Idle

    /** [state] 不是空闲。按键可能在 Rime 线程上问，所以单独做成 volatile。 */
    @Volatile
    private var engaged = false

    /** 每开一次会话加一；会话里发出来的事件带着自己的编号，过期的直接丢。 */
    private var sessionId = 0
    private var sessionJob: Job? = null

    @Volatile
    private var stopAudio = false

    private var pendingProvider: VoiceRecognitionProvider? = null
    private var pendingAudio: Flow<ByteArray>? = null

    /** 当前会话用的词库快照。每次开始前取一次，所以外部改了文件下一次听写就生效。 */
    private var vocabulary: VoiceVocabulary = VoiceVocabulary.EMPTY
    private val pipeline = TranscriptPipeline.of(VocabularyMappingProcessor { vocabulary })
    private val segmenter = TranscriptSegmenter()

    private val matrixValues = FloatArray(9)
    private val mappedPoint = FloatArray(2)

    private val dismissError = Runnable { dispatch(DictationEvent.ErrorDismissed) }
    private val finalizeTimeout = Runnable { dispatch(DictationEvent.Failure(FINALIZE_TIMEOUT_MESSAGE)) }
    private val maxDurationReached = Runnable { dispatch(DictationEvent.Stop(StopReason.MAX_DURATION)) }

    private val simulateRecognition: Boolean
        get() = BuildConfig.DEBUG && prefs.debugSimulateRecognition.getValue()

    private val simulateMicrophone: Boolean
        get() = simulateRecognition && prefs.debugSimulateMicrophone.getValue()

    /** 麦克风开着，或者在等定稿。 */
    val isActive: Boolean
        get() = state is DictationState.Listening || state is DictationState.Finishing

    // MARK: - 入口

    /** 麦克风键：没在听就开始，正在听就结束（等定稿）。在等定稿时再点不做什么。 */
    fun toggle() = onMain {
        when (state) {
            is DictationState.Listening -> dispatch(DictationEvent.Stop(StopReason.USER))
            is DictationState.Finishing -> Unit
            else -> start()
        }
    }

    /**
     * 打断：点了别的键、键盘收起、输入框结束、服务销毁。已识别的字立刻上屏，错误提示收起。
     * 输入框结束时必须在 `InputConnection` 换掉之前调，所以在主线程上是同步执行的。
     */
    fun interrupt() {
        if (engaged) onMain { dispatch(DictationEvent.Interrupt) }
    }

    /** 用户把光标点到了别处（不在待定文字末尾），当作打断。 */
    fun onSelectionUpdate(
        selStart: Int,
        selEnd: Int,
        composingStart: Int,
        composingEnd: Int,
    ) {
        if (isActive && DictationMachine.cursorLeftComposition(selStart, selEnd, composingStart, composingEnd)) {
            dispatch(DictationEvent.Interrupt)
        }
    }

    /** 听写期间（以及显示错误时）输入框报来的光标位置，换成屏幕坐标交给胶囊。 */
    fun onCursorAnchorInfo(info: CursorAnchorInfo) {
        if (state == DictationState.Idle) return
        // 全屏（抽取）模式下 App 的输入框被盖住了，报来的位置没有意义，胶囊退回键盘上方
        if (service.isFullscreenMode) {
            indicator.clearCaret()
            return
        }
        var x = info.insertionMarkerHorizontal
        var top = info.insertionMarkerTop
        var bottom = info.insertionMarkerBottom
        val flags = info.insertionMarkerFlags
        val hidden = flags and CursorAnchorInfo.FLAG_HAS_INVISIBLE_REGION != 0 &&
            flags and CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION == 0
        if (x.isNaN() || hidden) {
            // 不报光标的编辑器可能还报待定文字每个字的位置：取最后一个字的右边
            val start = info.composingTextStart
            val length = info.composingText?.length ?: 0
            val bounds = if (start >= 0 && length > 0) info.getCharacterBounds(start + length - 1) else null
            if (bounds == null) {
                indicator.clearCaret()
                return
            }
            x = bounds.right
            top = bounds.top
            bottom = bounds.bottom
        }
        info.matrix.getValues(matrixValues)
        DictationPillPlacement.mapPoint(matrixValues, x, top, mappedPoint)
        val screenX = mappedPoint[0]
        val screenTop = mappedPoint[1]
        DictationPillPlacement.mapPoint(matrixValues, x, bottom, mappedPoint)
        indicator.setCaret(screenX, screenTop, mappedPoint[1])
    }

    // MARK: - 状态机

    private fun start() {
        // 开始前就报错时胶囊也要贴着光标：先要一次光标位置
        indicator.clearCaret()
        service.requestVoiceCursorOnce()
        if (!prefs.enabled.getValue()) {
            dispatch(DictationEvent.Failure("语音输入没打开，去「设置 → 语音输入」开启"))
            return
        }
        val provider = createProvider()
        provider.unavailableReason()?.let {
            dispatch(DictationEvent.Failure(it))
            return
        }
        val simulatedMic = simulateMicrophone
        if (!simulatedMic && !VoiceAudioSource.hasPermission()) {
            requestPermission()
            return
        }
        pendingProvider = provider
        pendingAudio =
            if (simulatedMic) {
                FakeVoiceAudioSource.pcmFlow(prefs.debugSimulatedSpeechSeconds.getValue().coerceIn(0, 60) * 1000L)
            } else {
                VoiceAudioSource.pcmFlow()
            }
        vocabulary = VoiceVocabularyStore.load()
        dispatch(DictationEvent.Start)
    }

    private fun dispatch(event: DictationEvent) {
        val transition = DictationMachine.reduce(state, event)
        if (transition.state::class != state::class) {
            // 只记状态名：识别出的文字不进日志
            Timber.d("dictation ${state::class.simpleName} -> ${transition.state::class.simpleName} on ${event::class.simpleName}")
        }
        state = transition.state
        engaged = state != DictationState.Idle
        transition.effects.forEach(::perform)
        render()
    }

    private fun perform(effect: DictationEffect) {
        when (effect) {
            DictationEffect.BeginSession -> beginSession()
            DictationEffect.StopAudio -> {
                stopAudio = true
                handler.removeCallbacks(maxDurationReached)
                // 关麦之后等服务端定稿。等太久就别等了，已有的字先落下来
                handler.postDelayed(finalizeTimeout, FINALIZE_TIMEOUT_MS)
            }
            DictationEffect.EndSession -> endSession()
            is DictationEffect.SetComposing -> service.currentInputConnection?.setComposingText(effect.text, 1)
            is DictationEffect.Commit -> service.currentInputConnection?.run {
                beginBatchEdit()
                if (effect.text.isNotEmpty()) setComposingText(effect.text, 1)
                finishComposingText()
                endBatchEdit()
            }
            DictationEffect.FinishComposing -> service.currentInputConnection?.finishComposingText()
            DictationEffect.ScheduleErrorDismiss -> {
                handler.removeCallbacks(dismissError)
                handler.postDelayed(dismissError, ERROR_DISPLAY_MS)
            }
        }
    }

    private fun render() {
        indicator.phase =
            when (val s = state) {
                DictationState.Idle -> DictationIndicator.Phase.HIDDEN
                is DictationState.Listening -> DictationIndicator.Phase.LISTENING
                is DictationState.Finishing -> DictationIndicator.Phase.FINISHING
                is DictationState.Failed -> {
                    indicator.message = s.message
                    DictationIndicator.Phase.ERROR
                }
            }
        if (state !is DictationState.Listening) indicator.level = 0f
    }

    // MARK: - 会话

    private fun beginSession() {
        val provider = pendingProvider ?: return
        val audio = pendingAudio ?: return
        pendingProvider = null
        pendingAudio = null
        val id = ++sessionId
        stopAudio = false
        segmenter.reset()
        handler.removeCallbacks(dismissError)
        indicator.clearCaret()
        service.setVoiceCursorMonitor(true)
        handler.postDelayed(maxDurationReached, prefs.maxDuration.getValue().coerceIn(10, 300) * 1000L)

        sessionJob =
            service.lifecycleScope.launch {
                try {
                    // 先把 Rime 里没上屏的编码按首选上屏，免得跟语音的待定文字抢同一段 composing 区
                    service.postRimeJob {
                        if (statusCached.isComposing) commitComposition()
                        clearComposition()
                    }.join()
                    provider.recognize(monitored(audio, id)).collect { if (id == sessionId) onRecognition(it) }
                    if (id == sessionId) dispatch(DictationEvent.Completed)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "语音听写会话异常")
                    if (id == sessionId) dispatch(DictationEvent.Failure("语音输入出错了：${e.message ?: e.javaClass.simpleName}"))
                }
            }
    }

    /**
     * 给音频流接上音量检测：每包算一次分贝喂给 [VoiceActivityDetector]，结果投回主线程。
     * [stopAudio] 一置位就**正常结束**这条流（不是抛异常），provider 才会补结束包、等最终结果。
     */
    private fun monitored(
        source: Flow<ByteArray>,
        id: Int,
    ): Flow<ByteArray> {
        val detector = VoiceActivityDetector()
        return source
            .onStart { detector.start(SystemClock.elapsedRealtime()) }
            .onEach { chunk ->
                val decision = detector.onAudio(pcm16RmsDbfs(chunk), SystemClock.elapsedRealtime())
                val level = detector.level
                handler.post { if (id == sessionId) onAudioLevel(level, decision) }
            }.takeWhile { !stopAudio }
    }

    private fun onAudioLevel(
        level: Float,
        decision: VoiceActivityDetector.Decision,
    ) {
        if (state !is DictationState.Listening) return
        // 小于一格的变化不写，免得麦克风键每包都重画
        if (abs(level - indicator.level) >= LEVEL_STEP || (level < LEVEL_STEP && indicator.level != 0f)) {
            indicator.level = if (level < LEVEL_STEP) 0f else level
        }
        when (decision) {
            VoiceActivityDetector.Decision.STOP_SILENCE -> dispatch(DictationEvent.Stop(StopReason.SILENCE))
            VoiceActivityDetector.Decision.STOP_NO_SPEECH -> dispatch(DictationEvent.Stop(StopReason.NO_SPEECH))
            VoiceActivityDetector.Decision.CONTINUE -> Unit
        }
    }

    private fun onRecognition(event: VoiceRecognitionEvent) {
        when (event) {
            // 中间结果也过词库映射：边说边出的字就已经是对的，定稿时不会突然跳变
            is VoiceRecognitionEvent.Partial ->
                dispatch(DictationEvent.Partial(pipeline.process(segmenter.partial(event.text), isFinal = false)))
            is VoiceRecognitionEvent.Final ->
                dispatch(DictationEvent.Final(pipeline.process(segmenter.final(event.text), isFinal = true)))
            is VoiceRecognitionEvent.Failure -> {
                Timber.w(event.cause, "语音识别失败: ${event.message}")
                dispatch(DictationEvent.Failure(event.message))
            }
            VoiceRecognitionEvent.Completed -> dispatch(DictationEvent.Completed)
        }
    }

    private fun endSession() {
        // 先让编号过期，被取消的协程里再冒出来的事件就都丢了
        sessionId++
        stopAudio = true
        sessionJob?.cancel()
        sessionJob = null
        handler.removeCallbacks(finalizeTimeout)
        handler.removeCallbacks(maxDurationReached)
        service.setVoiceCursorMonitor(false)
        // the caret stays: the pill fades out, or shows an error, where it was
    }

    // MARK: - 杂项

    private fun createProvider(): VoiceRecognitionProvider = if (simulateRecognition) {
        FakeVoiceRecognitionProvider()
    } else {
        VolcVoiceRecognitionProvider(
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
    }

    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post { block() }
    }

    companion object {
        private const val ERROR_DISPLAY_MS = 2500L

        /** 关麦之后最多再等服务端多久给最终结果。 */
        private const val FINALIZE_TIMEOUT_MS = 8000L

        private const val FINALIZE_TIMEOUT_MESSAGE = "识别超时，已保留识别出的文字"

        /** 音量变化小于这个就不更新界面。 */
        private const val LEVEL_STEP = 0.05f
    }
}
