/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.compose.preference

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.fragment.findNavController
import com.osfans.trime.R
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.data.prefs.PreferenceDelegateOwner
import com.osfans.trime.data.prefs.PreferenceDelegateProvider
import com.osfans.trime.data.prefs.PreferenceDelegateUi
import com.osfans.trime.ui.compose.ComposeFragment
import com.osfans.trime.ui.compose.TrimeScreen
import kotlinx.coroutines.launch

/**
 * Compose renderer for the code-defined preference model in `data/prefs`.
 *
 * Every settings page in Trime is a [PreferenceDelegateProvider] that registers a list
 * of [PreferenceDelegateUi] descriptors plus the matching [PreferenceDelegate]s. This
 * file turns that list into Material 3 rows, so a page is migrated simply by extending
 * [PreferenceDelegateComposeFragment] — no per-item UI code.
 *
 * Values are read and written **only** through the existing [PreferenceDelegate]s, so
 * the SharedPreferences keys and formats are untouched.
 */
@Composable
fun PreferenceDelegateList(
    provider: PreferenceDelegateProvider,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    clickHandlers: Map<String, () -> Unit> = emptyMap(),
    suspendClickHandlers: Map<String, suspend () -> Unit> = emptyMap(),
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    // Bumped whenever any preference of this provider changes, so that dependent rows
    // re-evaluate their `enableUiOn` predicate and re-read their value.
    val revision = remember { mutableIntStateOf(0) }
    val listener = remember { PreferenceDelegateProvider.OnChangeListener { revision.intValue++ } }
    DisposableEffect(provider, listener) {
        provider.registerOnChangeListener(listener)
        onDispose { provider.unregisterOnChangeListener(listener) }
    }
    // Suspending handlers run in the composition's own scope, so a page no longer has
    // to wrap them in `lifecycleScope.launch { ... }; Unit` by hand.
    val scope = rememberCoroutineScope()
    val handlers = remember(clickHandlers, suspendClickHandlers, scope) {
        buildMap<String, () -> Unit> {
            putAll(clickHandlers)
            suspendClickHandlers.forEach { (key, action) ->
                put(key) { scope.launch { action() } }
            }
        }
    }
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        if (header != null) item("__header__") { header() }
        items(provider.preferenceDelegatesUi, key = { it.key }) { ui ->
            PreferenceDelegateItem(
                provider = provider,
                ui = ui,
                revision = revision.intValue,
                onClickOverride = handlers[ui.key],
            )
        }
        if (footer != null) item("__footer__") { footer() }
    }
}

