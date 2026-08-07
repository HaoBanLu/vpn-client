package com.vpn.member.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpn.member.data.device.LaunchableApp
import com.vpn.member.data.repository.AppRepository
import com.vpn.member.data.session.AppEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppDirectConnectItem(
    val packageName: String,
    val label: String,
    val directEnabled: Boolean,
)

data class AppDirectConnectUiState(
    val loading: Boolean = true,
    val query: String = "",
    val apps: List<AppDirectConnectItem> = emptyList(),
    val selectedCount: Int = 0,
    val totalInstalledCount: Int = 0,
    val needsInstalledAppsPermission: Boolean = false,
    val toastMessage: String? = null,
    val error: String? = null,
)

class AppDirectConnectViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private var allApps: List<LaunchableApp> = emptyList()
    private var selectedPackages: Set<String> = emptySet()

    private val _state = MutableStateFlow(AppDirectConnectUiState())
    val state: StateFlow<AppDirectConnectUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.listLaunchableApps()
                }
            }.onSuccess { apps ->
                allApps = apps
                selectedPackages = repository.getDirectConnectPackages()
                publishFiltered(
                    needsInstalledAppsPermission = !repository.isInstalledAppsPermissionGranted(),
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "加载应用列表失败",
                )
            }
        }
    }

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query)
        publishFiltered()
    }

    fun toggleDirectConnect(packageName: String, enabled: Boolean) {
        selectedPackages =
            if (enabled) {
                selectedPackages + packageName
            } else {
                selectedPackages - packageName
            }
        repository.setDirectConnectPackages(selectedPackages)
        publishFiltered()
        AppEvents.notifyVpnConfigChanged()
        _state.value =
            _state.value.copy(
                toastMessage = "已保存，正在应用直连设置…",
            )
    }

    fun dismissToast() {
        _state.value = _state.value.copy(toastMessage = null)
    }

    private fun publishFiltered(needsInstalledAppsPermission: Boolean = _state.value.needsInstalledAppsPermission) {
        val query = _state.value.query.trim().lowercase()
        val filtered = filterLaunchableApps(allApps, query)
        _state.value =
            _state.value.copy(
                loading = false,
                apps =
                    filtered.map { app ->
                        AppDirectConnectItem(
                            packageName = app.packageName,
                            label = app.label,
                            directEnabled = app.packageName in selectedPackages,
                        )
                    },
                selectedCount = selectedPackages.size,
                totalInstalledCount = allApps.size,
                needsInstalledAppsPermission = needsInstalledAppsPermission,
            )
    }

    internal companion object {
        fun filterLaunchableApps(apps: List<LaunchableApp>, query: String): List<LaunchableApp> {
            val normalized = query.trim().lowercase()
            if (normalized.isBlank()) return apps
            return apps.filter { app ->
                app.label.lowercase().contains(normalized) || app.packageName.lowercase().contains(normalized)
            }
        }
    }
}
