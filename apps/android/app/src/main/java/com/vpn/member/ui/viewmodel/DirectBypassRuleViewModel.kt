package com.vpn.member.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.vpn.member.data.repository.AppRepository
import com.vpn.member.data.session.AppEvents
import com.vpn.member.vpn.DirectBypassRule
import com.vpn.member.vpn.DirectBypassRuleStore
import com.vpn.member.vpn.DirectBypassRuleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DirectBypassRuleItem(
    val id: String,
    val type: DirectBypassRuleType,
    val typeLabel: String,
    val value: String,
    val enabled: Boolean,
)

data class DirectBypassRuleUiState(
    val rules: List<DirectBypassRuleItem> = emptyList(),
    val enabledCount: Int = 0,
    val showAddDialog: Boolean = false,
    val addType: DirectBypassRuleType = DirectBypassRuleType.DOMAIN_SUFFIX,
    val addValue: String = "",
    val addError: String? = null,
    val toastMessage: String? = null,
)

class DirectBypassRuleViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DirectBypassRuleUiState())
    val state: StateFlow<DirectBypassRuleUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        publish(repository.getDirectBypassRules())
    }

    fun openAddDialog() {
        _state.value =
            _state.value.copy(
                showAddDialog = true,
                addType = DirectBypassRuleType.DOMAIN_SUFFIX,
                addValue = "",
                addError = null,
            )
    }

    fun dismissAddDialog() {
        _state.value =
            _state.value.copy(
                showAddDialog = false,
                addValue = "",
                addError = null,
            )
    }

    fun setAddType(type: DirectBypassRuleType) {
        _state.value = _state.value.copy(addType = type, addError = null)
    }

    fun setAddValue(value: String) {
        _state.value = _state.value.copy(addValue = value, addError = null)
    }

    fun confirmAddRule() {
        val current = _state.value
        val result = DirectBypassRuleStore.createRule(current.addType, current.addValue)
        result.onSuccess { newRule ->
            if (isDuplicate(newRule, repository.getDirectBypassRules())) {
                _state.value = current.copy(addError = "相同规则已存在")
                return
            }
            val updated = repository.getDirectBypassRules() + newRule
            repository.setDirectBypassRules(updated)
            publish(updated)
            AppEvents.notifyVpnConfigChanged()
            _state.value =
                _state.value.copy(
                    showAddDialog = false,
                    addValue = "",
                    addError = null,
                    toastMessage = "已保存，正在应用规则…",
                )
        }.onFailure { error ->
            _state.value = current.copy(addError = error.message ?: "规则格式不正确")
        }
    }

    fun toggleRule(id: String, enabled: Boolean) {
        val updated =
            repository.getDirectBypassRules().map { rule ->
                if (rule.id == id) rule.copy(enabled = enabled) else rule
            }
        repository.setDirectBypassRules(updated)
        publish(updated)
        AppEvents.notifyVpnConfigChanged()
        _state.value = _state.value.copy(toastMessage = "已保存，正在应用规则…")
    }

    fun deleteRule(id: String) {
        val updated = repository.getDirectBypassRules().filterNot { it.id == id }
        repository.setDirectBypassRules(updated)
        publish(updated)
        AppEvents.notifyVpnConfigChanged()
        _state.value = _state.value.copy(toastMessage = "已保存，正在应用规则…")
    }

    fun dismissToast() {
        _state.value = _state.value.copy(toastMessage = null)
    }

    private fun publish(rules: List<DirectBypassRule>) {
        _state.value =
            _state.value.copy(
                rules = rules.map { it.toItem() },
                enabledCount = rules.count { it.enabled },
            )
    }

    internal companion object {
        fun isDuplicate(newRule: DirectBypassRule, existing: List<DirectBypassRule>): Boolean =
            existing.any {
                it.type == newRule.type &&
                    it.value.equals(newRule.value, ignoreCase = true)
            }

        fun DirectBypassRule.toItem(): DirectBypassRuleItem =
            DirectBypassRuleItem(
                id = id,
                type = type,
                typeLabel = type.label,
                value = value,
                enabled = enabled,
            )
    }
}
