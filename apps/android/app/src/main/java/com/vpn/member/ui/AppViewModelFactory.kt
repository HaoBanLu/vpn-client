package com.vpn.member.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vpn.member.data.repository.AppRepository
import com.vpn.member.ui.viewmodel.AboutViewModel
import com.vpn.member.ui.viewmodel.AppDirectConnectViewModel
import com.vpn.member.ui.viewmodel.DirectBypassRuleViewModel
import com.vpn.member.ui.viewmodel.AuthViewModel
import com.vpn.member.ui.viewmodel.ConnectViewModel
import com.vpn.member.ui.viewmodel.DebugLogViewModel
import com.vpn.member.ui.viewmodel.DevicesViewModel
import com.vpn.member.ui.viewmodel.HelpViewModel
import com.vpn.member.ui.viewmodel.NodesViewModel
import com.vpn.member.ui.viewmodel.PackagesViewModel
import com.vpn.member.ui.viewmodel.ChangePasswordViewModel
import com.vpn.member.ui.viewmodel.ProfileViewModel
import com.vpn.member.ui.viewmodel.PurchaseOrdersViewModel
import com.vpn.member.ui.viewmodel.RechargeOrdersViewModel
import com.vpn.member.ui.viewmodel.RechargeViewModel
import com.vpn.member.ui.viewmodel.StabilitySettingsViewModel
import com.vpn.member.ui.viewmodel.SupportViewModel
import com.vpn.member.ui.viewmodel.TicketsViewModel
import com.vpn.member.ui.viewmodel.TrafficViewModel
import com.vpn.member.vpn.VpnController
import com.vpn.member.vpn.VpnReconnectSupervisor

class AppViewModelFactory(
    private val repository: AppRepository,
    private val vpnController: VpnController,
    private val reconnectSupervisor: VpnReconnectSupervisor,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AboutViewModel::class.java) ->
                AboutViewModel(repository) as T
            modelClass.isAssignableFrom(AppDirectConnectViewModel::class.java) ->
                AppDirectConnectViewModel(repository) as T
            modelClass.isAssignableFrom(DirectBypassRuleViewModel::class.java) ->
                DirectBypassRuleViewModel(repository) as T
            modelClass.isAssignableFrom(AuthViewModel::class.java) ->
            AuthViewModel(repository, vpnController) as T
            modelClass.isAssignableFrom(ConnectViewModel::class.java) ->
                ConnectViewModel(repository, vpnController, reconnectSupervisor) as T
            modelClass.isAssignableFrom(DebugLogViewModel::class.java) ->
                DebugLogViewModel(repository) as T
            modelClass.isAssignableFrom(HelpViewModel::class.java) ->
                HelpViewModel(repository) as T
            modelClass.isAssignableFrom(NodesViewModel::class.java) ->
                NodesViewModel(repository) as T
            modelClass.isAssignableFrom(PackagesViewModel::class.java) ->
                PackagesViewModel(repository) as T
            modelClass.isAssignableFrom(ProfileViewModel::class.java) ->
                ProfileViewModel(repository) as T
            modelClass.isAssignableFrom(PurchaseOrdersViewModel::class.java) ->
                PurchaseOrdersViewModel(repository) as T
            modelClass.isAssignableFrom(TrafficViewModel::class.java) ->
                TrafficViewModel(repository) as T
            modelClass.isAssignableFrom(RechargeViewModel::class.java) ->
                RechargeViewModel(repository) as T
            modelClass.isAssignableFrom(RechargeOrdersViewModel::class.java) ->
                RechargeOrdersViewModel(repository) as T
            modelClass.isAssignableFrom(ChangePasswordViewModel::class.java) ->
                ChangePasswordViewModel(repository) as T
            modelClass.isAssignableFrom(TicketsViewModel::class.java) ->
                TicketsViewModel(repository) as T
            modelClass.isAssignableFrom(SupportViewModel::class.java) ->
                SupportViewModel(repository) as T
            modelClass.isAssignableFrom(DevicesViewModel::class.java) ->
                DevicesViewModel(repository) as T
            modelClass.isAssignableFrom(StabilitySettingsViewModel::class.java) ->
                StabilitySettingsViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
