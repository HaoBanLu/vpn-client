package com.vpn.member.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpn.member.data.api.NodeItem
import com.vpn.member.ui.NodeListDisplay
import com.vpn.member.ui.components.KuayunDashedDivider
import com.vpn.member.ui.components.KuayunInstantButton
import com.vpn.member.ui.components.KuayunMainTabBrandHeader
import com.vpn.member.ui.components.KuayunPullRefreshBox
import com.vpn.member.ui.components.KuayunScreenBackground
import com.vpn.member.ui.components.KuayunStatusBadge
import com.vpn.member.ui.components.RegionFilterRow
import com.vpn.member.ui.components.latencyColor
import com.vpn.member.ui.displayNodeName
import com.vpn.member.ui.isOnline
import com.vpn.member.ui.nodeRegionLabel
import com.vpn.member.ui.theme.ConnectVisual
import com.vpn.member.ui.viewmodel.NodesUiState
import com.vpn.member.vpn.AppProtocolSupport
import com.vpn.member.vpn.ConnectionState
import com.vpn.member.vpn.NodeAccessHint

@Composable
fun NodesScreen(
    state: NodesUiState,
    connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    connectingNodeName: String? = null,
    connectedNodeName: String? = null,
    selectedNodeName: String? = null,
    onRefresh: () -> Unit,
    onTestLatency: () -> Unit,
    onSelectRegion: (String?) -> Unit,
    onSelectNode: (NodeItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    KuayunScreenBackground(modifier = modifier.fillMaxSize()) {
        KuayunPullRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                KuayunMainTabBrandHeader(
                    title = "节点选择",
                    subtitle = "点「连接」上网；需要延迟时再批量测速",
                )

                RegionFilterRow(
                    regions = state.regions,
                    selectedRegion = state.filterRegion,
                    onRegionSelected = onSelectRegion,
                    modifier = Modifier.padding(top = 8.dp),
                )

                KuayunInstantButton(
                    onClick = onTestLatency,
                    enabled = !state.testingLatency &&
                        state.nodes.any { AppProtocolSupport.isAppConnectable(it) && it.isOnline() },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .height(40.dp),
                ) {
                    if (state.testingLatency) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("测速中…")
                    } else {
                        Text("批量测速")
                    }
                }

                if (state.loading) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier
                                .padding(16.dp)
                                .align(Alignment.CenterHorizontally),
                    )
                } else {
                    val connectableNodes =
                        NodeListDisplay.sortByLatency(
                            NodeListDisplay.filterConnectable(state.nodes, state.filterRegion),
                            state.latencyMap,
                        )
                    val fastestId = NodeListDisplay.fastestNodeId(connectableNodes, state.latencyMap)
                    if (connectableNodes.isEmpty() && state.error == null) {
                        Text(text = "当前地区暂无在线节点", modifier = Modifier.padding(top = 16.dp))
                    } else if (connectableNodes.isNotEmpty()) {
                        Card(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 6.dp),
                            ) {
                                itemsIndexed(connectableNodes) { index, node ->
                                    if (index > 0) {
                                        KuayunDashedDivider(
                                            modifier =
                                                Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                                        )
                                    }
                                    val isConnectingTarget =
                                        connectingNodeName?.equals(node.name, ignoreCase = true) == true
                                    val isActive =
                                        connectionState == ConnectionState.CONNECTED &&
                                            connectedNodeName == node.name &&
                                            !isConnectingTarget
                                    val isSelected =
                                        !isActive &&
                                            selectedNodeName?.equals(node.name, ignoreCase = true) == true
                                    NodeCard(
                                        node = node,
                                        latency = state.latencyMap[node.id],
                                        isFastest = fastestId == node.id,
                                        testingLatency = state.testingLatency,
                                        filterRegion = state.filterRegion,
                                        isActive = isActive,
                                        isSelected = isSelected,
                                        isConnecting = isConnectingTarget,
                                        isSwitch = connectionState == ConnectionState.CONNECTED,
                                        onConnect = { onSelectNode(node) },
                                    )
                                }
                            }
                        }
                    }
                }

                state.message?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                state.error?.let {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeCard(
    node: NodeItem,
    latency: Int?,
    isFastest: Boolean,
    testingLatency: Boolean,
    filterRegion: String?,
    isActive: Boolean,
    isSelected: Boolean,
    isConnecting: Boolean,
    isSwitch: Boolean,
    onConnect: () -> Unit,
) {
    val activeGreen = ConnectVisual.protectedGreen
    val sceneTags = NodeListDisplay.displaySceneTags(node.scene_tags, filterRegion)
    val showRegion = NodeListDisplay.shouldShowRegionLine(filterRegion, node.region)

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    when {
                        isActive -> ConnectVisual.activeRowBackground.copy(alpha = 0.88f)
                        isSelected || isConnecting ->
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                    },
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = displayNodeName(node.name),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                when {
                    isActive -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = activeGreen,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "已连接",
                                style = MaterialTheme.typography.labelMedium,
                                color = activeGreen,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    isSelected -> {
                        Text(
                            text = "已选",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    else -> {
                        KuayunStatusBadge(
                            text = if (node.isOnline()) "在线" else (node.status ?: "未知"),
                            color =
                                if (node.isOnline()) {
                                    ConnectVisual.onlineGreen
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }

            if (showRegion) {
                Text(
                    text = "地区 ${nodeRegionLabel(node.region, node.region_name)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val tagRowVisible = sceneTags.isNotEmpty() || NodeAccessHint.poolLabel(node.access_mode) != null
            if (tagRowVisible) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (sceneTags.isNotEmpty()) {
                        sceneTags.forEach { tag ->
                            KuayunStatusBadge(text = tag, color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        NodeAccessHint.poolLabel(node.access_mode)?.let { label ->
                            KuayunStatusBadge(text = label, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                ) {
                    when {
                        latency != null && latency > 0 ->
                            KuayunStatusBadge(text = "${latency}ms", color = latencyColor(latency))
                        testingLatency ->
                            Text(
                                text = "测速中…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        else ->
                            Text(
                                text = "未测速",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                    }
                    if (isFastest && latency != null && latency > 0) {
                        KuayunStatusBadge(text = "最快", color = ConnectVisual.fastestGreen)
                    }
                }

                if (!isActive) {
                    CompactConnectButton(
                        isConnecting = isConnecting,
                        isSwitch = isSwitch,
                        onClick = onConnect,
                    )
                }
            }
        }
    }
}

/** 行尾紧凑连接按钮（非通栏），比整行点击更明显。 */
@Composable
private fun CompactConnectButton(
    isConnecting: Boolean,
    isSwitch: Boolean,
    onClick: () -> Unit,
) {
    val label =
        when {
            isConnecting -> "连接中"
            isSwitch -> "切换"
            else -> "连接"
        }
    Button(
        onClick = onClick,
        enabled = !isConnecting,
        modifier = Modifier.height(32.dp),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    if (isSwitch) ConnectVisual.brandBlue else MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                disabledContentColor = Color.White,
            ),
        elevation =
            ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = Color.White,
                )
            } else {
                Icon(
                    imageVector = if (isSwitch) Icons.Filled.SwapHoriz else Icons.Filled.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
