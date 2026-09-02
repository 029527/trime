// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import com.osfans.trime.data.base.DataManager
import com.osfans.trime.util.FileUtils
import timber.log.Timber
import java.io.File

object OrphanCleaner {
    // Files that only exist in the app's own user data dir and must survive an import
    // from the external tree: installation.yaml (installation id) and user.yaml
    // (previously selected schema, option states, last build time).
    private val preservedFiles = setOf(DataManager.INSTALLATION_FILE_NAME, DataManager.USER_CONFIG_FILE_NAME)

    data class Result(
        val deleted: Int = 0,
        val failed: Int = 0,
    )

    fun removeLocalOrphans(
        root: File,
        externalPaths: Set<String>,
    ): Result {
        if (!root.exists()) return Result()
        var deleted = 0
        var failed = 0
        root
            .walkBottomUp()
            .filter { it != root }
            .filter {
                val relative = it.relativeTo(root).path.replace('\\', '/')
                !SafTreeWalker.shouldSkip(relative, it.isDirectory)
            }.forEach { file ->
                val relative =
                    runCatching {
                        SyncRelativePath.normalize(file.relativeTo(root).path.replace('\\', '/'))
                    }.getOrElse {
                        Timber.w(it, "Skip orphan cleanup for unsafe path")
                        return@forEach
                    }
                when {
                    file.isFile && relative in preservedFiles -> Unit
                    file.isFile && relative !in externalPaths -> {
                        val deleteResult = FileUtils.delete(file)
                        if (deleteResult.isSuccess) {
                            deleted++
                            Timber.i("Delete orphan $relative")
                        } else {
                            failed++
                            Timber.w(deleteResult.exceptionOrNull(), "Failed to delete orphan $relative")
                        }
                    }
                    file.isDirectory && file.list()?.isEmpty() == true -> {
                        if (file.delete()) {
                            deleted++
                        } else {
                            failed++
                            Timber.w("Failed to delete empty directory $relative")
                        }
                    }
                }
            }
        return Result(deleted = deleted, failed = failed)
    }
}
