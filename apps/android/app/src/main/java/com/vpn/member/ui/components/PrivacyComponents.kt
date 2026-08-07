package com.vpn.member.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vpn.member.vpn.ProtectionLevel

@Composable
fun ProtectionStatusBar(
    level: ProtectionLevel,
    label: String,
    modifier: Modifier = Modifier,
) {
    val background =
        when (level) {
            ProtectionLevel.PROTECTED -> Color(0xFFDCFCE7)
            ProtectionLevel.BASELINE_READY -> Color(0xFFE0F2FE)
            ProtectionLevel.ESTABLISHING -> Color(0xFFDBEAFE)
            ProtectionLevel.DEGRADED -> Color(0xFFFEF3C7)
            ProtectionLevel.BLOCKED -> Color(0xFFFEE2E2)
            ProtectionLevel.UNPROTECTED -> MaterialTheme.colorScheme.surfaceVariant
        }
    val content =
        when (level) {
            ProtectionLevel.PROTECTED -> Color(0xFF166534)
            ProtectionLevel.BASELINE_READY -> Color(0xFF0369A1)
            ProtectionLevel.ESTABLISHING -> Color(0xFF1D4ED8)
            ProtectionLevel.DEGRADED -> Color(0xFF92400E)
            ProtectionLevel.BLOCKED -> Color(0xFFB91C1C)
            ProtectionLevel.UNPROTECTED -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Text(
        text = label,
        modifier =
            modifier
                .fillMaxWidth()
                .background(background)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        color = content,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
fun PrivacyRiskConfirmDialog(
    visible: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("隐私风险提示") },
        text = {
            Text(
                "直连应用/域名将使用本机真实 IP 访问，无法隐藏位置，可能导致流媒体解锁失败或隐私泄露。是否继续？",
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text("继续")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
