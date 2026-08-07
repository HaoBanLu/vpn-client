package com.vpn.member.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ConnectAccountBar(
    accountName: String?,
    isVip: Boolean,
    expiresAt: String?,
    connectionScenarioLabel: String?,
    onViewDevices: () -> Unit,
    onRecharge: () -> Unit,
    onChangePassword: () -> Unit,
    onConnectionScenario: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = accountName?.takeIf { it.isNotBlank() } ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isVip) {
                        VipBadge()
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    connectionScenarioLabel?.takeIf { it.isNotBlank() }?.let { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    formatExpiry(expiresAt)?.let { expiry ->
                        Text(
                            text = "到期: $expiry",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "菜单")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("查看设备") },
                    onClick = {
                        menuExpanded = false
                        onViewDevices()
                    },
                )
                DropdownMenuItem(
                    text = { Text("充值") },
                    onClick = {
                        menuExpanded = false
                        onRecharge()
                    },
                )
                DropdownMenuItem(
                    text = { Text("修改密码") },
                    onClick = {
                        menuExpanded = false
                        onChangePassword()
                    },
                )
                DropdownMenuItem(
                    text = { Text("使用场景: ${connectionScenarioLabel ?: "自动"}") },
                    onClick = {
                        menuExpanded = false
                        onConnectionScenario()
                    },
                )
                DropdownMenuItem(
                    text = { Text("退出登录") },
                    onClick = {
                        menuExpanded = false
                        onLogout()
                    },
                )
            }
        }
    }
}

@Composable
private fun VipBadge() {
    Text(
        text = "VIP会员",
        modifier =
            Modifier
                .background(Color(0xFFFFC107), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
        fontWeight = FontWeight.Bold,
        color = Color(0xFF1A1A1A),
    )
}

private fun formatExpiry(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val instant = Instant.parse(raw)
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }.getOrElse { raw.take(16).replace('T', ' ') }
}
