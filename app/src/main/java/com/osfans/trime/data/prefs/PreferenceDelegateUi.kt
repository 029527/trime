/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.data.prefs

import android.content.Context
import androidx.annotation.StringRes

/**
 * How one preference wants to be shown. This is only the *description* — the widget is
 * picked by the renderer, `ui/compose/preference/PreferenceDelegateScreen.kt` — and the
 * value itself lives in the matching [PreferenceDelegate], so nothing here has any say
 * over the stored format.
 */
abstract class PreferenceDelegateUi(
    val key: String,
    private val enableUiOn: (() -> Boolean)? = null,
) {
    /** Dependent rows are greyed out rather than hidden, as they always have been. */
    fun isEnabled() = enableUiOn?.invoke() ?: true

    /** A plain row; what tapping it does is injected by the page that renders it. */
    class StringLike(
        @StringRes
        val title: Int,
        key: String,
        val defaultValue: String,
        @StringRes
        val summary: Int? = null,
        enableUiOn: (() -> Boolean)? = null,
    ) : PreferenceDelegateUi(key, enableUiOn)

    class Switch(
        @StringRes
        val title: Int,
        key: String,
        val defaultValue: Boolean,
        @StringRes
        val summary: Int? = null,
        enableUiOn: (() -> Boolean)? = null,
    ) : PreferenceDelegateUi(key, enableUiOn)

    /** A fixed set of choices, labelled by string resources. */
    class StringList<T : Any>(
        @StringRes
        val title: Int,
        key: String,
        val defaultValue: T,
        val serializer: PreferenceDelegate.Serializer<T>,
        val entryValues: List<T>,
        @StringRes
        val entryLabels: List<Int>,
        enableUiOn: (() -> Boolean)? = null,
    ) : PreferenceDelegateUi(key, enableUiOn)

    /**
     * Choices that only exist at runtime (installed input methods, deployed schemata…),
     * so both the values and the labels are computed every time they are shown.
     */
    class UniversalStringList<T : Any>(
        @StringRes
        val title: Int,
        key: String,
        val defaultValue: T,
        val entryValues: (() -> List<String>),
        val entryLabels: ((Context) -> List<CharSequence>),
        enableUiOn: (() -> Boolean)? = null,
    ) : PreferenceDelegateUi(key, enableUiOn)

    class EditText(
        @StringRes
        val title: Int,
        key: String,
        val defaultValue: String,
        @StringRes
        val message: Int? = null,
        enableUiOn: (() -> Boolean)? = null,
    ) : PreferenceDelegateUi(key, enableUiOn)

    class EditTextInt(
        @StringRes
        val title: Int,
        key: String,
        val defaultValue: Int,
        val min: Int,
        val max: Int,
        val unit: String = "",
        enableUiOn: (() -> Boolean)? = null,
    ) : PreferenceDelegateUi(key, enableUiOn)

    class SeekBarInt(
        @StringRes
        val title: Int,
        key: String,
        val defaultValue: Int,
        val min: Int,
        val max: Int,
        val unit: String = "",
        val step: Int = 1,
        @StringRes
        val defaultLabel: Int? = null,
        val useMinAsDefault: Boolean = false,
        enableUiOn: (() -> Boolean)? = null,
    ) : PreferenceDelegateUi(key, enableUiOn)
}
