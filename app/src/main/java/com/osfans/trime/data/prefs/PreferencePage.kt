/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.prefs

import androidx.annotation.StringRes

/**
 * What one settings page shows, in which order and under which headers.
 *
 * The owners in [AppPrefs] group preferences by what reads them (`prefs.keyboard` is
 * everything the keyboard view looks at, and the IME listens to whole groups), which is not
 * how a user looks for a setting. A page therefore only names keys: a section can pick rows
 * from any owner, and the rows keep their [PreferenceDelegate]s, keys and `enableUiOn`
 * dependencies exactly as declared. Keep a row and the rows it depends on on the same page,
 * or the greyed-out state has nothing to show the reason.
 */
class PreferencePage(
    @StringRes val title: Int,
    val sections: List<Section>,
) {
    /** [title] 0 draws no header, for a page that is one plain list. */
    class Section(
        @StringRes val title: Int,
        val keys: List<String>,
    )

    class ResolvedRow(
        val provider: PreferenceDelegateProvider,
        val ui: PreferenceDelegateUi,
    )

    class ResolvedSection(
        @StringRes val title: Int,
        val rows: List<ResolvedRow>,
    )

    val keys: List<String> get() = sections.flatMap { it.keys }

    /**
     * Looks every key up among [providers]. A key with no row fails loudly rather than
     * silently dropping a setting from the app. A row a provider hides (`visible = false`,
     * e.g. on an old Android) is registered as a delegate only, so it is skipped here.
     */
    fun resolve(providers: List<PreferenceDelegateProvider>): List<ResolvedSection> = sections.map { section ->
        ResolvedSection(
            section.title,
            section.keys.mapNotNull { key ->
                providers.firstNotNullOfOrNull { provider ->
                    provider.preferenceDelegatesUi.find { it.key == key }?.let { ResolvedRow(provider, it) }
                } ?: run {
                    require(providers.any { key in it.preferenceDelegates }) { "no preference `$key` on this page's providers" }
                    null
                }
            },
        )
    }.filter { it.rows.isNotEmpty() }

    companion object {
        /** One untitled section with every row of [provider], in declaration order. */
        fun of(provider: PreferenceDelegateProvider): List<ResolvedSection> = listOf(
            ResolvedSection(0, provider.preferenceDelegatesUi.map { ResolvedRow(provider, it) }),
        )
    }
}
