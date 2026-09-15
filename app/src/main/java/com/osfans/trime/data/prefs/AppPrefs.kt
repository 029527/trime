/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.Keep
import com.osfans.trime.R
import com.osfans.trime.core.Rime
import com.osfans.trime.data.sync.DataStorageMode
import com.osfans.trime.ime.candidates.popup.PopupCandidatesLayout
import com.osfans.trime.ime.candidates.popup.PopupCandidatesMode
import com.osfans.trime.ime.composition.PopupPosition
import com.osfans.trime.ime.core.InlinePreeditMode
import com.osfans.trime.util.InputMethodUtils
import com.osfans.trime.util.appContext
import java.lang.ref.WeakReference

/**
 * Helper class for an organized access to the shared preferences.
 */
class AppPrefs(
    private val shared: SharedPreferences,
) {
    private val applicationContext: WeakReference<Context> = WeakReference(appContext)

    private val providers = mutableListOf<PreferenceDelegateProvider>()

    fun <T : PreferenceDelegateProvider> registerProvider(providerF: (SharedPreferences) -> T): T {
        val provider = providerF(shared)
        providers.add(provider)
        return provider
    }

    private fun <T : PreferenceDelegateProvider> T.register() = this.apply {
        registerProvider { this }
    }

    val internal = Internal(shared)
    val general = General(shared).register()
    val profile = Profile(shared).register()
    val keyboard = Keyboard(shared).register()
    val candidates = Candidates(shared).register()
    val clipboard = Clipboard(shared).register()
    val advanced = Advanced(shared).register()
    val voice = Voice(shared).register()

    @Keep
    private val onSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null) return@OnSharedPreferenceChangeListener
            providers.forEach {
                it.notifyChange(key)
            }
        }

    companion object {
        private var defaultInstance: AppPrefs? = null

        fun initDefault(sharedPreferences: SharedPreferences): AppPrefs {
            val instance = AppPrefs(sharedPreferences)
            defaultInstance = instance
            sharedPreferences.registerOnSharedPreferenceChangeListener(
                defaultInstance().onSharedPreferenceChangeListener,
            )
            return instance
        }

        fun defaultInstance(): AppPrefs = defaultInstance
            ?: throw UninitializedPropertyAccessException(
                """
                    Default preferences not initialized! Make sure to call initDefault()
                    before accessing the default preferences.
                """.trimIndent(),
            )
    }

    class Internal(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared) {
        companion object {
            const val PID = "general__pid"
        }

        val pid = int(PID, 0)
    }

    class General(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared, R.string.general) {
        companion object {
            const val INLINE_PREEDIT_MODE = "inline_preedit_mode"
            const val ASCII_SWITCH_TIPS = "ascii_switch_tips"
            const val INLINE_SUGGESTIONS = "inline_suggestions"
            const val PREFERRED_VOICE_INPUT = "preferred_voice_input"
        }

        // the pinyin being typed goes into the app's text field, like iOS; the keyboard shows candidates only
        val inlinePreeditMode = enum(R.string.inline_preedit_mode, INLINE_PREEDIT_MODE, InlinePreeditMode.COMPOSING_TEXT)
        val asciiSwitchTips = switch(R.string.ascii_switch_tips, ASCII_SWITCH_TIPS, true)
        val inlineSuggestions = switch(R.string.inline_suggestions, INLINE_SUGGESTIONS, true)

        val preferredVoiceInput = list(
            R.string.preferred_voice_input,
            PREFERRED_VOICE_INPUT,
            "",
            { InputMethodUtils.voiceInputMethods().map { it.first.packageName } },
            { ctx ->
                InputMethodUtils.voiceInputMethods().map { it.first.loadLabel(ctx.packageManager) }
            },
        )
    }

    /**
     *  Wrapper class of keyboard settings.
     */
    class Keyboard(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared, R.string.virtual_keyboard) {
        companion object {
            const val LANDSCAPE_MODE = "keyboard_landscape_mode"
            const val SPLIT_SPACE_PERCENT = "keyboard_split_space"
            const val LANDSCAPE_SCHEMA = "keyboard_landscape_schema"
            const val LANDSCAPE_FULLSCREEN = "keyboard_landscape_fullscreen"

            const val USE_SOFT_CURSOR = "use_soft_cursor"
            const val LANDSCAPE_FLOATING = "keyboard_landscape_floating"
            const val LANDSCAPE_FLOATING_SIZE = "keyboard_landscape_floating_size"
            const val LANDSCAPE_FLOATING_WIDTH = "keyboard_landscape_floating_width"
            const val LANDSCAPE_FLOATING_HEIGHT = "keyboard_landscape_floating_height"
            const val LANDSCAPE_FLOATING_MARGIN = "keyboard_landscape_floating_margin"
            const val LANDSCAPE_FLOATING_OFFSET_X = "keyboard_landscape_floating_offset_x"
            const val LANDSCAPE_FLOATING_OFFSET_Y = "keyboard_landscape_floating_offset_y"
            const val HIDE_INPUT_BAR = "hide_input_bar"
            const val HIDE_KEY_SYMBOL = "hide_key_symbol"
            const val HIDE_KEY_HINT = "hide_key_hint"

            const val SOUND_ON_KEYPRESS = "sound_on_keypress"
            const val KEY_SOUND_VOLUME = "sound_volume"
            const val USE_CUSTOM_SOUND_EFFECT = "custom_sound_effect_enabled"
            const val CUSTOM_SOUND_EFFECT = "custom_sound_effect_name"
            const val VIBRATE_ON_KEY_PRESS = "vibrate_on_key_press"
            const val VIBRATION_EFFECT = "vibration_effect"
            const val VIBRATE_ON_KEY_RELEASE = "vibrate_on_key_release"
            const val VIBRATE_ON_KEY_REPEAT = "vibrate_on_key_repeat"
            const val VIBRATION_DURATION = "vibration_duration"
            const val VIBRATION_AMPLITUDE = "vibration_amplitude"
            const val SPEAK_ON_KEYPRESS = "speak_on_keypress"
            const val SPEAK_ON_COMMIT = "speak_on_commit"
            const val POPUP_ON_KEY_PRESS = "show_key_popup"
            const val EXPAND_KEYPRESS_AREA = "expand_keypress_area"
            const val LAYOUT_IN_DISPLAY_CUTOUT = "layout_in_display_cutout"
            const val SWIPE_TRAVEL = "key_swipe_travel"
            const val SWIPE_VELOCITY = "key_swipe_velocity"
            const val LONG_PRESS_TIMEOUT = "key_long_press_timeout"
            const val REPEAT_INTERVAL = "key_repeat_interval"
            const val DOUBLE_TAP_TIMEOUT = "key_double_tap_timeout"
            const val SLIDE_STEP_SIZE = "key_slide_step_size"

            const val HOOK_CTRL_A = "hook_ctrl_a"
            const val HOOK_CTRL_CV = "hook_ctrl_cv"
            const val HOOK_CTRL_LR = "hook_ctrl_lr"
            const val HOOK_CTRL_ZY = "hook_ctrl_zy"
            const val HOOK_SHIFT_SPACE = "hook_shift_space"
            const val HOOK_SHIFT_NUM = "hook_shift_num"
            const val HOOK_SHIFT_SYMBOL = "hook_shift_symbol"
            const val HOOK_SHIFT_ARROW = "hook_shift_arrow"
        }

        enum class LandscapeMode(override val stringRes: Int) : PreferenceDelegateEnum {
            NEVER(R.string.never),
            LANDSCAPE(R.string.landscape_only),
            WIDE(R.string.wide_or_landscape),
            ALWAYS(R.string.always),
        }

        val landscapeMode = enum(R.string.enable_landscape_mode, LANDSCAPE_MODE, LandscapeMode.NEVER)
        val splitSpacePercent = int(
            R.string.split_space_percent,
            SPLIT_SPACE_PERCENT,
            100,
            0,
            200,
            "%",
        )

        /** Schema to switch to in landscape; empty means "same as portrait". */
        val landscapeSchema = list(
            R.string.landscape_schema,
            LANDSCAPE_SCHEMA,
            "",
            { listOf("") + enabledSchemaList().map { it.id } },
            { ctx -> listOf(ctx.getString(R.string.landscape_schema_same_as_portrait)) + enabledSchemaList().map { it.name.ifEmpty { it.id } } },
        )

        private fun enabledSchemaList() = runCatching { Rime.getSelectedRimeSchemaList().toList() }.getOrDefault(emptyList())

        /**
         * In landscape, take over the whole screen (the system's fullscreen/extract mode) and
         * draw a text box above the keyboard, so the text is not hidden behind the keyboard.
         * Off for the floating keyboard, and for editors that ask not to be extracted.
         */
        val landscapeFullscreen = switch(
            R.string.landscape_fullscreen,
            LANDSCAPE_FULLSCREEN,
            true,
            R.string.landscape_fullscreen_summary,
        )

        /** In landscape, show the keyboard as a small window at the bottom end instead of full width. */
        val landscapeFloating = switch(R.string.landscape_floating, LANDSCAPE_FLOATING, false)

        /**
         * Size presets for the floating keyboard: width (percent of the screen) and a
         * scale applied to the keyboard height and key text together, so the keys keep
         * their proportions. [CUSTOM] uses [landscapeFloatingWidth] and [landscapeFloatingHeight].
         */
        enum class LandscapeFloatingSize(
            override val stringRes: Int,
            val widthPercent: Int,
            val scale: Float,
        ) : PreferenceDelegateEnum {
            SMALL(R.string.landscape_floating_size_small, 32, 0.8f),
            MEDIUM(R.string.landscape_floating_size_medium, 40, 1f),
            LARGE(R.string.landscape_floating_size_large, 50, 1.25f),
            XLARGE(R.string.landscape_floating_size_xlarge, 60, 1.5f),
            CUSTOM(R.string.landscape_floating_size_custom, 0, 0f),
        }

        val landscapeFloatingSize = enum(R.string.landscape_floating_size, LANDSCAPE_FLOATING_SIZE, LandscapeFloatingSize.MEDIUM)
        val landscapeFloatingWidth = int(
            R.string.landscape_floating_width,
            LANDSCAPE_FLOATING_WIDTH,
            40,
            25,
            80,
            "%",
        ) { landscapeFloatingSize.getValue() == LandscapeFloatingSize.CUSTOM }
        val landscapeFloatingHeight = int(
            R.string.landscape_floating_height,
            LANDSCAPE_FLOATING_HEIGHT,
            100,
            50,
            200,
            "%",
        ) { landscapeFloatingSize.getValue() == LandscapeFloatingSize.CUSTOM }

        /** Width of the floating keyboard as a percent of the screen, per preset. */
        fun floatingWidthPercent(): Int {
            val size = landscapeFloatingSize.getValue()
            return if (size == LandscapeFloatingSize.CUSTOM) landscapeFloatingWidth.getValue() else size.widthPercent
        }

        /** Scale for the floating keyboard's height and key text, per preset. */
        fun floatingScale(): Float {
            val size = landscapeFloatingSize.getValue()
            return if (size == LandscapeFloatingSize.CUSTOM) landscapeFloatingHeight.getValue() / 100f else size.scale
        }
        val landscapeFloatingMargin = int(
            R.string.landscape_floating_margin,
            LANDSCAPE_FLOATING_MARGIN,
            8,
            0,
            64,
            "dp",
        )

        /** Where the user dragged the floating keyboard to, in dp from its default bottom-end spot (≤ 0). */
        val landscapeFloatingOffsetX = int(LANDSCAPE_FLOATING_OFFSET_X, 0)
        val landscapeFloatingOffsetY = int(LANDSCAPE_FLOATING_OFFSET_Y, 0)

        val useSoftCursor = switch(R.string.use_soft_cursor, USE_SOFT_CURSOR, true)

        val hideInputBar = switch(R.string.hide_input_bar, HIDE_INPUT_BAR, false)
        val hideKeySymbol = switch(R.string.hide_key_symbol, HIDE_KEY_SYMBOL, false)
        val hideKeyHint = switch(R.string.hide_key_hint, HIDE_KEY_HINT, false)

        val soundOnKeyPress = switch(R.string.sound_on_keypress, SOUND_ON_KEYPRESS, false)
        val soundVolume = int(
            R.string.sound_volume,
            KEY_SOUND_VOLUME,
            10,
            0,
            100,
            "%",
            defaultLabel = R.string.system_default,
        ) { soundOnKeyPress.getValue() }

        val useCustomSoundEffect = switch(
            R.string.custom_sound_effect_enabled,
            USE_CUSTOM_SOUND_EFFECT,
            false,
        ) { soundOnKeyPress.getValue() }
        val customSoundEffect = string(
            R.string.custom_sound_effect_name,
            CUSTOM_SOUND_EFFECT,
            "",
        ) { soundOnKeyPress.getValue() && useCustomSoundEffect.getValue() }

        val vibrateOnKeyPress = switch(R.string.vibrate_on_key_press, VIBRATE_ON_KEY_PRESS, false)
        val vibrateOnKeyRelease = switch(
            R.string.vibrate_on_key_release,
            VIBRATE_ON_KEY_RELEASE,
            false,
        ) { vibrateOnKeyPress.getValue() }

        val vibrateOnKeyRepeat = switch(
            R.string.vibrate_on_key_repeat,
            VIBRATE_ON_KEY_REPEAT,
            false,
        ) { vibrateOnKeyPress.getValue() }

        /**
         * How a key press vibration is produced. The predefined effects and the composition
         * primitives use waveforms tuned by the device vendor for its motor, which give the
         * crisp "click" of a linear motor; a plain one-shot of N ms feels like a buzz on those.
         */
        enum class VibrationEffectType(override val stringRes: Int) : PreferenceDelegateEnum {
            SYSTEM(R.string.vibration_effect_system),
            CLICK(R.string.vibration_effect_click),
            TICK(R.string.vibration_effect_tick),
            HEAVY_CLICK(R.string.vibration_effect_heavy_click),
            PRIMITIVE_CLICK(R.string.vibration_effect_primitive_click),
            PRIMITIVE_TICK(R.string.vibration_effect_primitive_tick),
            CUSTOM(R.string.vibration_effect_custom),
        }

        val vibrationEffect = enum(R.string.vibration_effect, VIBRATION_EFFECT, VibrationEffectType.CLICK) {
            vibrateOnKeyPress.getValue()
        }

        val vibrationDuration = int(
            R.string.vibration_duration,
            VIBRATION_DURATION,
            0,
            0,
            100,
            "ms",
            defaultLabel = R.string.system_default,
        ) { vibrateOnKeyPress.getValue() && vibrationEffect.getValue() == VibrationEffectType.CUSTOM }

        val vibrationAmplitude = int(
            R.string.vibration_amplitude,
            VIBRATION_AMPLITUDE,
            0,
            0,
            255,
            defaultLabel = R.string.system_default,
        ) {
            vibrateOnKeyPress.getValue() &&
                vibrationEffect.getValue() in
                setOf(VibrationEffectType.CUSTOM, VibrationEffectType.PRIMITIVE_CLICK, VibrationEffectType.PRIMITIVE_TICK)
        }

        val speakOnKeyPress = switch(R.string.speak_on_keypress, SPEAK_ON_KEYPRESS, false)
        val speakOnCommit = switch(R.string.speak_on_commit, SPEAK_ON_COMMIT, false)
        val popupOnKeyPress = switch(R.string.popup_on_key_press, POPUP_ON_KEY_PRESS, true)
        val expandKeypressArea = switch(R.string.expand_keypress_area_to_edge, EXPAND_KEYPRESS_AREA, false)
        val layoutInDisplayCutout = switch(R.string.layout_in_display_cutout, LAYOUT_IN_DISPLAY_CUTOUT, false)
        val swipeTravel = int(
            R.string.key_swipe_travel,
            SWIPE_TRAVEL,
            60,
            0,
            400,
            "dp",
            10,
            R.string.disable,
            useMinAsDefault = true,
        )

        val swipeVelocity = int(
            R.string.key_swipe_velocity,
            SWIPE_VELOCITY,
            0,
            0,
            10000,
            "dp/s",
            100,
            R.string.disable,
        )

        val longPressTimeout = int(
            R.string.key_long_press_timeout,
            LONG_PRESS_TIMEOUT,
            300,
            100,
            1000,
            "ms",
            10,
        )

        val repeatInterval = int(
            R.string.key_repeat_interval,
            REPEAT_INTERVAL,
            30,
            10,
            100,
            "ms",
            10,
        )

        val doubleTapTimeout = int(
            R.string.key_double_tap_timeout,
            DOUBLE_TAP_TIMEOUT,
            300,
            100,
            1000,
            "ms",
            10,
        )

        val slideStepSize = int(
            R.string.key_slide_step_size,
            SLIDE_STEP_SIZE,
            24,
            1,
            100,
            "dp",
        )

        val hookCtrlA = switch(R.string.hook_ctrl_a, HOOK_CTRL_A, false)
        val hookCtrlCV = switch(R.string.hook_ctrl_cv, HOOK_CTRL_CV, false)
        val hookCtrlLR = switch(R.string.hook_ctrl_lr, HOOK_CTRL_LR, false)
        val hookCtrlZY = switch(R.string.hook_ctrl_zy, HOOK_CTRL_ZY, false)
        val hookShiftSpace = switch(R.string.hook_shift_space, HOOK_SHIFT_SPACE, false)
        val hookShiftNum = switch(R.string.hook_shift_num, HOOK_SHIFT_NUM, false)
        val hookShiftSymbol = switch(R.string.hook_shift_symbol, HOOK_SHIFT_SYMBOL, false)
        val hookShiftArrow = switch(R.string.hook_shift_arrow, HOOK_SHIFT_ARROW, true)
    }

    class Candidates(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared, R.string.candidates_window) {
        companion object {
            const val MODE = "show_candidates_window"
            const val LAYOUT = "candidates_layout"
            const val POSITION = "candidates_window_position"
        }

        val mode = enum(R.string.show_candidates_window, MODE, PopupCandidatesMode.DISABLED)
        val layout = enum(R.string.candidates_layout, LAYOUT, PopupCandidatesLayout.AUTOMATIC)
        val position = enum(R.string.candidates_window_position, POSITION, PopupPosition.BOTTOM_LEFT)
    }

    /**
     *  Wrapper class of profile settings.
     */
    class Profile(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared) {

        companion object {
            const val EXTERNAL_RIME_TREE_URI = "profile_external_rime_tree_uri"
            const val EXTERNAL_RIME_DISPLAY_NAME = "profile_external_rime_display_name"
            const val DATA_STORAGE_MODE = "profile_data_storage_mode"
            const val PERIODIC_BACKGROUND_SYNC = "periodic_background_sync"
            const val PERIODIC_BACKGROUND_SYNC_INTERVAL = "periodic_background_sync_interval"
            const val LAST_BACKGROUND_SYNC_STATUS = "last_background_sync_status"
            const val LAST_BACKGROUND_SYNC_TIME = "last_background_sync_time"
            const val USER_DB_MIGRATED = "profile_user_db_migrated"
            const val GIT_SYNC_ENABLED = "profile_git_sync_enabled"
            const val GIT_REPO_URL = "profile_git_repo_url"
            const val GIT_BRANCH = "profile_git_branch"
            const val GIT_USERNAME = "profile_git_username"
            const val GIT_TOKEN = "profile_git_token"
            const val GIT_LAST_COMMIT = "profile_git_last_commit"
            const val GIT_LAST_SYNC_TIME = "profile_git_last_sync_time"
        }

        val dataStorageMode = enum(R.string.data_storage_mode, DATA_STORAGE_MODE, DataStorageMode.EXTERNAL_SYNC)
        val externalRimeTreeUri = string(EXTERNAL_RIME_TREE_URI, "")
        val externalRimeDisplayName = string(EXTERNAL_RIME_DISPLAY_NAME, "")
        val userDbMigrated = bool(USER_DB_MIGRATED, false)
        val periodicBackgroundSync = bool(PERIODIC_BACKGROUND_SYNC, false)
        val periodicBackgroundSyncInterval = int(PERIODIC_BACKGROUND_SYNC_INTERVAL, 30)
        val lastBackgroundSyncStatus = bool(LAST_BACKGROUND_SYNC_STATUS, false)
        val lastBackgroundSyncTime = long(LAST_BACKGROUND_SYNC_TIME, 0L)

        // git config repository sync
        val gitSyncEnabled = bool(GIT_SYNC_ENABLED, false)
        val gitRepoUrl = string(GIT_REPO_URL, "")
        val gitBranch = string(GIT_BRANCH, "")
        val gitUsername = string(GIT_USERNAME, "")
        val gitToken = string(GIT_TOKEN, "")
        val gitLastCommit = string(GIT_LAST_COMMIT, "")
        val gitLastSyncTime = long(GIT_LAST_SYNC_TIME, 0L)
    }

    class Clipboard(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared, R.string.clipboard) {
        companion object {
            const val CLIPBOARD_LISTENING = "clipboard_listening"
            const val CLIPBOARD_LIMIT = "clipboard_clipboard_limit"
            const val CLIPBOARD_COMPARE_RULES = "clipboard_clipboard_compare"
            const val CLIPBOARD_OUTPUT_RULES = "clipboard_clipboard_output"
            const val CLIPBOARD_SUGGESTION = "clipboard_suggestion"
            const val CLIPBOARD_SUGGESTION_TIMEOUT = "clipboard_suggestion_timeout"
            const val CLIPBOARD_RETURN_AFTER_PASTE = "clipboard_return_after_paste"
        }
        val clipboardListening = switch(R.string.clipboard_listening, CLIPBOARD_LISTENING, true)
        val clipboardLimit = int(
            R.string.clipboard_limit,
            CLIPBOARD_LIMIT,
            10,
        ) { clipboardListening.getValue() }
        val clipboardCompareRules = editText(
            R.string.clipboard_compare_rules,
            CLIPBOARD_COMPARE_RULES,
            "",
            R.string.a_regular_expression_per_line,
        ) { clipboardListening.getValue() }
        val clipboardOutputRules = editText(
            R.string.clipboard_output_rules,
            CLIPBOARD_OUTPUT_RULES,
            "",
            R.string.a_regular_expression_per_line,
        ) { clipboardListening.getValue() }
        val clipboardSuggestion = switch(
            R.string.clipboard_suggestion,
            CLIPBOARD_SUGGESTION,
            true,
        ) { clipboardListening.getValue() }
        val clipboardSuggestionTimeout = int(
            R.string.clipboard_suggestion_timeout,
            CLIPBOARD_SUGGESTION_TIMEOUT,
            20,
            0,
            100,
            "s",
        ) { clipboardListening.getValue() && clipboardSuggestion.getValue() }
        val clipboardReturnAfterPaste = switch(
            R.string.clipboard_return_after_paste,
            CLIPBOARD_RETURN_AFTER_PASTE,
            true,
        ) { clipboardListening.getValue() }
    }

    class Advanced(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared, R.string.advanced) {
        companion object {
            const val SHOW_APP_ICON = "show_app_icon"
            const val IGNORE_SYSTEM_GESTURE_INSETS = "ignore_system_gesture_insets"
        }

        val showAppIcon = switch(
            R.string.show_app_icon,
            SHOW_APP_ICON,
            true,
            R.string.only_available_on_some_roms,
        )
        val ignoreSystemGestureInsets = switch(
            R.string.ignore_system_gesture_insets,
            IGNORE_SYSTEM_GESTURE_INSETS,
            false,
        )
    }

    /**
     * 语音输入。
     *
     * 这里**只放不敏感的开关**：火山的 apiKey / appKey / accessKey 走
     * [com.osfans.trime.voice.VoiceCredentialStore]（EncryptedSharedPreferences），
     * 不进这个普通的 SharedPreferences。
     *
     * 这些项全部用不带 UI 模型的重载注册，界面是手写的
     * `ui/main/settings/voice/VoiceInputScreen.kt`，不走 preference 渲染器。
     */
    class Voice(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared, R.string.voice_input) {
        companion object {
            const val ENABLED = "voice__enabled"
            const val PROVIDER = "voice__provider"
            const val AUTH_MODE = "voice__auth_mode"
            const val RESOURCE_ID = "voice__resource_id"
            const val MAX_DURATION = "voice__max_duration"
            const val BOOSTING_TABLE_ID = "voice__boosting_table_id"
            const val DEBUG_SIMULATE_RECOGNITION = "voice__debug_simulate_recognition"
            const val DEBUG_SIMULATE_MICROPHONE = "voice__debug_simulate_microphone"
            const val DEBUG_SIMULATED_SPEECH = "voice__debug_simulated_speech"
            const val LLM_ENABLED = "voice__llm_enabled"
            const val LLM_PRESET = "voice__llm_preset"
            const val LLM_BASE_URL = "voice__llm_base_url"
            const val LLM_MODEL = "voice__llm_model"
            const val LLM_PROMPT = "voice__llm_prompt"
            const val LLM_SHORT_TEXT_THRESHOLD = "voice__llm_short_text_threshold"
            const val DEBUG_SIMULATE_CORRECTION = "voice__debug_simulate_correction"
            const val DEBUG_SIMULATED_CORRECTION = "voice__debug_simulated_correction"

            const val PROVIDER_VOLCANO = "volcano"
        }

        /**
         * LLM 纠错。默认关；地址、模型、提示词都不是密钥，放普通偏好里。
         * **API Key 不在这里**，走 `VoiceCredentialStore.KEY_LLM_API_KEY`。
         */
        val llmEnabled = bool(LLM_ENABLED, false)
        val llmPreset = string(LLM_PRESET, "ark")

        /** 只有服务商选「自定义」时才用。 */
        val llmBaseUrl = string(LLM_BASE_URL, "")
        val llmModel = string(LLM_MODEL, "")

        /** 留空表示用内置提示词。 */
        val llmPrompt = string(LLM_PROMPT, "")
        val llmShortTextThreshold = int(LLM_SHORT_TEXT_THRESHOLD, 4)

        /** 调试：用 `FakeTranscriptCorrector` 代替真服务，结果是 success / failure / timeout。同样要再判 `BuildConfig.DEBUG`。 */
        val debugSimulateCorrection = bool(DEBUG_SIMULATE_CORRECTION, false)
        val debugSimulatedCorrection = string(DEBUG_SIMULATED_CORRECTION, "success")

        val enabled = bool(ENABLED, false)
        val provider = string(PROVIDER, PROVIDER_VOLCANO)
        val authMode = string(AUTH_MODE, "apiKey")
        val resourceId = string(RESOURCE_ID, "volc.seedasr.sauc.duration")

        /** 单次听写最长多少秒，到了就像静音一样收尾，防止忘了关一直录。 */
        val maxDuration = int(MAX_DURATION, 60)

        /** 火山控制台里的云端热词表 ID，不是密钥。填了就代替词库里的热词。 */
        val boostingTableId = string(BOOSTING_TABLE_ID, "")

        /**
         * 调试开关（「开发者」页）。界面只在 debug 包里露出来，读的地方也要再判一次
         * `BuildConfig.DEBUG`：release 包里就算偏好文件里留着也不生效。
         */
        val debugSimulateRecognition = bool(DEBUG_SIMULATE_RECOGNITION, false)
        val debugSimulateMicrophone = bool(DEBUG_SIMULATE_MICROPHONE, true)
        val debugSimulatedSpeechSeconds = int(DEBUG_SIMULATED_SPEECH, 4)
    }
}
