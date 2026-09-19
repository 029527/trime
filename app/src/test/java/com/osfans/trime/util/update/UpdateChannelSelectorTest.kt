/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util.update

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class UpdateChannelSelectorTest : FunSpec({

    test("默认通道列表正确配置包含直连和常用镜像") {
        UpdateChannelSelector.DEFAULT_CHANNELS.size shouldBe 3
        UpdateChannelSelector.DEFAULT_CHANNELS[0].name shouldBe "GitHub 直连"
        UpdateChannelSelector.DEFAULT_CHANNELS[0].proxyPrefix shouldBe ""
        UpdateChannelSelector.DEFAULT_CHANNELS[1].name shouldBe "ghfast.top 镜像"
        UpdateChannelSelector.DEFAULT_CHANNELS[1].proxyPrefix shouldBe "https://ghfast.top/"
        UpdateChannelSelector.DEFAULT_CHANNELS[2].name shouldBe "ghproxy.net 镜像"
        UpdateChannelSelector.DEFAULT_CHANNELS[2].proxyPrefix shouldBe "https://ghproxy.net/"
    }

    test("UpdateInfo 携带延迟和备用通道信息") {
        val info = UpdateInfo(
            versionName = "v3.3.14",
            displayTitle = "Release v3.3.14",
            releaseNotes = "Fix remote input",
            downloadUrl = "https://ghfast.top/https://github.com/029527/trime/releases/download/v3.3.14/app.apk",
            originalDownloadUrl = "https://github.com/029527/trime/releases/download/v3.3.14/app.apk",
            fileName = "app.apk",
            fileSize = 1024L,
            currentVersion = "v3.3.13",
            channelName = "ghfast.top 镜像",
            latencyMs = 120L,
            fallbackDownloadUrls = listOf("https://github.com/029527/trime/releases/download/v3.3.14/app.apk"),
        )

        info.channelName shouldBe "ghfast.top 镜像"
        info.latencyMs shouldBe 120L
        info.fallbackDownloadUrls.size shouldBe 1
    }
})
