/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.compose.preference

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import com.osfans.trime.data.prefs.PreferencePage
import com.osfans.trime.ui.compose.ComposeFragment
import com.osfans.trime.ui.compose.TrimeScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    livePreviewKeys: Set<String> = emptySet(),
) {
    PreferenceDelegateList(
        providers = remember(provider) { listOf(provider) },
        sections = remember(provider) { PreferencePage.of(provider) },
        modifier = modifier,
        contentPadding = contentPadding,
        clickHandlers = clickHandlers,
        suspendClickHandlers = suspendClickHandlers,
        header = header,
        footer = footer,
        livePreviewKeys = livePreviewKeys,
    )
}

/**
 * The general form: rows picked from several [providers] (see [PreferencePage]), each
 * titled section under a [PreferenceCategoryHeader].
 */
@Composable
fun PreferenceDelegateList(
    providers: List<PreferenceDelegateProvider>,
    sections: List<PreferencePage.ResolvedSection>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    clickHandlers: Map<String, () -> Unit> = emptyMap(),
    suspendClickHandlers: Map<String, suspend () -> Unit> = emptyMap(),
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    livePreviewKeys: Set<String> = emptySet(),
) {
    // Bumped whenever a preference of any provider on the page changes, so that dependent
    // rows re-evaluate their `enableUiOn` predicate and re-read their value.
    val revision = rememberProvidersRevision(providers)
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
        sections.forEachIndexed { index, section ->
            if (section.title != 0) {
                item("__section_${index}__") { PreferenceCategoryHeader(stringResource(section.title)) }
            }
            items(section.rows, key = { it.ui.key }) { row ->
                PreferenceDelegateItem(
                    provider = row.provider,
                    ui = row.ui,
                    revision = revision.intValue,
                    onClickOverride = handlers[row.ui.key],
                    livePreview = row.ui.key in livePreviewKeys,
                )
            }
        }
        if (footer != null) item("__footer__") { footer() }
    }
}

/** A change counter for a whole page: one listener on each provider, dropped with the composition. */
@Composable
private fun rememberProvidersRevision(providers: List<PreferenceDelegateProvider>): MutableIntState {
    val revision = remember { mutableIntStateOf(0) }
    val listener = remember { PreferenceDelegateProvider.OnChangeListener { revision.intValue++ } }
    DisposableEffect(providers, listener) {
        providers.forEach { it.registerOnChangeListener(listener) }
        onDispose { providers.forEach { it.unregisterOnChangeListener(listener) } }
    }
    return revision
}

/**
 * One model-driven row on a hand-written page, drawn exactly like it would be on a generated
 * one (dialog, "not set" label and all), so a preference can move there without new UI code.
 */
@Composable
fun PreferenceDelegateRow(
    provider: PreferenceDelegateProvider,
    key: String,
) {
    val providers = remember(provider) { listOf(provider) }
    val revision = rememberProvidersRevision(providers)
    val ui = remember(provider, key) {
        requireNotNull(provider.preferenceDelegatesUi.find { it.key == key }) { "no preference row for key `$key`" }
    }
    PreferenceDelegateItem(provider = provider, ui = ui, revision = revision.intValue, onClickOverride = null)
}

