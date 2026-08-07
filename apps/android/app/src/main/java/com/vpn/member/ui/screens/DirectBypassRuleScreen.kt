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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpn.member.ui.components.KuayunBackHeader
import com.vpn.member.ui.components.KuayunPageScaffold
import com.vpn.member.ui.components.PrivacyRiskConfirmDialog
import com.vpn.member.ui.viewmodel.DirectBypassRuleItem
import com.vpn.member.ui.viewmodel.DirectBypassRuleUiState
import com.vpn.member.vpn.DirectBypassRuleType

private sealed interface PendingDirectBypassRisk {
    data object AddRule : PendingDirectBypassRisk

    data class EnableRule(val id: String) : PendingDirectBypassRisk
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectBypassRuleScreen(
    state: DirectBypassRuleUiState,
    onOpenAddDialog: () -> Unit,
    onDismissAddDialog: () -> Unit,
    onAddTypeChange: (DirectBypassRuleType) -> Unit,
    onAddValueChange: (String) -> Unit,
    onConfirmAdd: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onDismissToast: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    var pendingRisk by remember { mutableStateOf<PendingDirectBypassRisk?>(null) }
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            snackbar.showSnackbar(it)
            onDismissToast()
        }
    }

    PrivacyRiskConfirmDialog(
        visible = pendingRisk != null,
        onConfirm = {
            when (val action = pendingRisk) {
                PendingDirectBypassRisk.AddRule -> onConfirmAdd()
                is PendingDirectBypassRisk.EnableRule -> onToggle(action.id, true)
                null -> Unit
            }
            pendingRisk = null
        },
        onDismiss = { pendingRisk = null },
    )

    if (state.showAddDialog) {
        AddDirectBypassRuleDialog(
            type = state.addType,
            value = state.addValue,
            error = state.addError,
            onTypeChange = onAddTypeChange,
            onValueChange = onAddValueChange,
            onRequestSave = { pendingRisk = PendingDirectBypassRisk.AddRule },
            onDismiss = onDismissAddDialog,
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        KuayunPageScaffold(
            modifier = Modifier.fillMaxSize(),
            contentPadding = 20.dp,
        ) {
            KuayunBackHeader(
                title = "规则直连",
                subtitle = "匹配规则的流量不经代理，将暴露真实 IP",
                onBack = onBack,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "匹配规则的流量不经代理，将暴露本机真实 IP。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = "已启用 ${state.enabledCount} 条规则；下次连接 VPN 后生效。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
                if (state.rules.isEmpty()) {
                    item {
                        Text(
                            text = "暂无规则，点击右下角添加",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                } else {
                    items(state.rules, key = { it.id }) { rule ->
                        DirectBypassRuleRow(
                            rule = rule,
                            onToggle = { id, enabled ->
                                if (enabled) {
                                    pendingRisk = PendingDirectBypassRisk.EnableRule(id)
                                } else {
                                    onToggle(id, false)
                                }
                            },
                            onDelete = onDelete,
                        )
                    }
                }
                item {
                    Column(modifier = Modifier.padding(bottom = 80.dp)) {}
                }
            }
        }
        FloatingActionButton(
            onClick = onOpenAddDialog,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "添加规则")
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDirectBypassRuleDialog(
    type: DirectBypassRuleType,
    value: String,
    error: String?,
    onTypeChange: (DirectBypassRuleType) -> Unit,
    onValueChange: (String) -> Unit,
    onRequestSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val placeholder =
        when (type) {
            DirectBypassRuleType.DOMAIN -> "例如 www.example.com"
            DirectBypassRuleType.DOMAIN_SUFFIX -> "例如 example.com 或 *.example.com"
            DirectBypassRuleType.DOMAIN_KEYWORD -> "例如 bank"
            DirectBypassRuleType.IP_CIDR -> "例如 192.168.1.0/24"
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = type.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("规则类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier =
                            Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        DirectBypassRuleType.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    onTypeChange(option)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("规则内容") },
                    placeholder = { Text(placeholder) },
                    isError = error != null,
                    supportingText =
                        error?.let { err ->
                            { Text(err, color = MaterialTheme.colorScheme.error) }
                        },
                )
                Text(
                    text = "规则直连将暴露真实 IP，可能导致隐私泄露。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onRequestSave) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun DirectBypassRuleRow(
    rule: DirectBypassRuleItem,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    Card {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = rule.typeLabel, fontWeight = FontWeight.Medium)
                Text(
                    text = rule.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = { enabled -> onToggle(rule.id, enabled) },
            )
            IconButton(onClick = { onDelete(rule.id) }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
