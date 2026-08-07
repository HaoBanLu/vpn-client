package com.vpn.member.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpn.member.data.api.RegionItem
import com.vpn.member.ui.displayNodeName
import com.vpn.member.ui.regionDisplayName
import com.vpn.member.vpn.ConnectionState

@Composable
fun ConnectRoutePanel(
    regions: List<RegionItem>,
    selectedRegion: String?,
    selectedNode: String?,
    connectedNodeName: String?,
    connectionState: ConnectionState,
    remainingGb: Double?,
    expiresAt: String?,
    onRegionSelected: (String?) -> Unit,
    onNavigateNodes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fixedNode = selectedNode?.takeIf { it.isNotBlank() }
    val connected = connectionState == ConnectionState.CONNECTED
    val activeNodeLabel =
        displayNodeName(
            if (connected) connectedNodeName ?: fixedNode else fixedNode,
        ).ifBlank { null }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "连接偏好",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (remainingGb != null && expiresAt != null) {
                    Text(
                        text = "${"%.0f".format(remainingGb)} GB · ${expiresAt.take(10)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (fixedNode != null) {
                FixedNodeBanner(
                    nodeName = displayNodeName(fixedNode),
                    connected = connected,
                    activeLabel = activeNodeLabel,
                    onChange = onNavigateNodes,
                )
                Text(
                    text = "固定节点时，下方地区筛选暂不生效",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                RegionFilterRow(
                    regions = regions,
                    selectedRegion = selectedRegion,
                    onRegionSelected = onRegionSelected,
                    segmentedShell = true,
                )
                RouteSummaryRow(
                    selectedRegion = selectedRegion,
                    regions = regions,
                    connected = connected,
                    activeNodeLabel = activeNodeLabel,
                    onNavigateNodes = onNavigateNodes,
                )
            }
        }
    }
}

@Composable
private fun RouteSummaryRow(
    selectedRegion: String?,
    regions: List<RegionItem>,
    connected: Boolean,
    activeNodeLabel: String?,
    onNavigateNodes: () -> Unit,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onNavigateNodes)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
        ) {
            Text(
                text = "请选择连接节点",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text =
                    when {
                        connected && !activeNodeLabel.isNullOrBlank() ->
                            "当前连接：$activeNodeLabel"
                        selectedRegion == null -> "前往节点页选择线路"
                        else -> "前往节点页选择「${regionDisplayName(selectedRegion, regions)}」线路"
                    },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FixedNodeBanner(
    nodeName: String,
    connected: Boolean,
    activeLabel: String?,
    onChange: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.PushPin,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = nodeName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text =
                    when {
                        connected && activeLabel != null -> "已连接 · 固定节点"
                        connected -> "已连接 · 固定节点"
                        else -> "已选定 · 下次连接生效"
                    },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onChange) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text("更换节点", modifier = Modifier.padding(start = 4.dp))
        }
    }
}
