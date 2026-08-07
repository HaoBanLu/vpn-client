package com.vpn.member.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpn.member.data.api.SubscriptionActive
import com.vpn.member.data.api.SubscriptionUsage
import com.vpn.member.ui.SubscriptionStatusLabel
import com.vpn.member.ui.formatExpiryDate
import com.vpn.member.ui.subscriptionPackageName
import com.vpn.member.ui.subscriptionStatusLabel
import com.vpn.member.ui.trafficProgress

@Composable
fun CurrentSubscriptionCard(
    subscription: SubscriptionActive?,
    usage: SubscriptionUsage?,
    onNavigatePackages: () -> Unit,
    onTraffic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (subscription == null) {
        SubscriptionHeroCardShell(modifier = modifier) {
            Text(
                text = "我使用的套餐",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "暂无有效套餐",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "购买套餐后即可使用跨云加速服务",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = onNavigatePackages,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .height(36.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("去购买套餐")
            }
        }
        return
    }

    val statusLabel = subscriptionStatusLabel(subscription, usage)
    val statusColor =
        when (statusLabel) {
            SubscriptionStatusLabel.ACTIVE -> Color(0xFF4CAF50)
            SubscriptionStatusLabel.EXPIRING_SOON -> Color(0xFFFFA726)
            SubscriptionStatusLabel.LOW_TRAFFIC -> MaterialTheme.colorScheme.error
            null -> MaterialTheme.colorScheme.primary
        }

    val remainingGb =
        usage?.remaining
            ?: (subscription.traffic_total_gb - subscription.traffic_used_gb)
    val totalGb = usage?.total ?: subscription.traffic_total_gb
    val summaryLine =
        "剩余 ${"%.1f".format(remainingGb)}/${"%.0f".format(totalGb)} GB · ${formatExpiryDate(subscription.expires_at)} 到期"

    SubscriptionHeroCardShell(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "我使用的套餐",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = subscriptionPackageName(subscription),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            statusLabel?.let {
                KuayunStatusBadge(text = it.text, color = statusColor)
            }
        }

        Text(
            text = summaryLine,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        LinearProgressIndicator(
            progress = { trafficProgress(usage) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onNavigatePackages,
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("续费 / 升级")
            }
            OutlinedButton(
                onClick = onTraffic,
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(12.dp),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    ),
            ) {
                Text("流量统计")
            }
        }
    }
}

@Composable
private fun SubscriptionHeroCardShell(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f),
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.34f),
                                ),
                        ),
                    ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                content = { content() },
            )
        }
    }
}

@Composable
fun CurrentSubscriptionSummaryBar(
    subscription: SubscriptionActive,
    usage: SubscriptionUsage?,
    modifier: Modifier = Modifier,
) {
    val remaining = usage?.let { "${"%.1f".format(it.remaining)}" } ?: "-"
    KuayunInfoCard(modifier = modifier) {
        Text("当前套餐", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = "${subscriptionPackageName(subscription)} · 剩余 ${remaining}GB · ${formatExpiryDate(subscription.expires_at)} 到期",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
