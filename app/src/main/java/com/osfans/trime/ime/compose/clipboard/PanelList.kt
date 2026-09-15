/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.clipboard

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.osfans.trime.ime.compose.theme.LocalImeColors
import com.osfans.trime.ime.compose.theme.LocalImeFonts
import com.osfans.trime.ime.compose.theme.LocalImeTokens

/** One row of a long-press or tap menu. [icon] 0 means no icon. */
class PanelMenuAction(
    val label: String,
    @param:DrawableRes val icon: Int = 0,
    val onClick: () -> Unit,
)

/**
 * A touch target that runs [onClick] on tap and opens a menu at the finger: on long press when
 * [longPressMenu] is set, or on tap instead of [onClick] when [tapMenu] is set. The menu rows are
 * built when it opens, so they reflect the item at that moment.
 *
 * [content] gets whether the cell is currently held down.
 */
@Composable
fun PanelCell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    longPressMenu: (() -> List<PanelMenuAction>)? = null,
    tapMenu: (() -> List<PanelMenuAction>)? = null,
    content: @Composable BoxScope.(pressed: Boolean) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    var menuAt by remember { mutableStateOf<IntOffset?>(null) }
    var menuActions by remember { mutableStateOf(emptyList<PanelMenuAction>()) }
    val haptic = LocalHapticFeedback.current
    val currentOnClick by rememberUpdatedState(onClick)
    val currentLongPressMenu by rememberUpdatedState(longPressMenu)
    val currentTapMenu by rememberUpdatedState(tapMenu)
    Box(
        modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
                onTap = { offset ->
                    val menu = currentTapMenu
                    if (menu != null) {
                        menuActions = menu()
                        menuAt = offset.round()
                    } else {
                        currentOnClick()
                    }
                },
                onLongPress = { offset ->
                    val menu = currentLongPressMenu ?: return@detectTapGestures
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuActions = menu()
                    menuAt = offset.round()
                },
            )
        },
    ) {
        content(pressed)
        // a zero-size anchor under the finger: the menu opens below it, or above when there is no room
        Box(Modifier.offset { menuAt ?: IntOffset.Zero }.size(0.dp)) {
            PanelMenu(
                expanded = menuAt != null,
                actions = menuActions,
                onDismiss = { menuAt = null },
            )
        }
    }
}

/** A themed [DropdownMenu]; it lives in its own popup window, so it may extend above the keyboard. */
@Composable
fun PanelMenu(
    expanded: Boolean,
    actions: List<PanelMenuAction>,
    onDismiss: () -> Unit,
) {
    val colors = LocalImeColors.current
    val fonts = LocalImeFonts.current
    val tokens = LocalImeTokens.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        // not focusable: a focused popup would take key events away from the IME window
        properties = PopupProperties(focusable = false),
        shape = RoundedCornerShape(tokens.keyCornerRadius * 2),
        // opaque even when the tint makes keys translucent: the menu floats over the app's text
        containerColor = colors.keyBack.copy(alpha = 1f),
        shadowElevation = 8.dp,
    ) {
        val itemColors = MenuDefaults.itemColors(textColor = colors.keyText, leadingIconColor = colors.keyText)
        actions.forEach { action ->
            DropdownMenuItem(
                text = {
                    Text(action.label, fontFamily = fonts.family, fontWeight = tokens.panelLabelWeight, fontSize = tokens.panelMenuTextSize)
                },
                leadingIcon = if (action.icon != 0) {
                    { Icon(painterResource(action.icon), contentDescription = null) }
                } else {
                    null
                },
                onClick = {
                    onDismiss()
                    action.onClick()
                },
                colors = itemColors,
            )
        }
    }
}

/** A square icon button for the bar above a panel, as tall as the bar. */
@Composable
fun PanelBarButton(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalImeColors.current
    val tokens = LocalImeTokens.current
    Box(
        modifier
            .fillMaxHeight()
            // square from the bar height; narrower only when the row runs out of room
            .aspectRatio(1f, matchHeightConstraintsFirst = true)
            .clickable(
                enabled = enabled,
                interactionSource = null,
                indication = ripple(bounded = false, color = colors.candidateText),
                onClick = onClick,
            ).alpha(if (enabled) 1f else tokens.panelDisabledAlpha),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(tokens.panelBarIconSize),
            tint = colors.candidateText,
        )
    }
}

/** Centred hint for a panel with nothing to show. */
@Composable
fun PanelEmptyHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalImeColors.current
    val fonts = LocalImeFonts.current
    val tokens = LocalImeTokens.current
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        BasicText(
            text,
            style = TextStyle(
                fontFamily = fonts.family,
                fontWeight = tokens.panelBodyWeight,
                fontSize = tokens.panelEmptyHintTextSize,
                textAlign = TextAlign.Center,
            ),
            color = { colors.keyText.copy(alpha = colors.keyText.alpha * tokens.panelEmptyHintAlpha) },
        )
    }
}
