package com.vpn.member.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpn.member.data.api.MemberSessionItem
import com.vpn.member.ui.components.KuayunBackHeader
import com.vpn.member.ui.components.KuayunPageScaffold
import com.vpn.member.ui.viewmodel.DevicesUiState

@Composable
fun DevicesScreen(
    state: DevicesUiState,
    onRefresh: () -> Unit,
    onRevoke: (String) -> Unit,
    onBack: () -> Unit,
    onDismissToast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbar.showSnackbar(it)
            onDismissToast()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        KuayunPageScaffold(
            modifier = Modifier.fillMaxSize(),
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            contentPadding = 20.dp,
        ) {
            KuayunBackHeader(
                title = "我的设备",
                subtitle = "管理已登录设备，可踢出其它终端",
                onBack = onBack,
                trailingAction = { DeviceQuotaBadge(used = state.deviceUsed, max = state.deviceMax) },
            )
            when {
                state.loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.sessions, key = { it.session_id }) { session ->
                            DeviceSessionCard(
                                session = session,
                                revoking = state.revokingSessionId == session.session_id,
                                onRevoke = { onRevoke(session.session_id) },
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun DeviceQuotaBadge(used: Int, max: Int) {
    Text(
        text = "$used/$max",
        modifier =
            Modifier
                .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun DeviceSessionCard(
    session: MemberSessionItem,
    revoking: Boolean,
    onRevoke: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = session.device_model ?: session.device_name ?: "未知设备",
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    session.device_type?.let { TagChip(it, Color(0xFF26C6DA)) }
                    if (session.is_current) TagChip("当前设备", Color(0xFFFF9800))
                    if (session.is_online) TagChip("在线", Color(0xFF4CAF50))
                }
                val nodeLine =
                    listOfNotNull(session.vpn_connected_node, session.exit_ip)
                        .joinToString(" ")
                if (nodeLine.isNotBlank()) {
                    Text(
                        text = nodeLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                session.last_active_at?.let {
                    Text(
                        text = "最后活跃: ${it.take(16).replace('T', ' ')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!session.is_current) {
                IconButton(onClick = onRevoke, enabled = !revoking) {
                    if (revoking) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(4.dp))
                    } else {
                        Icon(Icons.Default.Delete, contentDescription = "踢设备", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun TagChip(label: String, color: Color) {
    Text(
        text = label,
        modifier =
            Modifier
                .padding(end = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Medium,
    )
}
