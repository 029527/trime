/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.setup

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.sync.DataStorageMode
import com.osfans.trime.ui.setup.SetupPage.Companion.hasUndonePage
import kotlinx.coroutines.launch

/**
 * The first-run wizard: pick where Rime data lives, enable the input method, select it.
 *
 * All three steps are checked against the system on every [revision] bump — the user
 * leaves the app to flip a system switch and comes back, so the activity bumps it from
 * `onWindowFocusChanged`, exactly like the old `sync()` did on each fragment.
 */
@Composable
fun SetupScreen(
    revision: Int,
    onAction: (SetupPage) -> Unit,
    onStorageModeChange: (DataStorageMode) -> Unit,
    onFinish: () -> Unit,
) {
    val pages = remember { SetupPage.entries }
    val pagerState = rememberPagerState(
        initialPage = SetupPage.firstUndonePage()?.ordinal ?: 0,
        pageCount = { pages.size },
    )
    val scope = rememberCoroutineScope()
    var confirmSkip by remember { mutableStateOf(false) }

    // Recomputed on every revision: "done" is system state, not something we own.
    val doneStates = remember(revision) { pages.map { it.isDone() } }
    val allDone = remember(revision) { !hasUndonePage() }
    val modeSetupDone = doneStates[SetupPage.Mode.ordinal]

    val currentPage = pagerState.currentPage
    val isFirstPage = currentPage == 0
    val isLastPage = currentPage == pages.lastIndex

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.safeDrawingPadding()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                // The later steps are meaningless until the storage mode is settled.
                userScrollEnabled = modeSetupDone,
                verticalAlignment = Alignment.CenterVertically,
            ) { index ->
                SetupPageContent(
                    page = pages[index],
                    done = doneStates[index],
                    revision = revision,
                    onAction = onAction,
                    onStorageModeChange = onStorageModeChange,
                )
            }
            PageIndicator(count = pages.size, current = currentPage)
            Spacer(Modifier.height(8.dp))
            SetupButtons(
                showPrev = !isFirstPage,
                showSkip = modeSetupDone && !allDone,
                showNext = !(isLastPage && !allDone),
                nextEnabled = modeSetupDone,
                nextIsDone = isLastPage,
                onPrev = { scope.launch { pagerState.animateScrollToPage(currentPage - 1) } },
                onSkip = { confirmSkip = true },
                onNext = {
                    if (isLastPage) {
                        onFinish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                    }
                },
            )
        }
    }

    if (confirmSkip) {
        AlertDialog(
            onDismissRequest = { confirmSkip = false },
            text = { Text(stringResource(R.string.setup__skip_hint)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSkip = false
                        onFinish()
                    },
                ) { Text(stringResource(R.string.setup__skip_hint_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSkip = false }) {
                    Text(stringResource(R.string.setup__skip_hint_no))
                }
            },
        )
    }
}

@Composable
private fun SetupPageContent(
    page: SetupPage,
    done: Boolean,
    revision: Int,
    onAction: (SetupPage) -> Unit,
    onStorageModeChange: (DataStorageMode) -> Unit,
) {
    val context = LocalContext.current
    // The step is centred on the page, but still scrolls when it cannot fit (a short
    // landscape window with the storage options open); `heightIn(min = maxHeight)`
    // sits *inside* the scroll so there is room to centre in.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_app_icon_foreground),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(96.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = page.getStepText(context).toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = page.getHintText(context).toAnnotatedString(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (page == SetupPage.Mode) {
                Spacer(Modifier.height(24.dp))
                StorageModeOptions(revision = revision, onSelect = onStorageModeChange)
            }
            Spacer(Modifier.height(24.dp))
            if (done) {
                DoneBadge()
            } else if (page.showActionButton()) {
                Button(onClick = { onAction(page) }) {
                    Text(page.getButtonText(context).toString())
                }
            }
        }
    }
}

/** Where Rime data lives. The write is the caller's, so the rules stay in one place. */
@Composable
private fun StorageModeOptions(
    revision: Int,
    onSelect: (DataStorageMode) -> Unit,
) {
    val prefs = remember { AppPrefs.defaultInstance().profile }
    val current = remember(revision) { prefs.dataStorageMode.getValue() }
    Column(Modifier.selectableGroup()) {
        StorageModeOption(
            selected = current == DataStorageMode.EXTERNAL_SYNC,
            title = stringResource(R.string.sync_from_external),
            description = stringResource(R.string.sync_from_external_desc),
            onClick = { onSelect(DataStorageMode.EXTERNAL_SYNC) },
        )
        Spacer(Modifier.height(12.dp))
        StorageModeOption(
            selected = current == DataStorageMode.APP_STORAGE,
            title = stringResource(R.string.use_app_specific_storage),
            description = stringResource(R.string.use_app_specific_storage_desc),
            onClick = { onSelect(DataStorageMode.APP_STORAGE) },
        )
    }
}

@Composable
private fun StorageModeOption(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    // Selecting the option is the whole card's job, as tapping the description used to
    // check the radio button in the old layout.
    val border by animateDpAsState(if (selected) 2.dp else 0.dp, label = "border")
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        border = if (border > 0.dp) BorderStroke(border, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DoneBadge() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_baseline_check_circle_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.done),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PageIndicator(
    count: Int,
    current: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == current
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = if (active) 20.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ),
            )
        }
    }
}

@Composable
private fun SetupButtons(
    showPrev: Boolean,
    showSkip: Boolean,
    showNext: Boolean,
    nextEnabled: Boolean,
    nextIsDone: Boolean,
    onPrev: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = showSkip,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.setup__skip)) }
        }
        Box(Modifier.align(Alignment.CenterStart)) {
            AnimatedVisibility(visible = showPrev, enter = fadeIn(), exit = fadeOut()) {
                TextButton(onClick = onPrev) { Text(stringResource(R.string.setup__prev)) }
            }
        }
        Box(Modifier.align(Alignment.CenterEnd)) {
            AnimatedVisibility(visible = showNext, enter = fadeIn(), exit = fadeOut()) {
                Button(onClick = onNext, enabled = nextEnabled) {
                    Text(stringResource(if (nextIsDone) R.string.done else R.string.setup__next))
                }
            }
        }
    }
}

/**
 * The setup hints carry `<b>` markup in their string resources; `stringResource` would
 * drop it, so the styled `CharSequence` is converted span by span instead.
 */
private fun CharSequence.toAnnotatedString(): AnnotatedString {
    if (this !is Spanned) return AnnotatedString(toString())
    val text = this
    return buildAnnotatedString {
        append(text.toString())
        text.getSpans(0, text.length, StyleSpan::class.java).forEach { span ->
            val style = when (span.style) {
                Typeface.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                Typeface.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                Typeface.BOLD_ITALIC ->
                    SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                else -> return@forEach
            }
            addStyle(style, text.getSpanStart(span), text.getSpanEnd(span))
        }
    }
}
