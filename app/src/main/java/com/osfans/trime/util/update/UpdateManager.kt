/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util.update

import android.content.Context
import com.osfans.trime.util.Const
import kotlinx.coroutines.flow.Flow
import java.io.File

class UpdateManager(
    private val context: Context,
    private val checker: UpdateChecker = UpdateChecker(),
    private val downloader: ApkDownloader = ApkDownloader(context),
) {

    /**
     * 检查更新
     */
    suspend fun checkUpdate(
        currentVersion: String = Const.VERSION_NAME,
    ): CheckUpdateResult {
        return checker.checkUpdate(currentVersion)
    }

    /**
     * 下载 APK，返回进度 Flow
     */
    fun downloadApk(updateInfo: UpdateInfo): Flow<DownloadState> {
        return downloader.downloadApk(
            downloadUrl = updateInfo.downloadUrl,
            fileName = updateInfo.fileName,
            fallbackUrls = updateInfo.fallbackDownloadUrls,
            fallbackUrl = updateInfo.originalDownloadUrl,
        )
    }

    /**
     * 安装下载好的 APK 文件
     */
    fun installApk(file: File): Boolean {
        return ApkInstaller.installApk(context, file)
    }

    /**
     * 是否具备安装未知来源应用权限
     */
    fun canRequestPackageInstalls(): Boolean {
        return ApkInstaller.canRequestPackageInstalls(context)
    }

    /**
     * 跳转至“安装未知应用”系统设置页
     */
    fun openInstallPermissionSettings() {
        ApkInstaller.openInstallPermissionSettings(context)
    }
}
