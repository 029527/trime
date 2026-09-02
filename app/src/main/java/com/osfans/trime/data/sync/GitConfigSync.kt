/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.sync

import android.os.Build
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.appContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import timber.log.Timber
import java.io.File

/**
 * Keeps the Rime user data directory in sync with a Git repository.
 *
 * The repository is cloned into the app's private storage and hard-reset to the
 * remote branch on every pull (local edits are never kept), then its files are
 * copied into [DataManager.userDataDir] the same way the external folder import
 * does: files that disappeared from the repository are removed, while user
 * databases, `user.yaml` and `installation.yaml` are preserved.
 */
object GitConfigSync {
    private const val REPO_DIR_NAME = "git-config"

    private val prefs get() = AppPrefs.defaultInstance().profile

    val repoDir: File
        get() = File(appContext.filesDir, REPO_DIR_NAME)

    /** JGit needs java.nio.file, available since API 26. */
    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun isEnabled(): Boolean = isSupported && prefs.gitSyncEnabled.getValue() && prefs.gitRepoUrl.getValue().isNotBlank()

    private fun credentials(): CredentialsProvider? {
        val token = prefs.gitToken.getValue()
        if (token.isBlank()) return null
        val user = prefs.gitUsername.getValue().ifBlank { "git" }
        return UsernamePasswordCredentialsProvider(user, token)
    }

    /**
     * Clone the repository if needed, then fetch and hard-reset the working tree to
     * the configured remote branch. Returns the HEAD commit id.
     */
    suspend fun pull(): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                check(isSupported) { "Git sync requires Android 8.0+" }
                val url = prefs.gitRepoUrl.getValue().trim()
                check(url.isNotEmpty()) { "Repository URL is empty" }
                val branch = prefs.gitBranch.getValue().trim()
                val dir = repoDir
                val credentials = credentials()
                val git =
                    if (File(dir, ".git").isDirectory) {
                        Git.open(dir)
                    } else {
                        if (dir.exists()) dir.deleteRecursively()
                        Timber.i("Cloning config repository $url")
                        Git
                            .cloneRepository()
                            .setURI(url)
                            .setDirectory(dir)
                            .setCredentialsProvider(credentials)
                            .apply { if (branch.isNotEmpty()) setBranch(branch) }
                            .call()
                    }
                git.use { g ->
                    g.repository.config.apply {
                        setString("remote", "origin", "url", url)
                        save()
                    }
                    g
                        .fetch()
                        .setRemote("origin")
                        .setRemoveDeletedRefs(true)
                        .setCredentialsProvider(credentials)
                        .call()
                    val targetBranch = branch.ifEmpty { g.repository.branch ?: "main" }
                    val remoteRef = "refs/remotes/origin/$targetBranch"
                    checkNotNull(g.repository.resolve(remoteRef)) { "Branch '$targetBranch' not found on remote" }
                    g.reset().setMode(ResetCommand.ResetType.HARD).setRef(remoteRef).call()
                    g.clean().setCleanDirectories(true).setForce(true).call()
                    val head = g.repository.resolve("HEAD")?.name.orEmpty()
                    prefs.gitLastCommit.setValue(head.take(12))
                    prefs.gitLastSyncTime.setValue(System.currentTimeMillis())
                    Timber.i("Config repository at $head")
                    head
                }
            }.onFailure { Timber.e(it, "Git pull failed") }
        }

    data class ImportStats(
        val copied: Int,
        val deleted: Int,
    )

    /** Copy the checked-out files into the Rime user data directory. */
    suspend fun importToLocal(): Result<ImportStats> =
        withContext(Dispatchers.IO) {
            runCatching {
                val src = repoDir
                check(File(src, ".git").isDirectory) { "Repository not cloned yet" }
                val dest = DataManager.userDataDir
                val repoPaths = HashSet<String>()
                var copied = 0
                src
                    .walkTopDown()
                    .onEnter { it == src || (it.name != ".git" && !SafTreeWalker.shouldSkip(it.relativeTo(src).path, isDirectory = true)) }
                    .filter { it.isFile }
                    .forEach { file ->
                        val relative = file.relativeTo(src).path.replace('\\', '/')
                        if (SafTreeWalker.shouldSkip(relative)) return@forEach
                        repoPaths += relative
                        val target = File(dest, relative)
                        if (!target.exists() || target.length() != file.length() || target.lastModified() < file.lastModified()) {
                            target.parentFile?.mkdirs()
                            file.copyTo(target, overwrite = true)
                            copied++
                        }
                    }
                val removed = OrphanCleaner.removeLocalOrphans(dest, repoPaths)
                Timber.i("Git import: copied=$copied deleted=${removed.deleted} failed=${removed.failed}")
                ImportStats(copied, removed.deleted)
            }.onFailure { Timber.e(it, "Git import failed") }
        }

    /** Pull then import; the result carries the HEAD commit id. */
    suspend fun pullAndImport(): Result<String> =
        pull().mapCatching { head ->
            importToLocal().getOrThrow()
            head
        }
}
