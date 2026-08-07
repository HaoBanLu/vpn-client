package com.vpn.member.ui.screens

import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.vpn.member.data.device.InstalledAppsPermission
import com.vpn.member.ui.components.KuayunBackHeader
import com.vpn.member.ui.components.KuayunPageScaffold
import com.vpn.member.ui.components.PrivacyRiskConfirmDialog
import com.vpn.member.ui.viewmodel.AppDirectConnectItem
import com.vpn.member.ui.viewmodel.AppDirectConnectUiState

@Composable
fun AppDirectConnectScreen(
    state: AppDirectConnectUiState,
    onQueryChange: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDismissToast: () -> Unit,
    onRefreshApps: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    var pendingDirectEnable by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                onRefreshApps()
            }
        }
    LaunchedEffect(Unit) {
        if (!InstalledAppsPermission.isGranted(context) &&
            InstalledAppsPermission.isPermissionDeclared(context)
        ) {
            permissionLauncher.launch(InstalledAppsPermission.GET_INSTALLED_APPS)
        }
    }
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            snackbar.showSnackbar(it)
            onDismissToast()
        }
    }

    PrivacyRiskConfirmDialog(
        visible = pendingDirectEnable != null,
        onConfirm = {
            pendingDirectEnable?.let { onToggle(it, true) }
            pendingDirectEnable = null
        },
        onDismiss = { pendingDirectEnable = null },
    )

    Box(modifier = modifier.fillMaxSize()) {
        KuayunPageScaffold(
            modifier = Modifier.fillMaxSize(),
            contentPadding = 20.dp,
        ) {
            KuayunBackHeader(
                title = "应用直连",
                subtitle = "指定应用不走 VPN，将暴露真实 IP",
                onBack = onBack,
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
                state.error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(text = state.error, color = MaterialTheme.colorScheme.error)
                    }
                }
                else -> {
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
                                        text = "开启后该应用不经过跨云加速，将暴露真实 IP。",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Text(
                                        text = "列表为本机全部已安装应用（含无桌面图标的应用）。默认全部走 VPN；已选 ${state.selectedCount} 个应用直连。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                    if (state.needsInstalledAppsPermission) {
                                        Text(
                                            text = "需要「读取已安装应用列表」权限才能显示完整列表（与 LibChecker 相同权限）。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 8.dp),
                                        )
                                        Button(
                                            onClick = {
                                                permissionLauncher.launch(InstalledAppsPermission.GET_INSTALLED_APPS)
                                            },
                                            modifier = Modifier.padding(top = 8.dp),
                                        ) {
                                            Text("授予权限并刷新列表")
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            OutlinedTextField(
                                value = state.query,
                                onValueChange = onQueryChange,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("搜索应用") },
                                placeholder = { Text("应用名或包名") },
                            )
                        }
                        if (state.apps.isEmpty()) {
                            item {
                                Text(
                                    text = "未找到匹配的应用",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 24.dp),
                                )
                            }
                        } else {
                            items(state.apps, key = { it.packageName }) { app ->
                                AppDirectConnectRow(
                                    app = app,
                                    onToggle = { packageName, enabled ->
                                        if (enabled) pendingDirectEnable = packageName else onToggle(packageName, false)
                                    },
                                )
                            }
                        }
                        item {
                            Column(modifier = Modifier.padding(bottom = 16.dp)) {}
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
private fun AppDirectConnectRow(
    app: AppDirectConnectItem,
    onToggle: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val bitmap =
        remember(app.packageName) {
            runCatching { context.packageManager.getApplicationIcon(app.packageName) }
                .getOrNull()
                ?.let { drawable ->
                    when (drawable) {
                        is BitmapDrawable -> drawable.bitmap
                        else -> drawable.toBitmap()
                    }
                }
        }

    Card {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.label, fontWeight = FontWeight.Medium)
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = app.directEnabled,
                onCheckedChange = { enabled -> onToggle(app.packageName, enabled) },
            )
        }
    }
}
