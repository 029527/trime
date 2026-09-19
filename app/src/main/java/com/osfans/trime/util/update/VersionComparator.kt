/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util.update

object VersionComparator {

    private val SEMVER_REGEX = Regex("""(?i)\bv?(\d+)\.(\d+)(?:\.(\d+))?(?:\.(\d+))?\b""")
    private val COMMIT_COUNT_REGEX = Regex("""-(\d+)-g[0-9a-fA-F]+""")

    /**
     * 判断远端版本是否高于本地当前版本。
     *
     * @param currentVersion 本地版本（如 Const.VERSION_NAME 或 BuildConfig.BUILD_VERSION_NAME）
     * @param remoteTag 远端 Release 的 tag_name（如 "v3.3.13"、"custom-latest"）
     * @param remoteName 远端 Release 的 name（可选，如 "Custom build (hash)"）
     * @param remoteAssetName 远端匹配的 APK 资产文件名（可选，如 "com.osfans.trime-v3.3.12-164-g9b3e2089-arm64-v8a-release.apk"）
     * @return 如果远端有新版本返回 true，否则返回 false
     */
    fun isNewerVersion(
        currentVersion: String,
        remoteTag: String,
        remoteName: String? = null,
        remoteAssetName: String? = null,
    ): Boolean {
        val cleanCurrent = cleanVersionString(currentVersion)
        val cleanRemoteTag = cleanVersionString(remoteTag)

        // 1. 如果版本号纯文本完全匹配，则说明相同
        if (cleanCurrent.equals(cleanRemoteTag, ignoreCase = true)) {
            return false
        }

        // 2. 尝试从当前版本和远端版本中提取数字语义化版本进行比对
        val currentSemVer = extractSemVer(currentVersion)
        // 远端可能在 tag 中包含 semver，也可能在 asset 文件名中包含（例如 custom-latest tag 里的 com.osfans.trime-v3.3.12-xxx.apk）
        val remoteSemVer = extractSemVer(remoteTag)
            ?: remoteAssetName?.let { extractSemVer(it) }
            ?: remoteName?.let { extractSemVer(it) }

        if (currentSemVer != null && remoteSemVer != null) {
            val semVerCompare = compareSemVer(remoteSemVer, currentSemVer)
            if (semVerCompare > 0) {
                return true
            } else if (semVerCompare < 0) {
                return false
            }
            // 如果语义版本主版本号相等（例如都是 3.3.12），继续检查后面的 commit 次数
        }

        // 3. 检查 git describe 产生的 commit 次数（如 -166-g4b46a18b 中的 166）
        val currentCommitCount = extractCommitCount(currentVersion)
        val remoteCommitCount = extractCommitCount(remoteTag)
            ?: remoteAssetName?.let { extractCommitCount(it) }
            ?: remoteName?.let { extractCommitCount(it) }

        if (currentCommitCount != null && remoteCommitCount != null) {
            return remoteCommitCount > currentCommitCount
        }

        // 4. 如果本地有 commit count，而远端没有（或者远端只有通用 tag 且没有更高数字），保守认为未更新
        if (remoteSemVer == null && remoteCommitCount == null) {
            return false
        }

        return false
    }

    private fun cleanVersionString(version: String): String {
        return version
            .removePrefix("v")
            .removePrefix("V")
            .removeSuffix("-debug")
            .removeSuffix("-release")
            .trim()
    }

    private fun extractSemVer(text: String): List<Int>? {
        val match = SEMVER_REGEX.find(text) ?: return null
        return match.groupValues.drop(1).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
    }

    private fun extractCommitCount(text: String): Int? {
        val match = COMMIT_COUNT_REGEX.find(text)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * 对比两个 SemVer 列表，v1 > v2 返回正数，v1 < v2 返回负数，相等返回 0
     */
    private fun compareSemVer(v1: List<Int>, v2: List<Int>): Int {
        val maxLen = maxOf(v1.size, v2.size)
        for (i in 0 until maxLen) {
            val p1 = v1.getOrElse(i) { 0 }
            val p2 = v2.getOrElse(i) { 0 }
            if (p1 != p2) {
                return p1.compareTo(p2)
            }
        }
        return 0
    }
}