@Composable
private fun PreferenceDelegateItem(
    provider: PreferenceDelegateProvider,
    ui: PreferenceDelegateUi<*>,
    revision: Int,
    onClickOverride: (() -> Unit)?,
) {
    val context = LocalContext.current
    val enabled = remember(revision) { ui.isEnabled() }
    when (ui) {
        is PreferenceDelegateUi.Switch -> {
            val delegate = provider.requireDelegate<Boolean>(ui.key)
            val state = rememberDelegateState(delegate, revision)
            SwitchPreferenceItem(
                title = stringResource(ui.title),
                summary = ui.summary?.let { stringResource(it) },
                checked = state.value,
                enabled = enabled,
                onCheckedChange = {
                    state.value = it
                    delegate.setValue(it)
                },
            )
        }

        is PreferenceDelegateUi.SeekBarInt -> {
            val delegate = provider.requireDelegate<Int>(ui.key)
            val state = rememberDelegateState(delegate, revision)
            val defaultLabel = ui.defaultLabel?.let { stringResource(it) }
            val labelledValue = if (ui.useMinAsDefault) ui.min else ui.defaultValue
            SliderPreferenceItem(
                title = stringResource(ui.title),
                value = state.value,
                min = ui.min,
                max = ui.max,
                step = ui.step,
                unit = ui.unit,
                valueLabel = if (defaultLabel != null && state.value == labelledValue) {
                    defaultLabel
                } else {
                    "${state.value} ${ui.unit}".trim()
                },
                defaultValue = ui.defaultValue,
                enabled = enabled,
                onValueChangeFinished = {
                    state.value = it
                    delegate.setValue(it)
                },
            )
        }

        is PreferenceDelegateUi.EditTextInt -> {
            val delegate = provider.requireDelegate<Int>(ui.key)
            val state = rememberDelegateState(delegate, revision)
            var showDialog by remember { mutableStateOf(false) }
            val title = stringResource(ui.title)
            DialogPreferenceItem(
                title = title,
                value = "${state.value} ${ui.unit}".trim(),
                enabled = enabled,
                onClick = { showDialog = true },
            )
            if (showDialog) {
                IntInputDialog(
                    title = title,
                    initialValue = state.value,
                    min = ui.min,
                    max = ui.max,
                    unit = ui.unit,
                    defaultValue = ui.defaultValue,
                    onDismiss = { showDialog = false },
                    onConfirm = {
                        state.value = it
                        delegate.setValue(it)
                        showDialog = false
                    },
                )
            }
        }

        is PreferenceDelegateUi.EditText -> {
            val delegate = provider.requireDelegate<String>(ui.key)
            val state = rememberDelegateState(delegate, revision)
            var showDialog by remember { mutableStateOf(false) }
            val title = stringResource(ui.title)
            DialogPreferenceItem(
                title = title,
                value = state.value,
                enabled = enabled,
                onClick = { showDialog = true },
            )
            if (showDialog) {
                TextInputDialog(
                    title = title,
                    initialValue = state.value,
                    message = ui.message?.let { stringResource(it) },
                    onDismiss = { showDialog = false },
                    onConfirm = {
                        state.value = it
                        delegate.setValue(it)
                        showDialog = false
                    },
                )
            }
        }

        is PreferenceDelegateUi.StringList<*> -> {
            @Suppress("UNCHECKED_CAST")
            val spec = ui as PreferenceDelegateUi.StringList<Any>
            val delegate = provider.requireDelegate<Any>(spec.key)
            val state = rememberDelegateState(delegate, revision)
            val labels = spec.entryLabels.map { stringResource(it) }
            val selected = spec.entryValues.indexOf(state.value)
            SelectionRow(
                title = stringResource(spec.title),
                labels = labels,
                selectedIndex = selected,
                enabled = enabled,
                onSelect = { index ->
                    val value = spec.entryValues[index]
                    state.value = value
                    delegate.setValue(value)
                },
            )
        }

        is PreferenceDelegateUi.UniversalStringList<*> -> {
            val delegate = provider.requireDelegate<String>(ui.key)
            val state = rememberDelegateState(delegate, revision)
            // Entries can depend on runtime state (e.g. the enabled schema list),
            // so they are recomputed whenever anything on this page changes.
            val values = remember(revision) { ui.entryValues() }
            val labels = remember(revision, context) { ui.entryLabels(context).map { it.toString() } }
            SelectionRow(
                title = stringResource(ui.title),
                labels = labels,
                selectedIndex = values.indexOf(state.value),
                enabled = enabled,
                onSelect = { index ->
                    val value = values[index]
                    state.value = value
                    delegate.setValue(value)
                },
            )
        }

        is PreferenceDelegateUi.StringLike -> {
            val delegate = provider.requireDelegate<String>(ui.key)
            val state = rememberDelegateState(delegate, revision)
            PreferenceRow(
                title = stringResource(ui.title),
                summary = ui.summary?.let { stringResource(it) } ?: state.value.ifEmpty { null },
                enabled = enabled,
                onClick = onClickOverride,
            )
        }
    }
}

