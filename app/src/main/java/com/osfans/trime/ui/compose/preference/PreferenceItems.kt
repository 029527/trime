/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.compose.preference

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.osfans.trime.R
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Horizontal padding shared by every row of a flat preference list. */
internal val PreferenceRowPadding = 24.dp

private const val DISABLED_ALPHA = 0.38f

/**
 * The one row layout every preference item is built from: title, optional summary,
 * optional leading icon, optional trailing control, optional full-width control below
 * the text (used by the slider).
 */
@Composable
fun PreferenceRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    horizontalPadding: Dp = PreferenceRowPadding,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val titleColor = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = DISABLED_ALPHA)
    val summaryColor =
        if (enabled) scheme.onSurfaceVariant else scheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier,
            ).padding(horizontal = horizontalPadding, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = if (enabled) scheme.primary else scheme.onSurface.copy(alpha = DISABLED_ALPHA),
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                )
                if (!summary.isNullOrEmpty()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = summaryColor,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(16.dp))
                trailing()
            }
        }
        if (below != null) {
            below()
        }
    }
}

/** Section header for a grouped list. */
@Composable
fun PreferenceCategoryHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = PreferenceRowPadding, end = PreferenceRowPadding, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
fun SwitchPreferenceItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    PreferenceRow(
        title = title,
        summary = summary,
        enabled = enabled,
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            // an unchecked thumb in `outline` disappears on the zinc dark track, which is the same grey
            Switch(
                checked = checked,
                onCheckedChange = { onCheckedChange(it) },
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    )
}

/**
 * A row whose value is picked from a dialog; the current value is shown as the summary.
 * Used for list selections and text input alike.
 */
@Composable
fun DialogPreferenceItem(
    title: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    PreferenceRow(
        title = title,
        summary = value,
        enabled = enabled,
        modifier = modifier,
        onClick = onClick,
    )
}

/**
 * Inline slider with a tappable value bubble. Dragging is local state; the value is
 * only committed through [onValueChangeFinished], and tapping the bubble opens a
 * precise numeric input that also offers "reset to default".
 *
 * [onValueChange], when given, also hears every distinct snapped value while dragging,
 * for settings whose effect should be seen live. It is not throttled here.
 */
@Composable
fun SliderPreferenceItem(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    valueLabel: String,
    onValueChangeFinished: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    defaultValue: Int = value,
    unit: String = "",
    onValueChange: ((Int) -> Unit)? = null,
) {
    var dragging by remember { mutableStateOf<Int?>(null) }
    var showInput by remember { mutableStateOf(false) }
    val shown = dragging ?: value
    val safeStep = if (step > 0) step else 1

    fun snap(raw: Float): Int {
        val steps = ((raw - min) / safeStep).roundToInt()
        return (min + steps * safeStep).coerceIn(min, max)
    }

    PreferenceRow(
        title = title,
        enabled = enabled,
        modifier = modifier,
        trailing = {
            ValueBubble(
                text = if (dragging != null) "$shown $unit".trim() else valueLabel,
                enabled = enabled,
                onClick = { showInput = true },
            )
        },
        below = {
            Slider(
                value = shown.toFloat(),
                onValueChange = {
                    val snapped = snap(it)
                    if (snapped != dragging) {
                        dragging = snapped
                        onValueChange?.invoke(snapped)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                valueRange = min.toFloat()..max.toFloat(),
                onValueChangeFinished = {
                    dragging?.let(onValueChangeFinished)
                    dragging = null
                },
            )
        },
    )

    if (showInput) {
        IntInputDialog(
            title = title,
            initialValue = value,
            min = min,
            max = max,
            unit = unit,
            defaultValue = defaultValue,
            onDismiss = { showInput = false },
            onConfirm = {
                onValueChangeFinished(it)
                showInput = false
            },
        )
    }
}

@Composable
private fun ValueBubble(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (enabled) scheme.secondaryContainer else scheme.surfaceContainerHighest,
        contentColor = if (enabled) scheme.onSecondaryContainer else scheme.onSurface.copy(alpha = DISABLED_ALPHA),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .defaultMinSize(minWidth = 56.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Single-choice dialog, the Material 3 replacement for `ListPreference`'s dialog. */
@Composable
fun SingleChoiceDialog(
    title: String,
    entries: List<CharSequence>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                entries.forEachIndexed { index, entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = index == selectedIndex,
                                onClick = { onSelect(index) },
                            ).padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = index == selectedIndex,
                            onClick = null,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = entry.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

/**
 * A dialog that only says something — the Material 3 stand-in for an `AlertDialog`
 * built with `setMessage()` and one dismiss button (e.g. "no theme to select").
 */
@Composable
fun NoticeDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

/**
 * Material 3 stand-in for `ProgressBarDialogIndeterminate`: not cancellable, and the
 * caller only shows it once the work has run past a threshold (see `withLoadingState`).
 */
@Composable
fun LoadingDialog(
    @StringRes title: Int = R.string.loading,
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(title)) },
        text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
        confirmButton = {},
    )
}

/**
 * Compose replacement for `withLoadingDialog`, with the same 200 ms threshold: work
 * that finishes quickly never flashes a dialog at all.
 *
 * [setLoading] flips the caller's own state, which is what actually draws
 * [LoadingDialog]; it is always turned back off, cancellation included.
 */
suspend fun withLoadingState(
    setLoading: (Boolean) -> Unit,
    threshold: Long = 200L,
    action: suspend () -> Unit,
) {
    coroutineScope {
        val loadingJob = launch {
            delay(threshold)
            setLoading(true)
        }
        try {
            action()
        } finally {
            withContext(NonCancellable) {
                loadingJob.cancelAndJoin()
                setLoading(false)
            }
        }
    }
}

/**
 * Free text input dialog, the replacement for `EditTextPreference`'s dialog.
 *
 * @param password masks the input, for secrets
 * @param singleLine false for long free text such as a prompt
 */
@Composable
fun TextInputDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    password: Boolean = false,
    singleLine: Boolean = true,
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (!message.isNullOrEmpty()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = singleLine,
                    maxLines = if (singleLine) 1 else 10,
                    visualTransformation = if (password) {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    } else {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    },
                    keyboardOptions = if (password) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

/**
 * Numeric input dialog, clamped to `[min, max]`. Also offers resetting to
 * [defaultValue], which replaces the old seek bar dialog's neutral button.
 */
@Composable
fun IntInputDialog(
    title: String,
    initialValue: Int,
    min: Int,
    max: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    unit: String = "",
    defaultValue: Int? = null,
) {
    var text by remember { mutableStateOf(initialValue.toString()) }
    val parsed = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() || (it == '-' && min < 0) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = if (unit.isEmpty()) null else ({ Text(unit) }),
                    isError = parsed == null || parsed < min || parsed > max,
                    supportingText = { Text("$min – $max") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { onConfirm(it.coerceIn(min, max)) } },
                enabled = parsed != null,
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.End) {
                if (defaultValue != null) {
                    TextButton(onClick = { onConfirm(defaultValue) }) {
                        Text(stringResource(R.string.default_))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        },
    )
}

/** A grouped card, used by hand-written screens such as the home screen. */
@Composable
fun PreferenceCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(content = { content() })
    }
}
