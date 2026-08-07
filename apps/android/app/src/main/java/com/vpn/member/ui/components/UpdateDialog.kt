package com.vpn.member.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.vpn.member.data.api.ClientVersionData

@Composable
fun UpdateDialog(
    update: ClientVersionData,
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = {
            if (!update.force_update) {
                onDismiss?.invoke()
            }
        },
        title = { Text(if (update.force_update) "需要更新 App" else "发现新版本") },
        text = {
            Text(
                buildString {
                    append("最新版本：")
                    append(update.latest_version_name ?: update.latest_version_code.toString())
                    if (!update.release_notes.isNullOrBlank()) {
                        append("\n\n")
                        append(update.release_notes)
                    }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("去更新")
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss?.invoke() }) {
                Text(if (update.force_update) "稍后再说" else "稍后")
            }
        },
    )
}
