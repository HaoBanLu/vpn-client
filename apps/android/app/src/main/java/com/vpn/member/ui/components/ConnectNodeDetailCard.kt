package com.vpn.member.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpn.member.ui.displayNodeName
import com.vpn.member.ui.theme.ConnectVisual
import com.vpn.member.vpn.VpnSessionStatsTracker

/** 已连接时的会话统计卡：紧凑展示线路 / 速率 / 时长 / 套餐；累计流量默认折叠。 */
@Composable
fun ConnectNodeDetailCard(
    connected: Boolean,
    sessionUploadBytes: Long = 0L,
    sessionDownloadBytes: Long = 0L,
    sessionDurationMs: Long = 0L,
    sessionUploadBps: Long = 0L,
    sessionDownloadBps: Long = 0L,
    remainingGb: Double? = null,
    expiresAt: String? = null,
    nodeName: String? = null,
    onSwitchNode: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (!connected) return

    var sessionExpanded by remember { mutableStateOf(false) }
    val nodeLabel = displayNodeName(nodeName).ifBlank { "未命名节点" }

    val durationText =
        if (sessionDurationMs > 0L) {
            VpnSessionStatsTracker.formatDuration(sessionDurationMs)
        } else {
            "00:00"
        }

    val subscriptionLine =
        when {
            remainingGb != null && !expiresAt.isNullOrBlank() ->
                "剩余 ${"%.1f".format(remainingGb)} GB · ${expiresAt.take(10)} 到期"
            remainingGb != null -> "剩余 ${"%.1f".format(remainingGb)} GB"
            !expiresAt.isNullOrBlank() -> "${expiresAt.take(10)} 到期"
            else -> null
        }

    val shape = RoundedCornerShape(18.dp)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        brush =
                            Brush.linearGradient(
                                colors =
                                    listOf(
                                        ConnectVisual.protectedGreen.copy(alpha = 0.10f),
                                        ConnectVisual.brandBlue.copy(alpha = 0.05f),
                                    ),
                            ),
                        shape = shape,
                    )
                    .border(
                        width = 1.dp,
                        brush =
                            Brush.linearGradient(
                                colors =
                                    listOf(
                                        ConnectVisual.protectedGreen.copy(alpha = 0.35f),
                                        ConnectVisual.brandBlue.copy(alpha = 0.15f),
                                    ),
                            ),
                        shape = shape,
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onSwitchNode != null) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                            .clickable(onClick = onSwitchNode)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = nodeLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "切换",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ConnectVisual.brandBlue,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "切换节点",
                        tint = ConnectVisual.brandBlue,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            CompactSpeedAndDurationRow(
                downloadBps = sessionDownloadBps,
                uploadBps = sessionUploadBps,
                durationText = durationText,
            )

            if (subscriptionLine != null) {
                Text(
                    text = subscriptionLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = ConnectVisual.subtitleMuted,
                )
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { sessionExpanded = !sessionExpanded }
                        .padding(vertical = 2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "本次隧道流量",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = if (sessionExpanded) "收起" else "展开",
                        tint = ConnectVisual.subtitleMuted,
                        modifier =
                            Modifier
                                .size(18.dp)
                                .rotate(if (sessionExpanded) 90f else 0f),
                    )
                }

                AnimatedVisibility(
                    visible = sessionExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            SessionTrafficChip(
                                label = "接收",
                                value = VpnSessionStatsTracker.formatBytes(sessionDownloadBytes),
                                tint = ConnectVisual.protectedGreen,
                            )
                            SessionTrafficChip(
                                label = "发送",
                                value = VpnSessionStatsTracker.formatBytes(sessionUploadBytes),
                                tint = ConnectVisual.brandBlue,
                            )
                        }
                        Text(
                            text = "仅统计本次连接，断开重连后重新计数",
                            style = MaterialTheme.typography.labelSmall,
                            color = ConnectVisual.subtitleMuted,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSpeedAndDurationRow(
    downloadBps: Long,
    uploadBps: Long,
    durationText: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactSpeedCell(
            modifier = Modifier.weight(1f),
            label = "下载",
            icon = Icons.Rounded.ArrowDownward,
            tint = ConnectVisual.protectedGreen,
            bytesPerSecond = downloadBps,
        )
        Box(
            modifier =
                Modifier
                    .padding(horizontal = 6.dp)
                    .width(1.dp)
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        )
        CompactSpeedCell(
            modifier = Modifier.weight(1f),
            label = "上传",
            icon = Icons.Rounded.ArrowUpward,
            tint = ConnectVisual.brandBlue,
            bytesPerSecond = uploadBps,
        )
        Box(
            modifier =
                Modifier
                    .padding(horizontal = 6.dp)
                    .width(1.dp)
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        )
        Row(
            modifier = Modifier.padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = ConnectVisual.subtitleMuted,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = durationText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CompactSpeedCell(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    bytesPerSecond: Long,
    modifier: Modifier = Modifier,
) {
    val speedText = VpnSessionStatsTracker.formatSpeed(bytesPerSecond)
    val isIdle = bytesPerSecond <= 0L
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(18.dp)
                        .background(tint.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(11.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = ConnectVisual.subtitleMuted,
            )
        }
        Text(
            text = speedText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color =
                if (isIdle) {
                    ConnectVisual.subtitleMuted
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
    }
}

@Composable
private fun SessionTrafficChip(
    label: String,
    value: String,
    tint: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ConnectVisual.subtitleMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = tint,
        )
    }
}
