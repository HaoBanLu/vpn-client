package com.vpn.member.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vpn.member.ui.isOnline
import com.vpn.member.ui.viewmodel.ConnectViewModel
import com.vpn.member.ui.viewmodel.NodesViewModel
import com.vpn.member.ui.viewmodel.PackagesViewModel
import com.vpn.member.ui.viewmodel.ProfileViewModel

@Composable
fun MainShell(
    connectViewModel: ConnectViewModel,
    nodesViewModel: NodesViewModel,
    packagesViewModel: PackagesViewModel,
    profileViewModel: ProfileViewModel,
    connectState: com.vpn.member.ui.viewmodel.ConnectUiState,
    nodesState: com.vpn.member.ui.viewmodel.NodesUiState,
    packagesState: com.vpn.member.ui.viewmodel.PackagesUiState,
    profileState: com.vpn.member.ui.viewmodel.ProfileUiState,
    onRequestVpnConnect: (connectAction: () -> Unit) -> Unit,
    onNavigateTraffic: () -> Unit,
    onNavigateRecharge: () -> Unit,
    onNavigateRechargeOrders: () -> Unit,
    onNavigatePurchaseOrders: () -> Unit,
    onNavigateChangePassword: () -> Unit,
    onNavigateTickets: () -> Unit,
    onNavigateSupport: () -> Unit,
    onNavigateAbout: () -> Unit,
    onNavigateAppDirectConnect: () -> Unit,
    onNavigateDirectBypassRule: () -> Unit,
    onNavigateStabilitySettings: () -> Unit,
    onNavigateDebugLog: (() -> Unit)? = null,
    onNavigateDevices: () -> Unit,
    onLogout: () -> Unit,
    isLoggedIn: Boolean = false,
    accountFallbackEmail: String? = null,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val appContext = LocalContext.current.applicationContext as android.app.Application

    LaunchedEffect(Unit) {
        connectViewModel.warmupForFastConnect(appContext)
    }

    DisposableEffect(Unit) {
        profileViewModel.startNotificationPolling()
        onDispose { profileViewModel.stopNotificationPolling() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    connectViewModel.onAppForeground()
                    connectViewModel.reloadDashboard()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 3) {
            profileViewModel.clearUnreadNotifications()
        }
    }

    LaunchedEffect(connectState.requestNavigateToNodes) {
        if (connectState.requestNavigateToNodes) {
            selectedTab = 1
            snackbarHostState.showSnackbar("请选择要连接的节点")
            connectViewModel.consumeNavigateToNodesRequest()
        }
    }

    LaunchedEffect(connectState.requestNavigateToPackages) {
        if (connectState.requestNavigateToPackages) {
            selectedTab = 2
            snackbarHostState.showSnackbar("请先购买或续费套餐")
            connectViewModel.consumeNavigateToPackagesRequest()
        }
    }

    LaunchedEffect(profileState.toastMessage) {
        profileState.toastMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            profileViewModel.dismissToast()
        }
    }

    LaunchedEffect(connectState.routeMessage) {
        connectState.routeMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            connectViewModel.dismissRouteMessage()
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("连接") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                    label = { Text("节点") },
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                    label = { Text("套餐") },
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (profileState.unreadNotificationCount > 0) {
                                    Badge {
                                        Text(
                                            text = profileState.unreadNotificationCount.coerceAtMost(99).toString(),
                                        )
                                    }
                                }
                            },
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null)
                        }
                    },
                    label = { Text("我的") },
                )
            }
        },
    ) { padding ->
        when (selectedTab) {
            0 -> ConnectScreen(
                modifier = Modifier.padding(padding),
                state = connectState,
                entryLatencyMs = resolveEntryLatency(nodesState, connectState),
                onConnect = { onRequestVpnConnect { connectViewModel.connect() } },
                onDisconnect = connectViewModel::disconnect,
                onRefresh = connectViewModel::refresh,
                onBuy = { selectedTab = 2 },
                onNavigateNodes = { selectedTab = 1 },
                onDismissPrivacyOnboarding = connectViewModel::dismissPrivacyOnboarding,
                onOpenPrivacyOnboardingVpnSettings = connectViewModel::openPrivacyOnboardingVpnSettings,
                onOpenPrivacyOnboardingBatterySettings = connectViewModel::openPrivacyOnboardingBatterySettings,
                onCompletePrivacyOnboarding = connectViewModel::completePrivacyOnboarding,
            )
            1 -> NodesScreen(
                modifier = Modifier.padding(padding),
                state = nodesState,
                connectionState = connectState.connectionState,
                connectingNodeName = connectState.connectingNodeName,
                connectedNodeName = connectState.connectedNodeName,
                selectedNodeName = nodesState.selectedNode,
                onRefresh = nodesViewModel::load,
                onTestLatency = nodesViewModel::testLatency,
                onSelectRegion = nodesViewModel::setFilterRegion,
                onSelectNode = { node ->
                    when {
                        !node.isOnline() || !com.vpn.member.vpn.AppProtocolSupport.isAppConnectable(node) ->
                            nodesViewModel.selectNode(node)
                        else -> {
                            nodesViewModel.syncSelected(node)
                            selectedTab = 0
                            onRequestVpnConnect {
                                connectViewModel.connectToNode(node.name, node.region)
                            }
                        }
                    }
                },
            )
            2 -> PackagesScreen(
                modifier = Modifier.padding(padding),
                state = packagesState,
                onRefresh = packagesViewModel::load,
                onPurchase = { id ->
                    packagesViewModel.purchase(
                        packageId = id,
                        onSuccess = {
                            connectViewModel.refresh()
                            profileViewModel.refresh()
                            selectedTab = 0
                        },
                        onInsufficientBalance = onNavigateRecharge,
                    )
                },
                onInsufficientBalance = onNavigateRecharge,
            )
            3 -> ProfileScreen(
                modifier = Modifier.padding(padding),
                state = profileState,
                onRefresh = profileViewModel::refresh,
                onLogout = onLogout,
                onRecharge = onNavigateRecharge,
                onRechargeOrders = onNavigateRechargeOrders,
                onPurchaseOrders = onNavigatePurchaseOrders,
                onChangePassword = onNavigateChangePassword,
                onTickets = onNavigateTickets,
                onSupport = onNavigateSupport,
                onTraffic = onNavigateTraffic,
                onAbout = onNavigateAbout,
                onNavigateAppDirectConnect = onNavigateAppDirectConnect,
                onNavigateDirectBypassRule = onNavigateDirectBypassRule,
                onNavigateStabilitySettings = onNavigateStabilitySettings,
                onNavigateDebugLog = onNavigateDebugLog,
                onNavigatePackages = { selectedTab = 2 },
                isVip = connectState.isVip,
                expiresAt = connectState.dashboardExpiresAt ?: profileState.subscription?.expires_at,
                connectionScenarioLabel = connectState.connectionScenarioLabel,
                onNavigateDevices = onNavigateDevices,
                onOpenConnectionScenario = { selectedTab = 0 },
                isLoggedIn = isLoggedIn,
                accountFallbackEmail = accountFallbackEmail,
            )
        }
    }
}

private fun resolveEntryLatency(
    nodesState: com.vpn.member.ui.viewmodel.NodesUiState,
    connectState: com.vpn.member.ui.viewmodel.ConnectUiState,
): Int? {
    val nodeName =
        connectState.connectedNodeName
            ?: connectState.selectedNode
            ?: return null
    val node = nodesState.nodes.find { it.name == nodeName } ?: return null
    return nodesState.latencyMap[node.id]
}
