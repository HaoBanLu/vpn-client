package com.vpn.member.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.vpn.member.update.AppUpdateInstaller

@Composable
fun PendingInstallDialog(
    pending: AppUpdateInstaller.PendingInstallInfo,
    needsInstallPermission: Boolean,
    onInstall: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onDismiss: (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        title = { Text("新版本已下载") },
        text = {
            Text(
                buildString {
                    append("版本 ")
                    append(pending.versionLabel)
                    append(" 已下载完成。")
                    if (needsInstallPermission) {
                        append("\n\n请先允许「安装未知应用」，再点击立即安装。")
                    } else {
                        append("\n\n请点击立即安装完成更新。")
                    }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onInstall) {
                Text("立即安装")
            }
        },
        dismissButton = {
            if (needsInstallPermission) {
                TextButton(onClick = onOpenPermissionSettings) {
                    Text("去授权")
                }
            } else if (onDismiss != null) {
                TextButton(onClick = onDismiss) {
                    Text("稍后")
                }
            }
        },
    )
}
