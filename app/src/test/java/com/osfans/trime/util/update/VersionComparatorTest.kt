/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util.update

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class VersionComparatorTest : FunSpec({

    test("语义版本比较：远端版本更高") {
        VersionComparator.isNewerVersion(
            currentVersion = "3.3.12-release",
            remoteTag = "v3.3.13",
        ) shouldBe true

        VersionComparator.isNewerVersion(
            currentVersion = "v3.3.12",
            remoteTag = "3.4.0",
        ) shouldBe true

        VersionComparator.isNewerVersion(
            currentVersion = "3.3.12-debug",
            remoteTag = "v3.3.12.1",
        ) shouldBe true
    }

    test("语义版本比较：本地已是最新或更高") {
        VersionComparator.isNewerVersion(
            currentVersion = "3.3.13-release",
            remoteTag = "v3.3.13",
        ) shouldBe false

        VersionComparator.isNewerVersion(
            currentVersion = "v3.3.13",
            remoteTag = "v3.3.12",
        ) shouldBe false

        VersionComparator.isNewerVersion(
            currentVersion = "3.3.13",
            remoteTag = "3.3.13",
        ) shouldBe false
    }

    test("Git 提交数比较：远端提交数更高") {
        VersionComparator.isNewerVersion(
            currentVersion = "custom-latest-164-g9b3e2089-debug",
            remoteTag = "custom-latest",
            remoteName = "Custom build (4b46a18b)",
            remoteAssetName = "com.osfans.trime-v3.3.12-166-g4b46a18b-arm64-v8a-release.apk",
        ) shouldBe true
    }

    test("Git 提交数比较：本地提交数更高或相等") {
        VersionComparator.isNewerVersion(
            currentVersion = "custom-latest-166-g4b46a18b",
            remoteTag = "custom-latest",
            remoteName = "Custom build (9b3e2089)",
            remoteAssetName = "com.osfans.trime-v3.3.12-164-g9b3e2089-arm64-v8a-release.apk",
        ) shouldBe false

        VersionComparator.isNewerVersion(
            currentVersion = "custom-latest-164-g9b3e2089",
            remoteTag = "custom-latest",
            remoteAssetName = "com.osfans.trime-v3.3.12-164-g9b3e2089-arm64-v8a-release.apk",
        ) shouldBe false
    }

    test("仓库地址提取 repo 测试") {
        UpdateChecker.extractRepoFromGitUrl("https://github.com/029527/trime") shouldBe "029527/trime"
        UpdateChecker.extractRepoFromGitUrl("https://github.com/osfans/trime.git") shouldBe "osfans/trime"
        UpdateChecker.extractRepoFromGitUrl("git@github.com:029527/trime.git") shouldBe "029527/trime"
    }

    test("ABI 匹配算法测试") {
        val checker = UpdateChecker()
        val assets = listOf(
            GithubAsset(name = "com.osfans.trime-v3.3.12-arm64-v8a-release.apk"),
            GithubAsset(name = "com.osfans.trime-v3.3.12-armeabi-v7a-release.apk"),
            GithubAsset(name = "com.osfans.trime-v3.3.12-x86_64-release.apk"),
        )
        val best = checker.findBestApkAsset(assets)
        best shouldBe assets.first() // 在单测 JVM 环境下兜底匹配第一个或匹配架构
    }
})
