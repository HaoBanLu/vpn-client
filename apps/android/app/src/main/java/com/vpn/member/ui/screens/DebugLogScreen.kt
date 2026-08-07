package com.vpn.member.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vpn.member.debug.AppDebugLogEntry
import com.vpn.member.ui.components.KuayunBackHeader
import com.vpn.member.ui.components.KuayunPageScaffold
import com.vpn.member.ui.viewmodel.DebugLogUiState

@Composable
fun DebugLogScreen(
    state: DebugLogUiState,
    onBack: () -> Unit,
    onUploadNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current

    KuayunPageScaffold(modifier = modifier.fillMaxSize(), contentPadding = 20.dp) {
        KuayunBackHeader(
            title = "诊断日志",
            subtitle = "仅调试账号可见，脱敏后上报便于排查连接问题",
            onBack = onBack,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onUploadNow,
                enabled = !state.uploading && state.entries.isNotEmpty(),
            ) {
                if (state.uploading) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text("立即上报")
            }
            Button(
                onClick = {
                    val text =
                        state.entries.joinToString("\n") { entry ->
                            "[${entry.clientAt}] [${entry.level}] [${entry.category}] ${entry.message}"
                        }
                    clipboard.setText(AnnotatedString(text))
                },
                enabled = state.entries.isNotEmpty(),
            ) {
                Text("复制全部")
            }
        }
        state.message?.let {
            Text(text = it, color = MaterialTheme.colorScheme.primary)
        }
        if (state.entries.isEmpty()) {
            Text(
                text = "暂无日志。请尝试连接 VPN 或切换节点后再查看。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.entries.reversed(), key = { it.id }) { entry ->
                    DebugLogCard(entry)
                }
            }
        }
    }
}

@Composable
private fun DebugLogCard(entry: AppDebugLogEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "[${entry.level}] ${entry.category}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(text = entry.message, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            if (entry.context.isNotEmpty()) {
                Text(
                    text = entry.context.entries.joinToString(" · ") { "${it.key}=${it.value}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = entry.clientAt,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
