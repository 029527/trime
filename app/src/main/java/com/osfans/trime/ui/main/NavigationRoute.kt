/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.os.Parcelable
import androidx.navigation.NavController
import androidx.navigation.createGraph
import androidx.navigation.fragment.fragment
import com.osfans.trime.R
import com.osfans.trime.ui.main.settings.ClipboardSettingsFragment
import com.osfans.trime.ui.main.settings.GeneralSettingsFragment
import com.osfans.trime.ui.main.settings.GitRepositoryFragment
import com.osfans.trime.ui.main.settings.KeyFeedbackSettingsFragment
import com.osfans.trime.ui.main.settings.KeyboardSettingsFragment
import com.osfans.trime.ui.main.settings.KeyboardUiSettingsFragment
import com.osfans.trime.ui.main.settings.ProfileSettingsFragment
import com.osfans.trime.ui.main.settings.hotwords.HotWordFragment
import com.osfans.trime.ui.main.settings.schema.SchemaListFragment
import com.osfans.trime.ui.main.settings.theme.ThemeSettingsFragment
import com.osfans.trime.ui.main.settings.userdict.UserDictionaryFragment
import com.osfans.trime.ui.main.settings.voice.VoiceCorrectionServiceFragment
import com.osfans.trime.ui.main.settings.voice.VoiceInputSettingsFragment
import com.osfans.trime.ui.main.settings.voice.VoiceRecognitionServiceFragment
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * The destinations of the settings app. Every one of them is a Compose screen hosted by
 * a `ComposeFragment`; the graph itself stays Navigation-for-Fragments so that the
 * keyboard can keep handing a `@Parcelize`d route to `MainActivity` through
 * `EXTRA_SETTINGS_ROUTE` (see `docs/modern-ui-notes.md`).
 */
@Parcelize
sealed class NavigationRoute : Parcelable {

    @Serializable
    data object Main : NavigationRoute()

    @Serializable
    data object SchemaList : NavigationRoute()

    @Serializable
    data object UserDict : NavigationRoute()

    @Serializable
    data object HotWords : NavigationRoute()

    @Serializable
    data object Profile : NavigationRoute()

    /** 配置 › 仓库与账号: the Git repository the profile pulls from. */
    @Serializable
    data object GitRepository : NavigationRoute()

    @Serializable
    data object General : NavigationRoute()

    /** 按键与手势. Named after the old 虚拟键盘 page; kept because routes travel in intents. */
    @Serializable
    data object VirtualKeyboard : NavigationRoute()

    /** 按键反馈: sound, vibration, read aloud. */
    @Serializable
    data object KeyFeedback : NavigationRoute()

    /** 配色 (`ThemePrefs`). */
    @Serializable
    data object Theme : NavigationRoute()

    /** 键盘界面: what the keyboard and the candidates window look like, landscape and floating. */
    @Serializable
    data object KeyboardUi : NavigationRoute()

    @Serializable
    data object Clipboard : NavigationRoute()

    @Serializable
    data object VoiceInput : NavigationRoute()

    /** 语音输入 › 识别服务: Volcengine credentials, model, cloud hot word table. */
    @Serializable
    data object VoiceRecognitionService : NavigationRoute()

    /** 语音输入 › 纠错服务: the language model provider, address, model, key and prompt. */
    @Serializable
    data object VoiceCorrectionService : NavigationRoute()

    @Serializable
    data object Developer : NavigationRoute()

    @Serializable
    data object About : NavigationRoute()

    @Serializable
    data object License : NavigationRoute()

    companion object {
        fun createGraph(controller: NavController) = controller.createGraph(Main) {
            val ctx = controller.context

            fragment<MainFragment, Main> {
                label = ctx.getString(R.string.trime_app_name)
            }

            fragment<SchemaListFragment, SchemaList> {
                label = ctx.getString(R.string.schemata)
            }
            fragment<UserDictionaryFragment, UserDict> {
                label = ctx.getString(R.string.user_dictionary)
            }
            fragment<HotWordFragment, HotWords> {
                label = ctx.getString(R.string.hot_words)
            }
            fragment<ProfileSettingsFragment, Profile> {
                label = ctx.getString(R.string.profile)
            }
            fragment<GitRepositoryFragment, GitRepository> {
                label = ctx.getString(R.string.git_repository)
            }

            fragment<GeneralSettingsFragment, General> {
                label = ctx.getString(R.string.general)
            }
            fragment<KeyboardSettingsFragment, VirtualKeyboard> {
                label = ctx.getString(R.string.keys_and_gestures)
            }
            fragment<KeyFeedbackSettingsFragment, KeyFeedback> {
                label = ctx.getString(R.string.key_feedback)
            }
            fragment<ClipboardSettingsFragment, Clipboard> {
                label = ctx.getString(R.string.clipboard)
            }
            fragment<ThemeSettingsFragment, Theme> {
                label = ctx.getString(R.string.theme)
            }
            fragment<KeyboardUiSettingsFragment, KeyboardUi> {
                label = ctx.getString(R.string.keyboard_ui)
            }
            fragment<VoiceInputSettingsFragment, VoiceInput> {
                label = ctx.getString(R.string.voice_input)
            }
            fragment<VoiceRecognitionServiceFragment, VoiceRecognitionService> {
                label = ctx.getString(R.string.voice_recognition_service)
            }
            fragment<VoiceCorrectionServiceFragment, VoiceCorrectionService> {
                label = ctx.getString(R.string.voice_correction_service)
            }
            fragment<DeveloperFragment, Developer> {
                label = ctx.getString(R.string.developer)
            }
            fragment<AboutFragment, About> {
                label = ctx.getString(R.string.about)
            }
            fragment<LicenseFragment, License> {
                label = ctx.getString(R.string.license)
            }
        }
    }
}
