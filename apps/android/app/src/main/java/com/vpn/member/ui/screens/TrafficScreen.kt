package com.vpn.member.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vpn.member.data.api.DailyTrafficItem
import com.vpn.member.ui.components.KuayunBackHeader
import com.vpn.member.ui.components.KuayunListCard
import com.vpn.member.ui.components.KuayunPullRefreshBox
import com.vpn.member.ui.components.KuayunScreenBackground
import com.vpn.member.ui.components.KuayunStateBlock
import com.vpn.member.ui.viewmodel.TrafficUiState

@Composable
fun TrafficScreen(
    state: TrafficUiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    KuayunScreenBackground(modifier = Modifier.fillMaxSize()) {
        KuayunPullRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    KuayunBackHeader(
                        title = "流量统计",
                        subtitle = "统计最近 30 天使用情况，与会员 Web 端一致",
                        onBack = onBack,
                    )
                }
                item {
                    KuayunStateBlock(
                        loading = state.loading,
                        empty = !state.loading && state.daily.isEmpty() && state.summary == null,
                        emptyMessage = "暂无每日流量记录",
                        error = state.error,
                    )
                }
                if (!state.loading) {
                    state.summary?.let { summary ->
                        item {
                            KuayunListCard {
                                Text(text = "总流量：${"%.2f".format(summary.total_mb)} MB")
                                Text(text = "上传：${"%.2f".format(summary.total_up_mb)} MB")
                                Text(text = "下载：${"%.2f".format(summary.total_down_mb)} MB")
                                Text(text = "记录数：${summary.count}")
                            }
                        }
                    }
                    items(state.daily) { item ->
                        DailyTrafficCard(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyTrafficCard(item: DailyTrafficItem) {
    KuayunListCard {
        Text(text = item.date)
        Text(text = "合计：${"%.2f".format(item.total_mb)} MB")
        Text(
            text = "↑ ${"%.2f".format(item.total_up_mb)} / ↓ ${"%.2f".format(item.total_down_mb)} MB",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
