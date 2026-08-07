package com.vpn.member.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpn.member.vpn.ConnectionScenario

@Composable
fun ConnectionScenarioDialog(
    visible: Boolean,
    currentScenario: String,
    onDismiss: () -> Unit,
    onSelectScenario: (String) -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("使用场景") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "决定连接时的加速策略（与所选节点联动）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ScenarioOption(
                    title = "自动",
                    description = "全局模式，全部流量走所选节点",
                    selected = ConnectionScenario.normalize(currentScenario) == ConnectionScenario.AUTO,
                    onClick = { onSelectScenario(ConnectionScenario.AUTO) },
                )
                ScenarioOption(
                    title = "回国加速",
                    description = "全局走代理，适合海外使用国内 App",
                    selected = ConnectionScenario.normalize(currentScenario) == ConnectionScenario.RETURN_HOME,
                    onClick = { onSelectScenario(ConnectionScenario.RETURN_HOME) },
                )
                ScenarioOption(
                    title = "海外访问",
                    description = "全局走代理，访问外网",
                    selected = ConnectionScenario.normalize(currentScenario) == ConnectionScenario.OVERSEAS,
                    onClick = { onSelectScenario(ConnectionScenario.OVERSEAS) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun ScenarioOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(text = title, fontWeight = FontWeight.SemiBold)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
