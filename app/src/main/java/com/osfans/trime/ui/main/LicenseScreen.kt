/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.entity.License
import com.osfans.trime.R
import com.osfans.trime.ui.compose.TrimeScreen
import com.osfans.trime.ui.compose.preference.PreferenceRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the `aboutlibraries` metadata that is generated into `res/raw` at build time.
 * Parsing is a few hundred entries of JSON, so it happens off the main thread and the
 * screen shows a spinner until it is done.
 */
private suspend fun loadLibraries(context: Context): List<Library> = withContext(Dispatchers.IO) {
    val json = context.resources
        .openRawResource(R.raw.aboutlibraries)
        .bufferedReader()
        .use { it.readText() }
    Libs
        .Builder()
        .withJson(json)
        .build()
        .libraries
        .sortedBy {
            if (it.tag == "native") it.uniqueId.uppercase() else it.uniqueId.lowercase()
        }
}

@Composable
fun LicenseScreen(onNavigateUp: () -> Unit) {
    val context = LocalContext.current
    val libraries by produceState<List<Library>?>(initialValue = null, context) {
        value = loadLibraries(context)
    }
    // Non-null while the "which of this library's licenses?" dialog is up.
    var picking by remember { mutableStateOf<Library?>(null) }

    fun openLicense(license: License) {
        val url = license.url
        if (url.isNullOrBlank()) return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    TrimeScreen(
        title = stringResource(R.string.license),
        onNavigateUp = onNavigateUp,
    ) { padding ->
        val libs = libraries
        if (libs == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(contentPadding = padding) {
                items(libs, key = { it.uniqueId + ':' + it.artifactVersion }) { library ->
                    PreferenceRow(
                        title = "${library.uniqueId}:${library.artifactVersion}",
                        summary = library.licenses.joinToString { it.spdxId ?: it.name },
                        onClick = {
                            when (library.licenses.size) {
                                0 -> Unit
                                1 -> openLicense(library.licenses.first())
                                else -> picking = library
                            }
                        },
                    )
                }
            }
        }
    }

    picking?.let { library ->
        LicensePickerDialog(
            library = library,
            onSelect = {
                picking = null
                openLicense(it)
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun LicensePickerDialog(
    library: Library,
    onSelect: (License) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(library.uniqueId) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                library.licenses.forEach { license ->
                    Text(
                        text = license.spdxId ?: license.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(license) }
                            .padding(vertical = 14.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
