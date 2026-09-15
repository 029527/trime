/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.voice

import android.app.Activity
import android.os.Bundle
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.osfans.trime.util.toast

/**
 * 只干一件事的透明 Activity：申请 `RECORD_AUDIO`，然后立刻关掉。
 *
 * **输入法服务自己弹不了运行时权限**（`InputMethodService` 不是 Activity，
 * 没有 `onRequestPermissionsResult` 那条回调链），所以必须借一个 Activity。
 * 走的是项目里已有的 `xxpermissions`，跟 `MainActivity` 申请通知权限同一条路：
 * 被永久拒绝时把用户送去系统设置页。
 */
class VoicePermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (XXPermissions.isGranted(this, Permission.RECORD_AUDIO)) {
            toast("录音权限已授予，回键盘点一下麦克风试试")
            finish()
            return
        }
        XXPermissions
            .with(this)
            .permission(Permission.RECORD_AUDIO)
            .request(
                object : OnPermissionCallback {
                    override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
                        if (allGranted) toast("录音权限已授予，回键盘点一下麦克风试试")
                        finish()
                    }

                    override fun onDenied(permissions: MutableList<String>, doNotAskAgain: Boolean) {
                        if (doNotAskAgain) {
                            toast("录音权限被永久拒绝了，请在系统设置里手动打开")
                            XXPermissions.startPermissionActivity(this@VoicePermissionActivity, permissions)
                        } else {
                            toast("没有录音权限，语音输入用不了")
                        }
                        finish()
                    }
                },
            )
    }
}
