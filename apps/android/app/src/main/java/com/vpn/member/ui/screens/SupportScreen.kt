package com.vpn.member.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vpn.member.data.api.SupportChannelItem
import com.vpn.member.ui.components.KuayunBackHeader
import com.vpn.member.ui.components.KuayunInfoCard
import com.vpn.member.ui.components.KuayunScreenBackground
import com.vpn.member.ui.viewmodel.SupportUiState

@Composable
fun SupportScreen(
    state: SupportUiState,
    onRefresh: () -> Unit,
    onOpenTickets: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    KuayunScreenBackground(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                KuayunBackHeader(
                    title = "在线客服",
                    subtitle = "问题反馈与人工协助",
                    onBack = onBack,
                )
            }
            if (state.loading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            state.error?.let { error ->
                item {
                    Text(text = error, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Text("重试")
                    }
                }
            }
            state.config?.let { config ->
                if (!config.enabled) {
                    item {
                        KuayunInfoCard {
                            Text(text = "在线客服暂未开放")
                        }
                    }
                } else {
                    item {
                        KuayunInfoCard {
                            config.description?.takeIf { it.isNotBlank() }?.let {
                                Text(text = it)
                            }
                            config.work_hours?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = "工作时间：$it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                    if (config.channels.isEmpty()) {
                        item {
                            Text(
                                text = "暂未配置客服渠道",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(config.channels) { channel ->
                            SupportChannelButton(
                                channel = channel,
                                onClick = {
                                    if (channel.type == "ticket") {
                                        onOpenTickets()
                                    } else if (channel.url.isNotBlank()) {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(channel.url))
                                        context.startActivity(intent)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportChannelButton(
    channel: SupportChannelItem,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (channel.type == "ticket") {
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(channel.label.ifBlank { "提交工单" })
            }
        } else {
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(channel.label.ifBlank { channel.type })
            }
        }
    }
}
