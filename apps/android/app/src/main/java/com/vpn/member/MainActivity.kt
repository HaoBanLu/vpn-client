package com.vpn.member

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import com.vpn.member.ui.theme.KuayunTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.core.content.ContextCompat
import com.vpn.member.ui.navigation.Routes
import com.vpn.member.ui.screens.AboutScreen
import com.vpn.member.ui.screens.AppDirectConnectScreen
import com.vpn.member.ui.screens.DirectBypassRuleScreen
import com.vpn.member.ui.screens.HelpScreen
import com.vpn.member.ui.screens.LoginScreen
import com.vpn.member.ui.screens.DebugLogScreen
import com.vpn.member.ui.screens.DevicesScreen
import com.vpn.member.ui.screens.MainShell
import com.vpn.member.ui.screens.ChangePasswordScreen
import com.vpn.member.ui.screens.PurchaseOrdersScreen
import com.vpn.member.ui.screens.RechargeOrdersScreen
import com.vpn.member.ui.screens.RechargeScreen
import com.vpn.member.ui.screens.ForgotPasswordScreen
import com.vpn.member.ui.screens.RegisterScreen
import com.vpn.member.ui.screens.SplashScreen
import com.vpn.member.ui.screens.StabilitySettingsScreen
import com.vpn.member.ui.screens.SupportScreen
import com.vpn.member.ui.screens.TicketsScreen
import com.vpn.member.ui.screens.TrafficScreen
import com.vpn.member.ui.components.PendingInstallDialog
import com.vpn.member.ui.components.UpdateDialog
import com.vpn.member.ui.viewmodel.AboutViewModel
import com.vpn.member.ui.viewmodel.AppDirectConnectViewModel
import com.vpn.member.ui.viewmodel.DirectBypassRuleViewModel
import com.vpn.member.ui.viewmodel.AuthViewModel
import com.vpn.member.update.AppUpdateChecker
import com.vpn.member.update.AppUpdateInstaller
import com.vpn.member.ui.viewmodel.DebugLogViewModel
import com.vpn.member.ui.viewmodel.DevicesViewModel
import com.vpn.member.ui.viewmodel.ConnectViewModel
import com.vpn.member.ui.viewmodel.HelpViewModel
import com.vpn.member.ui.viewmodel.NodesViewModel
import com.vpn.member.ui.viewmodel.PackagesViewModel
import com.vpn.member.ui.viewmodel.ProfileViewModel
import com.vpn.member.ui.viewmodel.PurchaseOrdersViewModel
import com.vpn.member.ui.viewmodel.ChangePasswordViewModel
import com.vpn.member.ui.viewmodel.RechargeOrdersViewModel
import com.vpn.member.ui.viewmodel.RechargeViewModel
import com.vpn.member.ui.viewmodel.StabilitySettingsViewModel
import com.vpn.member.ui.viewmodel.SupportViewModel
import com.vpn.member.ui.viewmodel.TicketsViewModel
import com.vpn.member.ui.viewmodel.TrafficViewModel
import com.vpn.member.data.api.ClientVersionData
import com.vpn.member.data.local.AuthDisconnectReasonStore
import com.vpn.member.data.network.ApiErrors
import com.vpn.member.notification.UserNotificationContent
import com.vpn.member.notification.UserNotificationCoordinator
import com.vpn.member.vpn.PrivacyForceDisconnectEvents
import com.vpn.member.data.session.SessionEvents
import com.vpn.member.data.session.SessionInvalidation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app by lazy { application as VpnMemberApp }
    private val appUpdateInstaller by lazy { AppUpdateInstaller(this) }

    override fun onResume() {
        super.onResume()
        appUpdateInstaller.attachActivity(this)
        // 避免在 onResume 同步 startActivity（部分 ROM 会闪退）；待安装包仅在授权返回后自动拉起。
        window.decorView.post { schedulePendingInstallResume() }
    }

    private fun schedulePendingInstallResume() {
        if (pendingInstallNotifier != null) {
            resumePendingAppInstall()
        } else {
            pendingInstallResumeDeferred = true
        }
    }

    private fun resumePendingAppInstall() {
        if (!appUpdateInstaller.hasPendingInstall()) {
            pendingInstallNotifier?.invoke(true)
            return
        }
        if (appUpdateInstaller.consumeAwaitingPermissionReturn() && appUpdateInstaller.canInstallPackages()) {
            when (appUpdateInstaller.tryInstallPendingApk()) {
                AppUpdateInstaller.InstallAttemptResult.Launched ->
                    pendingInstallNotifier?.invoke(true)
                else ->
                    pendingInstallNotifier?.invoke(false)
            }
            return
        }
        pendingInstallNotifier?.invoke(false)
    }

    private var pendingInstallNotifier: ((clearDialog: Boolean) -> Unit)? = null
    /** onResume 早于 Compose 注册回调时，延后到 UI 就绪再处理待安装包。 */
    private var pendingInstallResumeDeferred = false
    private var pendingConnect: (() -> Unit)? = null
    private var pendingNotificationConnect: (() -> Unit)? = null
    private var notificationNavRoute by mutableStateOf<String?>(null)
    private var notificationNavRouteHandler: ((String) -> Unit)? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        pendingConnect?.invoke()
        pendingConnect = null
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "可开启通知权限查看实时流量，VPN 连接不受影响", Toast.LENGTH_LONG).show()
        }
        val connectAction = pendingNotificationConnect
        pendingNotificationConnect = null
        if (connectAction != null) {
            requestVpnPermission(connectAction)
        }
    }

    private val accountNotificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                "开启通知可在后台接收账户与安全提醒",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationNavRoute = intent.getStringExtra(UserNotificationCoordinator.EXTRA_NAV_ROUTE)
        appUpdateInstaller.attachActivity(this)
        setContent {
            KuayunTheme {
                Surface {
                    AppNavHost(
                        onRequestVpnConnect = { connectAction ->
                            requestNotificationThenVpn(connectAction)
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(UserNotificationCoordinator.EXTRA_NAV_ROUTE)?.let { route ->
            notificationNavRouteHandler?.invoke(route) ?: run {
                notificationNavRoute = route
            }
        }
    }

    private fun requestAccountNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            accountNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestNotificationThenVpn(connectAction: () -> Unit) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingNotificationConnect = connectAction
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        requestVpnPermission(connectAction)
    }

    private fun requestVpnPermission(connectAction: () -> Unit) {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            pendingConnect = connectAction
            vpnPermissionLauncher.launch(intent)
        } else {
            connectAction()
        }
    }

    private fun startAppUpdate(update: ClientVersionData) {
        val label = update.latest_version_name ?: update.latest_version_code.toString()
        appUpdateInstaller.startDownload(
            update.download_url.orEmpty(),
            label,
            update.latest_version_code,
        )
    }

    @Composable
    private fun AppNavHost(onRequestVpnConnect: (() -> Unit) -> Unit) {
        val navController = rememberNavController()
        val authViewModel: AuthViewModel = viewModel(factory = app.viewModelFactory)
        val scope = rememberCoroutineScope()
        var sessionDialog by remember { mutableStateOf<SessionInvalidation?>(null) }
        var updateDialog by remember { mutableStateOf<ClientVersionData?>(null) }
        var pendingInstall by remember { mutableStateOf<AppUpdateInstaller.PendingInstallInfo?>(null) }
        var skipNextForegroundUpdateCheck by remember { mutableStateOf(true) }
        val pendingNotificationRoute = notificationNavRoute

        fun navigateFromNotification(route: String) {
            when (route) {
                UserNotificationContent.NAV_LOGIN ->
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Main) { inclusive = true }
                        launchSingleTop = true
                    }
                UserNotificationContent.NAV_RECHARGE_ORDERS ->
                    navController.navigate(Routes.RechargeOrders) { launchSingleTop = true }
                UserNotificationContent.NAV_MAIN ->
                    navController.navigate(Routes.Main) {
                        popUpTo(Routes.Splash) { inclusive = true }
                        launchSingleTop = true
                    }
            }
        }

        LaunchedEffect(pendingNotificationRoute) {
            val route = pendingNotificationRoute ?: return@LaunchedEffect
            notificationNavRoute = null
            navigateFromNotification(route)
        }

        DisposableEffect(Unit) {
            notificationNavRouteHandler = { route -> navigateFromNotification(route) }
            onDispose { notificationNavRouteHandler = null }
        }

        fun handleSessionExpired(event: SessionInvalidation) {
            app.vpnController.disconnectForAuth()
            authViewModel.logout { }
            // 接口不可达时静默回登录页，避免误报「网络不可用」
            if (event.appCode != ApiErrors.UNREACHABLE_APP_CODE) {
                sessionDialog = event
            }
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != Routes.Login && currentRoute != Routes.Register && currentRoute != Routes.Splash) {
                navController.navigate(Routes.Login) {
                    popUpTo(Routes.Main) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }

        fun dismissSessionDialog() {
            sessionDialog = null
            scope.launch {
                runCatching { UserNotificationCoordinator.lastInvalidationStore().clear() }
            }
        }

        fun handleAutoUpdateCheck(update: ClientVersionData) {
            if (!update.has_update) {
                appUpdateInstaller.readPendingInstall()
                return
            }
            if (AppUpdateChecker.shouldShowAutoPrompt(update, app.repository)) {
                updateDialog = update
            }
        }

        LaunchedEffect(Unit) {
            // 冷启动/后台被挤：SharedFlow 可能已错过，从持久化补弹一次界面对话框
            runCatching {
                val pending = UserNotificationCoordinator.lastInvalidationStore().peek()
                if (pending != null && pending.appCode != ApiErrors.UNREACHABLE_APP_CODE) {
                    sessionDialog =
                        SessionInvalidation(
                            message = pending.message,
                            appCode = pending.appCode,
                        )
                }
            }
            launch {
                SessionEvents.invalidated.collect { event ->
                    handleSessionExpired(event)
                }
            }
            launch {
                PrivacyForceDisconnectEvents.events.collect { reason ->
                    app.vpnController.disconnectForAuth()
                    val message =
                        when (reason) {
                            "traffic_exceeded" -> "流量已用尽，VPN 已断开以保护隐私"
                            "subscription_expired" -> "套餐已到期，VPN 已断开"
                            else -> "订阅状态变更，VPN 已断开"
                        }
                    Toast.makeText(app, message, Toast.LENGTH_LONG).show()
                }
            }
            runCatching { app.repository.checkForUpdateAndRecord() }
                .onSuccess(::handleAutoUpdateCheck)
        }

        DisposableEffect(Unit) {
            pendingInstall = appUpdateInstaller.readPendingInstall()
            pendingInstallNotifier = { clearDialog ->
                pendingInstall =
                    if (clearDialog) {
                        null
                    } else {
                        appUpdateInstaller.readPendingInstall()
                    }
            }
            appUpdateInstaller.setListener(
                object : AppUpdateInstaller.Listener {
                    override fun onDownloadStarted() = Unit

                    override fun onDownloadCompleted(pending: AppUpdateInstaller.PendingInstallInfo) {
                        pendingInstall = pending
                    }

                    override fun onDownloadFailed(message: String) {
                        pendingInstall = appUpdateInstaller.readPendingInstall()
                    }

                    override fun onInstallPermissionRequired(pending: AppUpdateInstaller.PendingInstallInfo) {
                        pendingInstall = pending
                    }
                },
            )
            if (pendingInstallResumeDeferred) {
                pendingInstallResumeDeferred = false
                schedulePendingInstallResume()
            }
            onDispose {
                pendingInstallNotifier = null
                appUpdateInstaller.setListener(null)
            }
        }

        DisposableEffect(Unit) {
            val observer = object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    if (skipNextForegroundUpdateCheck) {
                        skipNextForegroundUpdateCheck = false
                        return
                    }
                    if (!app.repository.shouldRunPeriodicUpdateCheck()) return
                    scope.launch {
                        runCatching { app.repository.checkForUpdateAndRecord() }
                            .onSuccess(::handleAutoUpdateCheck)
                    }
                }
            }
            ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
            onDispose {
                ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
            }
        }

        sessionDialog?.let { event ->
            val isUnreachable = event.appCode == ApiErrors.UNREACHABLE_APP_CODE
            AlertDialog(
                onDismissRequest = { dismissSessionDialog() },
                title = { Text(if (isUnreachable) "无法连接服务器" else "登录状态已失效") },
                text = { Text(event.message) },
                confirmButton = {
                    TextButton(onClick = { dismissSessionDialog() }) {
                        Text("知道了")
                    }
                },
            )
        }

        pendingInstall?.let { pending ->
            PendingInstallDialog(
                pending = pending,
                needsInstallPermission = !appUpdateInstaller.canInstallPackages(),
                onInstall = {
                    when (appUpdateInstaller.tryInstallPendingApk()) {
                        AppUpdateInstaller.InstallAttemptResult.Launched -> {
                            pendingInstall = null
                        }
                        AppUpdateInstaller.InstallAttemptResult.NeedPermission -> {
                            appUpdateInstaller.openInstallPermissionSettings()
                        }
                        AppUpdateInstaller.InstallAttemptResult.Failed -> {
                            Toast.makeText(app, "无法打开安装程序，请稍后重试", Toast.LENGTH_LONG).show()
                        }
                        else -> Unit
                    }
                },
                onOpenPermissionSettings = {
                    appUpdateInstaller.openInstallPermissionSettings()
                },
                onDismiss = {
                    pendingInstall = null
                },
            )
        }

        updateDialog?.let { update ->
            UpdateDialog(
                update = update,
                onConfirm = {
                    startAppUpdate(update)
                    updateDialog = null
                },
                onDismiss = {
                    app.repository.dismissUpdate(update.latest_version_code)
                    updateDialog = null
                },
            )
        }

        NavHost(navController = navController, startDestination = Routes.Splash) {
            composable(Routes.Splash) {
                SplashScreen()
                LaunchedEffect(Unit) {
                    delay(600)
                    val destination = if (app.repository.isLoggedIn) Routes.Main else Routes.Login
                    navController.navigate(destination) {
                        popUpTo(Routes.Splash) { inclusive = true }
                    }
                }
            }
            composable(Routes.Login) {
                val state by authViewModel.state.collectAsStateWithLifecycle()
                var loginBanner by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    // peek 而非 consume：主界面对话框关闭时再 clear，避免只剩通知栏
                    val pending = UserNotificationCoordinator.lastInvalidationStore().peek()
                    loginBanner = pending?.let { "${it.title}：${it.message}" }
                }
                LoginScreen(
                    state = state,
                    bannerMessage = loginBanner,
                    onLogin = { email, password, rememberLogin ->
                        authViewModel.login(email, password, rememberLogin) {
                            AuthDisconnectReasonStore.clear(app)
                            app.vpnController.releaseKillSwitch()
                            com.vpn.member.vpn.VpnConnectionBus.resetForSessionEnd()
                            requestAccountNotificationPermission()
                            com.vpn.member.vpn.mihomo.MihomoWarmup.schedule(app)
                            navController.navigate(Routes.Main) {
                                popUpTo(Routes.Login) { inclusive = true }
                            }
                        }
                    },
                    onNavigateRegister = { navController.navigate(Routes.Register) },
                    onNavigateForgotPassword = { navController.navigate(Routes.ForgotPassword) },
                )
            }
            composable(Routes.Register) {
                val state by authViewModel.state.collectAsStateWithLifecycle()
                RegisterScreen(
                    state = state,
                    onRegister = { email, password, emailCode ->
                        authViewModel.register(email, password, emailCode) {
                            requestAccountNotificationPermission()
                            com.vpn.member.vpn.mihomo.MihomoWarmup.schedule(app)
                            navController.navigate(Routes.Main) {
                                popUpTo(Routes.Login) { inclusive = true }
                            }
                        }
                    },
                    onSendCode = authViewModel::sendRegisterCode,
                    onNavigateLogin = { navController.popBackStack() },
                )
            }
            composable(Routes.ForgotPassword) {
                val state by authViewModel.state.collectAsStateWithLifecycle()
                ForgotPasswordScreen(
                    state = state,
                    onSendCode = authViewModel::sendResetCode,
                    onResetPassword = { email, emailCode, newPassword ->
                        authViewModel.resetPassword(email, emailCode, newPassword) {
                            navController.popBackStack()
                        }
                    },
                    onNavigateLogin = { navController.popBackStack() },
                )
            }
            composable(Routes.Main) {
                val connectViewModel: ConnectViewModel = viewModel(factory = app.viewModelFactory)
                val nodesViewModel: NodesViewModel = viewModel(factory = app.viewModelFactory)
                val packagesViewModel: PackagesViewModel = viewModel(factory = app.viewModelFactory)
                val profileViewModel: ProfileViewModel = viewModel(factory = app.viewModelFactory)

                val connectState by connectViewModel.state.collectAsStateWithLifecycle()
                val nodesState by nodesViewModel.state.collectAsStateWithLifecycle()
                val packagesState by packagesViewModel.state.collectAsStateWithLifecycle()
                val profileState by profileViewModel.state.collectAsStateWithLifecycle()
                val authState by authViewModel.state.collectAsStateWithLifecycle()

                MainShell(
                    connectViewModel = connectViewModel,
                    nodesViewModel = nodesViewModel,
                    packagesViewModel = packagesViewModel,
                    profileViewModel = profileViewModel,
                    connectState = connectState,
                    nodesState = nodesState,
                    packagesState = packagesState,
                    profileState = profileState,
                    onRequestVpnConnect = { connectAction ->
                        connectViewModel.onConnectIntent()
                        onRequestVpnConnect(connectAction)
                    },
                    onNavigateTraffic = { navController.navigate(Routes.Traffic) },
                    onNavigateRecharge = { navController.navigate(Routes.Recharge) },
                    onNavigateRechargeOrders = { navController.navigate(Routes.RechargeOrders) },
                    onNavigatePurchaseOrders = { navController.navigate(Routes.PurchaseOrders) },
                    onNavigateChangePassword = { navController.navigate(Routes.ChangePassword) },
                    onNavigateTickets = { navController.navigate(Routes.Tickets) },
                    onNavigateSupport = { navController.navigate(Routes.Support) },
                    onNavigateHelp = { navController.navigate(Routes.Help) },
                    onNavigateAbout = { navController.navigate(Routes.About) },
                    onNavigateAppDirectConnect = { navController.navigate(Routes.AppDirectConnect) },
                    onNavigateDirectBypassRule = { navController.navigate(Routes.DirectBypassRule) },
                    onNavigateStabilitySettings = { navController.navigate(Routes.StabilitySettings) },
                    onNavigateDebugLog = { navController.navigate(Routes.DebugLog) },
                    onNavigateDevices = { navController.navigate(Routes.Devices) },
                    onLogout = {
                        app.vpnController.disconnectForAuth()
                        authViewModel.logout {
                            navController.navigate(Routes.Login) {
                                popUpTo(Routes.Main) { inclusive = true }
                            }
                        }
                    },
                    isLoggedIn = app.repository.isLoggedIn,
                    accountFallbackEmail = authState.savedEmail.ifBlank { "已登录账号" },
                )
            }
            composable(Routes.Help) {
                val helpViewModel: HelpViewModel = viewModel(factory = app.viewModelFactory)
                val state by helpViewModel.state.collectAsStateWithLifecycle()
                HelpScreen(
                    state = state,
                    onLoadSubscriptionUrl = helpViewModel::loadSubscriptionUrl,
                    onCopied = helpViewModel::markCopied,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.About) {
                val aboutViewModel: AboutViewModel = viewModel(factory = app.viewModelFactory)
                val aboutState by aboutViewModel.state.collectAsStateWithLifecycle()
                AboutScreen(
                    state = aboutState,
                    onCheckUpdate = aboutViewModel::checkUpdate,
                    onStartUpdate = ::startAppUpdate,
                    pendingInstallVersion = appUpdateInstaller.readPendingInstall()?.versionLabel,
                    onContinueInstall = {
                        when (appUpdateInstaller.tryInstallPendingApk()) {
                            AppUpdateInstaller.InstallAttemptResult.NeedPermission ->
                                appUpdateInstaller.openInstallPermissionSettings()
                            AppUpdateInstaller.InstallAttemptResult.Failed ->
                                Toast.makeText(app, "无法打开安装程序，请稍后重试", Toast.LENGTH_LONG).show()
                            else -> resumePendingAppInstall()
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.AppDirectConnect) {
                val appDirectConnectViewModel: AppDirectConnectViewModel = viewModel(factory = app.viewModelFactory)
                val appDirectConnectState by appDirectConnectViewModel.state.collectAsStateWithLifecycle()
                val profileViewModel: ProfileViewModel =
                    viewModel(
                        viewModelStoreOwner = navController.getBackStackEntry(Routes.Main),
                        factory = app.viewModelFactory,
                    )
                AppDirectConnectScreen(
                    state = appDirectConnectState,
                    onQueryChange = appDirectConnectViewModel::setQuery,
                    onToggle = appDirectConnectViewModel::toggleDirectConnect,
                    onDismissToast = appDirectConnectViewModel::dismissToast,
                    onRefreshApps = appDirectConnectViewModel::load,
                    onBack = {
                        profileViewModel.refresh()
                        navController.popBackStack()
                    },
                )
            }
            composable(Routes.DirectBypassRule) {
                val directBypassRuleViewModel: DirectBypassRuleViewModel = viewModel(factory = app.viewModelFactory)
                val directBypassRuleState by directBypassRuleViewModel.state.collectAsStateWithLifecycle()
                val profileViewModel: ProfileViewModel =
                    viewModel(
                        viewModelStoreOwner = navController.getBackStackEntry(Routes.Main),
                        factory = app.viewModelFactory,
                    )
                DirectBypassRuleScreen(
                    state = directBypassRuleState,
                    onOpenAddDialog = directBypassRuleViewModel::openAddDialog,
                    onDismissAddDialog = directBypassRuleViewModel::dismissAddDialog,
                    onAddTypeChange = directBypassRuleViewModel::setAddType,
                    onAddValueChange = directBypassRuleViewModel::setAddValue,
                    onConfirmAdd = directBypassRuleViewModel::confirmAddRule,
                    onToggle = directBypassRuleViewModel::toggleRule,
                    onDelete = directBypassRuleViewModel::deleteRule,
                    onDismissToast = directBypassRuleViewModel::dismissToast,
                    onBack = {
                        profileViewModel.refresh()
                        navController.popBackStack()
                    },
                )
            }
            composable(Routes.StabilitySettings) {
                val stabilityViewModel: StabilitySettingsViewModel = viewModel(factory = app.viewModelFactory)
                val stabilityState by stabilityViewModel.state.collectAsStateWithLifecycle()
                StabilitySettingsScreen(
                    state = stabilityState,
                    onBack = { navController.popBackStack() },
                    onAutoReconnectChanged = stabilityViewModel::setAutoReconnectEnabled,
                    onBootAutoConnectChanged = stabilityViewModel::setBootAutoConnectEnabled,
                    onTunStackModeChanged = stabilityViewModel::setTunStackMode,
                    onOpenBatterySettings = stabilityViewModel::openBatterySettings,
                    onOpenVpnSettings = stabilityViewModel::openVpnSettings,
                    onRunPrivacyProbe = stabilityViewModel::runPrivacyProbe,
                    onToggleAdvanced = stabilityViewModel::toggleAdvanced,
                    onBlockOnConnectFailureChanged = stabilityViewModel::setBlockOnConnectFailureEnabled,
                    onRequestDisableKillSwitch = stabilityViewModel::requestDisableKillSwitch,
                    onConfirmDisableKillSwitch = stabilityViewModel::confirmDisableKillSwitch,
                    onDismissDisableKillSwitchConfirm = stabilityViewModel::dismissDisableKillSwitchConfirm,
                    onDismissToast = stabilityViewModel::dismissToast,
                )
            }
            composable(Routes.Recharge) {
                val rechargeViewModel: RechargeViewModel = viewModel(factory = app.viewModelFactory)
                val state by rechargeViewModel.state.collectAsStateWithLifecycle()
                RechargeScreen(
                    state = state,
                    onRefresh = rechargeViewModel::refresh,
                    onAmountChange = rechargeViewModel::setAmount,
                    onFromAddressChange = rechargeViewModel::setFromAddress,
                    onTxidChange = rechargeViewModel::setTxid,
                    onPickProof = rechargeViewModel::uploadProof,
                    onCreateOrder = rechargeViewModel::createOrder,
                    onSubmitProof = rechargeViewModel::submitProof,
                    onSaveTransferHint = rechargeViewModel::saveTransferHint,
                    onCancelOrder = rechargeViewModel::cancelOrder,
                    onRestartRecharge = rechargeViewModel::startFreshRecharge,
                    onViewOrders = { navController.navigate(Routes.RechargeOrders) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.RechargeOrders) {
                val ordersViewModel: RechargeOrdersViewModel = viewModel(factory = app.viewModelFactory)
                val state by ordersViewModel.state.collectAsStateWithLifecycle()
                RechargeOrdersScreen(
                    state = state,
                    onRefresh = ordersViewModel::refresh,
                    onSelectOrder = ordersViewModel::selectOrder,
                    onRechargeAgain = {
                        navController.navigate(Routes.Recharge) {
                            popUpTo(Routes.RechargeOrders) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.PurchaseOrders) {
                val purchaseOrdersViewModel: PurchaseOrdersViewModel = viewModel(factory = app.viewModelFactory)
                val state by purchaseOrdersViewModel.state.collectAsStateWithLifecycle()
                PurchaseOrdersScreen(
                    state = state,
                    onRefresh = purchaseOrdersViewModel::refresh,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ChangePassword) {
                val changePasswordViewModel: ChangePasswordViewModel = viewModel(factory = app.viewModelFactory)
                val state by changePasswordViewModel.state.collectAsStateWithLifecycle()
                ChangePasswordScreen(
                    state = state,
                    onChangePassword = changePasswordViewModel::changePassword,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.Support) {
                val supportViewModel: SupportViewModel = viewModel(factory = app.viewModelFactory)
                val state by supportViewModel.state.collectAsStateWithLifecycle()
                SupportScreen(
                    state = state,
                    onRefresh = supportViewModel::refresh,
                    onOpenTickets = { navController.navigate(Routes.Tickets) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.Tickets) {
                val ticketsViewModel: TicketsViewModel = viewModel(factory = app.viewModelFactory)
                val state by ticketsViewModel.state.collectAsStateWithLifecycle()
                TicketsScreen(
                    state = state,
                    onRefresh = ticketsViewModel::refresh,
                    onToggleCreate = ticketsViewModel::toggleCreateForm,
                    onCreateTitleChange = ticketsViewModel::setCreateTitle,
                    onCreateContentChange = ticketsViewModel::setCreateContent,
                    onCreatePriorityChange = ticketsViewModel::setCreatePriority,
                    onCreateTicket = ticketsViewModel::createTicket,
                    onSelectTicket = ticketsViewModel::selectTicket,
                    onReplyContentChange = ticketsViewModel::setReplyContent,
                    onSubmitReply = ticketsViewModel::submitReply,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.DebugLog) {
                val debugLogViewModel: DebugLogViewModel = viewModel(factory = app.viewModelFactory)
                val debugState by debugLogViewModel.state.collectAsStateWithLifecycle()
                DebugLogScreen(
                    state = debugState,
                    onBack = { navController.popBackStack() },
                    onUploadNow = debugLogViewModel::uploadNow,
                )
            }
            composable(Routes.Devices) {
                val devicesViewModel: DevicesViewModel = viewModel(factory = app.viewModelFactory)
                val devicesState by devicesViewModel.state.collectAsStateWithLifecycle()
                DevicesScreen(
                    state = devicesState,
                    onRefresh = devicesViewModel::refresh,
                    onRevoke = devicesViewModel::revokeSession,
                    onBack = { navController.popBackStack() },
                    onDismissToast = devicesViewModel::dismissToast,
                )
            }
            composable(Routes.Traffic) {
                val trafficViewModel: TrafficViewModel = viewModel(factory = app.viewModelFactory)
                val state by trafficViewModel.state.collectAsStateWithLifecycle()
                TrafficScreen(
                    state = state,
                    onRefresh = trafficViewModel::refresh,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