@Composable
private fun PreferenceDelegateItem(
    provider: PreferenceDelegateProvider,
    ui: PreferenceDelegateUi,
    revision: Int,
    onClickOverride: (() -> Unit)?,
    livePreview: Boolean = false,
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
            val liveWrite = if (livePreview) rememberThrottledWrite(delegate::setValue) else null
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
                onValueChange = liveWrite?.let { write -> write::offer },
                onValueChangeFinished = {
                    liveWrite?.cancel()
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

/**
 * Minimum gap between two live writes of a dragged slider.
 *
 * A tint write runs on the main thread the slider itself renders on. Measured on the
 * emulator (debug build): clearing the colour cache and recolouring the views takes about
 * 2 ms, and the keyboard's redraw that follows another 10–14 ms, so one write costs roughly
 * a whole frame. At 80 ms at most one frame in five pays for it and the keyboard still
 * updates about twelve times a second; much shorter intervals start starving the slider.
 */
private const val LIVE_WRITE_INTERVAL_MS = 80L

/**
 * Leading + trailing throttle: the first value goes out at once, later ones at most every
 * [LIVE_WRITE_INTERVAL_MS], and the last value of a burst is never dropped.
 */
private class ThrottledWrite(
    private val scope: CoroutineScope,
    private val write: (Int) -> Unit,
) {
    private var lastWriteAt = 0L
    private var latest = 0
    private var pending: Job? = null

    fun offer(value: Int) {
        latest = value
        if (pending != null) return // the scheduled write will pick up [latest]
        val wait = lastWriteAt + LIVE_WRITE_INTERVAL_MS - SystemClock.uptimeMillis()
        if (wait <= 0) {
            flush()
        } else {
            pending = scope.launch {
                delay(wait)
                pending = null
                flush()
            }
        }
    }

    /** Drop a scheduled write; the caller is about to commit the final value itself. */
    fun cancel() {
        pending?.cancel()
        pending = null
    }

    private fun flush() {
        lastWriteAt = SystemClock.uptimeMillis()
        write(latest)
    }
}

@Composable
private fun rememberThrottledWrite(write: (Int) -> Unit): ThrottledWrite {
    val scope = rememberCoroutineScope()
    val currentWrite by rememberUpdatedState(write)
    return remember(scope) { ThrottledWrite(scope) { currentWrite(it) } }
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
    providers: List<PreferenceDelegateProvider>,
    sections: List<PreferencePage.ResolvedSection>,
    onNavigateUp: (() -> Unit)? = null,
    clickHandlers: Map<String, () -> Unit> = emptyMap(),
    suspendClickHandlers: Map<String, suspend () -> Unit> = emptyMap(),
    actions: @Composable RowScope.() -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    livePreviewKeys: Set<String> = emptySet(),
) {
    TrimeScreen(
        title = title,
        onNavigateUp = onNavigateUp,
        actions = actions,
    ) { padding ->
        PreferenceDelegateList(
            providers = providers,
            sections = sections,
            contentPadding = padding,
            clickHandlers = clickHandlers,
            suspendClickHandlers = suspendClickHandlers,
            header = header,
            footer = footer,
            livePreviewKeys = livePreviewKeys,
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
abstract class PreferenceDelegateComposeFragment private constructor(
    private val providers: List<PreferenceDelegateProvider>,
    private val page: PreferencePage?,
    @StringRes private val titleRes: Int,
) : ComposeFragment() {
    /** Every row of [provider], in declaration order, without section headers. */
    constructor(
        provider: PreferenceDelegateProvider,
        @StringRes titleRes: Int = 0,
    ) : this(listOf(provider), null, titleRes)

    /** The rows [page] names, looked up among [providers], under the page's section headers. */
    constructor(
        page: PreferencePage,
        vararg providers: PreferenceDelegateProvider,
    ) : this(providers.toList(), page, page.title)

    private val sections by lazy { page?.resolve(providers) ?: PreferencePage.of(providers.single()) }

    @Composable
    protected open fun clickHandlers(): Map<String, () -> Unit> = emptyMap()

    /**
     * Like [clickHandlers], but the handler may suspend; it is launched in the
     * composition's coroutine scope, which is cancelled when the screen leaves.
     */
    @Composable
    protected open fun suspendClickHandlers(): Map<String, suspend () -> Unit> = emptyMap()

    /**
     * Int sliders whose value is written while dragging (throttled) instead of only on
     * release, for settings the user should watch take effect.
     */
    protected open val livePreviewKeys: Set<String> = emptySet()

    /** Extra rows appended after the model-driven ones (e.g. an "export" action). */
    @Composable
    protected open fun Footer() {
    }

    /**
     * Dialogs a [clickHandlers] entry opens. It sits beside the whole screen rather
     * than inside the lazy list — which only composes the rows it can see — so it must
     * only emit things that take no space of their own: dialogs and popups.
     */
    @Composable
    protected open fun Dialogs() {
    }

    @Composable
    final override fun Content() {
        PreferenceDelegateScreen(
            title = screenTitle(),
            providers = providers,
            sections = sections,
            onNavigateUp = if (showNavigateUp) ({ navigateUp() }) else null,
            clickHandlers = clickHandlers(),
            suspendClickHandlers = suspendClickHandlers(),
            footer = { Footer() },
            livePreviewKeys = livePreviewKeys,
        )
        Dialogs()
    }

    @Composable
    private fun screenTitle(): String {
        val res = titleRes.takeIf { it != 0 }
            ?: (providers.singleOrNull() as? PreferenceDelegateOwner)?.title?.takeIf { it != 0 }
        if (res != null) return stringResource(res)
        return findNavController().currentDestination?.label?.toString()
            ?: stringResource(R.string.trime_app_name)
    }
}
