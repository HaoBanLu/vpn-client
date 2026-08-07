package com.vpn.member.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpn.member.data.api.TicketItem
import com.vpn.member.data.network.ApiRequestSupport
import com.vpn.member.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TicketsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val tickets: List<TicketItem> = emptyList(),
    val selectedTicket: TicketItem? = null,
    val detailLoading: Boolean = false,
    val showCreateForm: Boolean = false,
    val creating: Boolean = false,
    val replying: Boolean = false,
    val createTitle: String = "",
    val createContent: String = "",
    val createPriority: String = "normal",
    val replyContent: String = "",
    val error: String? = null,
    val message: String? = null,
)

class TicketsViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TicketsUiState())
    val state: StateFlow<TicketsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val hasData = _state.value.tickets.isNotEmpty()
            _state.value = _state.value.copy(
                loading = !hasData,
                refreshing = hasData,
                error = null,
            )
            runCatching { repository.getMyTickets() }
                .onSuccess { data ->
                    val selectedId = _state.value.selectedTicket?.id
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        tickets = data.tickets,
                    )
                    selectedId?.let { reloadDetail(it, silent = true) }
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        error = ApiRequestSupport.mapError(e, "加载工单失败"),
                    )
                }
        }
    }

    fun toggleCreateForm() {
        _state.value = _state.value.copy(
            showCreateForm = !_state.value.showCreateForm,
            message = null,
            error = null,
        )
    }

    fun setCreateTitle(value: String) {
        _state.value = _state.value.copy(createTitle = value)
    }

    fun setCreateContent(value: String) {
        _state.value = _state.value.copy(createContent = value)
    }

    fun setCreatePriority(value: String) {
        _state.value = _state.value.copy(createPriority = value)
    }

    fun setReplyContent(value: String) {
        _state.value = _state.value.copy(replyContent = value)
    }

    fun selectTicket(ticket: TicketItem?) {
        if (ticket == null) {
            _state.value = _state.value.copy(selectedTicket = null, replyContent = "")
            return
        }
        if (_state.value.selectedTicket?.id == ticket.id) {
            _state.value = _state.value.copy(selectedTicket = null, replyContent = "")
            return
        }
        reloadDetail(ticket.id)
    }

    fun createTicket() {
        val title = _state.value.createTitle.trim()
        val content = _state.value.createContent.trim()
        if (title.isEmpty() || content.isEmpty()) {
            _state.value = _state.value.copy(error = "请填写工单标题和问题描述")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(creating = true, error = null, message = null)
            runCatching {
                repository.createTicket(title, content, _state.value.createPriority)
            }.onSuccess { ticket ->
                _state.value = _state.value.copy(
                    creating = false,
                    showCreateForm = false,
                    createTitle = "",
                    createContent = "",
                    createPriority = "normal",
                    message = "工单已提交",
                )
                refresh()
                reloadDetail(ticket.id)
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    creating = false,
                    error = ApiRequestSupport.mapError(e, "创建工单失败"),
                )
            }
        }
    }

    fun submitReply() {
        val ticket = _state.value.selectedTicket ?: return
        val content = _state.value.replyContent.trim()
        if (content.isEmpty()) {
            _state.value = _state.value.copy(error = "请输入回复内容")
            return
        }
        if (ticket.status == "closed") {
            _state.value = _state.value.copy(error = "工单已关闭，无法继续回复")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(replying = true, error = null, message = null)
            runCatching { repository.addTicketReply(ticket.id, content) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        replying = false,
                        replyContent = "",
                        message = "回复已提交",
                    )
                    reloadDetail(ticket.id)
                    refresh()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        replying = false,
                        error = ApiRequestSupport.mapError(e, "回复失败"),
                    )
                }
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun reloadDetail(ticketId: Long, silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _state.value = _state.value.copy(detailLoading = true, error = null)
            }
            runCatching { repository.getTicketById(ticketId) }
                .onSuccess { ticket ->
                    _state.value = _state.value.copy(
                        detailLoading = false,
                        selectedTicket = ticket,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        detailLoading = false,
                        error = ApiRequestSupport.mapError(e, "加载工单详情失败"),
                    )
                }
        }
    }
}
