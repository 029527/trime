/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.ui.theme.TrimeTheme
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The little editor the keyboard opens for one clipboard or collection entry. It runs
 * in a floating, dialog-themed window (see the manifest), so it draws its content
 * straight onto the platform dialog frame rather than bringing chrome of its own.
 */
class ClipEditActivity : ComponentActivity() {
    private var beanId: Int = -1
    private var clipType: String? = null
    private var text by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes.gravity = Gravity.TOP
        setContent {
            TrimeTheme(applySystemBarAppearance = false) {
                ClipEditContent(
                    text = text,
                    onTextChange = { text = it },
                    onCancel = ::finish,
                    onConfirm = ::finishEditing,
                )
            }
        }
        processIntent(intent)
    }

    private fun finishEditing() {
        val str = text
        lifecycleScope.launch {
            when (clipType) {
                FROM_CLIPBOARD -> ClipboardHelper.updateText(beanId, str)
                FROM_COLLECTION -> CollectionHelper.updateText(beanId, str)
                else -> {}
            }
        }
        finish()
    }

    private fun setBean(bean: DatabaseBean) {
        beanId = bean.id
        text = bean.text.orEmpty()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent) {
        lifecycleScope.launch {
            intent.run {
                val clipType = intent.getStringExtra(CLIP_TYPE) ?: return@launch
                val beanId = getIntExtra(BEAN_ID, -1)
                Timber.d("processIntent: id=$beanId, type=$clipType")
                when (clipType) {
                    FROM_CLIPBOARD -> ClipboardHelper.get(beanId)
                    FROM_COLLECTION -> CollectionHelper.get(beanId)
                    else -> null
                }?.also {
                    this@ClipEditActivity.clipType = clipType
                    setBean(it)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    companion object {
        const val BEAN_ID = "id"
        const val CLIP_TYPE = "clip_type"
        const val FROM_CLIPBOARD = "from_clipboard"
        const val FROM_COLLECTION = "from_collection"
    }
}

@Composable
private fun ClipEditContent(
    text: String,
    onTextChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(stringResource(R.string.clip_edit_text_hint)) },
            textStyle = MaterialTheme.typography.bodyLarge,
            minLines = 4,
            maxLines = 8,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(android.R.string.cancel))
            }
            TextButton(onClick = onConfirm) {
                Text(stringResource(android.R.string.ok))
            }
        }
    }
}
