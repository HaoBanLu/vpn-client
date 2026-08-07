package com.vpn.member

import android.app.Application
import com.vpn.member.data.api.ApiClient
import com.vpn.member.data.local.AppPreferences
import com.vpn.member.data.local.TokenStore
import com.vpn.member.data.repository.AppRepository
import com.vpn.member.debug.AppDebugLogger
import com.vpn.member.session.SessionHeartbeatManager
import com.vpn.member.ui.AppViewModelFactory
import com.vpn.member.data.network.NetworkMonitor
import com.vpn.member.vpn.PrivacyBaselineMigrator
import com.vpn.member.vpn.VpnController
import com.vpn.member.vpn.NetworkServices
import com.vpn.member.notification.UserNotificationCoordinator
import com.vpn.member.push.FcmPushBootstrap
import com.vpn.member.vpn.VpnCrashRecovery
import com.vpn.member.vpn.VpnReconnectSupervisor
import com.vpn.member.vpn.mihomo.MihomoWarmup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VpnMemberApp : Application() {
    lateinit var repository: AppRepository
        private set
    lateinit var vpnController: VpnController
        private set
    lateinit var reconnectSupervisor: VpnReconnectSupervisor
        private set
    lateinit var viewModelFactory: AppViewModelFactory
        private set

    private lateinit var sessionHeartbeatManager: SessionHeartbeatManager

    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        VpnCrashRecovery.install(this)
        NetworkServices.init(this)
        val tokenStore = TokenStore(this)
        val preferences = AppPreferences(this)
        PrivacyBaselineMigrator.migrateIfNeeded(preferences)
        repository = AppRepository(ApiClient.create(tokenStore), tokenStore, preferences, this)
        vpnController = VpnController(this)
        reconnectSupervisor =
            VpnReconnectSupervisor(
                context = this,
                repository = repository,
                vpnController = vpnController,
                scope = initScope,
            )
        reconnectSupervisor.start()
        viewModelFactory = AppViewModelFactory(repository, vpnController, reconnectSupervisor)
        sessionHeartbeatManager = SessionHeartbeatManager(repository, initScope)
        sessionHeartbeatManager.start()
        UserNotificationCoordinator.start(this, initScope) {
            vpnController.disconnectForAuth()
        }
        FcmPushBootstrap.start(this, initScope) { token ->
            repository.syncPushToken(token)
        }
        NetworkMonitor.start(this)
        refreshAppDebugLogger()
        repository.ensurePrivacyAcceptedIfLoggedIn()
        if (repository.isLoggedIn) {
            MihomoWarmup.schedule(this)
            VpnCrashRecovery.scheduleRestoreIfNeeded(this)
        }
    }

    fun refreshAppDebugLogger() {
        AppDebugLogger.configure(
            enabled = repository.isAppDebugEnabled(),
            scope = initScope,
            upload = { entries -> repository.uploadAppDebugLogs(entries) },
        )
    }
}