/**
 * A list-selection row plus its single-choice dialog.
 *
 * The stored value can fall outside the entries: a `UniversalStringList` enumerates
 * what is installed *right now*, so uninstalling the picked voice input method (or
 * deleting the picked schema) leaves a value with no label. The row then reads
 * "not set" instead of going blank, which is what the old
 * `ListPreference.SimpleSummaryProvider` did.
 */
@Composable
private fun SelectionRow(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    DialogPreferenceItem(
        title = title,
        value = labels.getOrNull(selectedIndex) ?: stringResource(R.string.not_set),
        enabled = enabled,
        onClick = { showDialog = true },
    )
    if (showDialog) {
        SingleChoiceDialog(
            title = title,
            entries = labels,
            selectedIndex = selectedIndex,
            onDismiss = { showDialog = false },
            onSelect = {
                onSelect(it)
                showDialog = false
            },
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T : Any> PreferenceDelegateProvider.requireDelegate(key: String): PreferenceDelegate<T> = requireNotNull(preferenceDelegates[key]) {
    "no PreferenceDelegate registered for key `$key`"
} as PreferenceDelegate<T>

@Composable
private fun <T : Any> rememberDelegateState(
    delegate: PreferenceDelegate<T>,
    revision: Int,
): MutableState<T> {
    val state = remember(delegate) { mutableStateOf(delegate.getValue()) }
    LaunchedEffect(revision) { state.value = delegate.getValue() }
    return state
}

/** A whole settings page: [TrimeScreen] chrome plus the rendered preference list. */
@Composable
fun PreferenceDelegateScreen(
    title: String,
    provider: PreferenceDelegateProvider,
    onNavigateUp: (() -> Unit)? = null,
    clickHandlers: Map<String, () -> Unit> = emptyMap(),
    suspendClickHandlers: Map<String, suspend () -> Unit> = emptyMap(),
    actions: @Composable RowScope.() -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    TrimeScreen(
        title = title,
        onNavigateUp = onNavigateUp,
        actions = actions,
    ) { padding ->
        PreferenceDelegateList(
            provider = provider,
            contentPadding = padding,
            clickHandlers = clickHandlers,
            suspendClickHandlers = suspendClickHandlers,
            header = header,
            footer = footer,
        )
    }
}

/**
 * Drop-in Compose replacement for `PreferenceDelegateFragment`: pass the same
 * provider and the page is done.
 *
 * Override [clickHandlers] to attach behaviour to a [PreferenceDelegateUi.StringLike]
 * row (the model has no click slot, so the key is matched by hand), or
 * [suspendClickHandlers] when that behaviour has to suspend.
 */
abstract class PreferenceDelegateComposeFragment(
    protected val provider: PreferenceDelegateProvider,
    @StringRes private val titleRes: Int = 0,
) : ComposeFragment() {
    @Composable
    protected open fun clickHandlers(): Map<String, () -> Unit> = emptyMap()

    /**
     * Like [clickHandlers], but the handler may suspend; it is launched in the
     * composition's coroutine scope, which is cancelled when the screen leaves.
     */
    @Composable
    protected open fun suspendClickHandlers(): Map<String, suspend () -> Unit> = emptyMap()

    /** Extra rows appended after the model-driven ones (e.g. an "export" action). */
    @Composable
    protected open fun Footer() {
    }

    @Composable
    final override fun Content() {
        PreferenceDelegateScreen(
            title = screenTitle(),
            provider = provider,
            onNavigateUp = if (showNavigateUp) ({ navigateUp() }) else null,
            clickHandlers = clickHandlers(),
            suspendClickHandlers = suspendClickHandlers(),
            footer = { Footer() },
        )
    }

    @Composable
    private fun screenTitle(): String {
        val res = titleRes.takeIf { it != 0 }
            ?: (provider as? PreferenceDelegateOwner)?.title?.takeIf { it != 0 }
        if (res != null) return stringResource(res)
        return findNavController().currentDestination?.label?.toString()
            ?: stringResource(R.string.trime_app_name)
    }
}
