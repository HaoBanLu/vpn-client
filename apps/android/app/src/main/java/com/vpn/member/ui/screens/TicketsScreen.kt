package com.vpn.member.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpn.member.data.api.TicketItem
import com.vpn.member.data.api.TicketReplyItem
import com.vpn.member.ui.components.KuayunBackHeader
import com.vpn.member.ui.components.KuayunInfoCard
import com.vpn.member.ui.components.KuayunListCard
import com.vpn.member.ui.components.KuayunPullRefreshBox
import com.vpn.member.ui.components.KuayunScreenBackground
import com.vpn.member.ui.components.KuayunSectionTitle
import com.vpn.member.ui.components.KuayunStateBlock
import com.vpn.member.ui.viewmodel.TicketsUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TicketsScreen(
    state: TicketsUiState,
    onRefresh: () -> Unit,
    onToggleCreate: () -> Unit,
    onCreateTitleChange: (String) -> Unit,
    onCreateContentChange: (String) -> Unit,
    onCreatePriorityChange: (String) -> Unit,
    onCreateTicket: () -> Unit,
    onSelectTicket: (TicketItem?) -> Unit,
    onReplyContentChange: (String) -> Unit,
    onSubmitReply: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KuayunScreenBackground(modifier = modifier.fillMaxSize()) {
        KuayunPullRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    KuayunBackHeader(
                        title = "我的工单",
                        subtitle = "问题反馈与客服回复",
                        onBack = onBack,
                        trailingAction = {
                            TextButton(onClick = onToggleCreate) {
                                Text(if (state.showCreateForm) "取消" else "新建")
                            }
                        },
                    )
                }
                state.message?.let { message ->
                    item {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                state.error?.let { error ->
                    item {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (state.showCreateForm) {
                    item {
                        CreateTicketForm(
                            title = state.createTitle,
                            content = state.createContent,
                            priority = state.createPriority,
                            creating = state.creating,
                            onTitleChange = onCreateTitleChange,
                            onContentChange = onCreateContentChange,
                            onPriorityChange = onCreatePriorityChange,
                            onSubmit = onCreateTicket,
                        )
                    }
                }
                item {
                    KuayunStateBlock(
                        loading = state.loading,
                        empty = !state.loading && state.tickets.isEmpty() && !state.showCreateForm,
                        emptyMessage = "暂无工单，点击右上角新建反馈问题",
                        error = null,
                    )
                }
                if (!state.loading && state.tickets.isEmpty() && !state.showCreateForm) {
                    item {
                        Button(
                            onClick = onToggleCreate,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("新建工单")
                        }
                    }
                }
                items(state.tickets) { ticket ->
                    TicketCard(
                        ticket = ticket,
                        selected = state.selectedTicket?.id == ticket.id,
                        onClick = { onSelectTicket(ticket) },
                    )
                }
                state.selectedTicket?.let { ticket ->
                    item {
                        TicketDetailSection(
                            ticket = ticket,
                            detailLoading = state.detailLoading,
                            replyContent = state.replyContent,
                            replying = state.replying,
                            onReplyContentChange = onReplyContentChange,
                            onSubmitReply = onSubmitReply,
                            onClose = { onSelectTicket(null) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateTicketForm(
    title: String,
    content: String,
    priority: String,
    creating: Boolean,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onPriorityChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    KuayunInfoCard {
        KuayunSectionTitle(title = "新建工单")
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("标题") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = content,
            onValueChange = onContentChange,
            label = { Text("问题描述") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "优先级",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ticketPriorityOptions.forEach { (value, label) ->
                FilterChip(
                    selected = priority == value,
                    onClick = { onPriorityChange(value) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
        }
        Button(
            onClick = onSubmit,
            enabled = !creating,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (creating) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(if (creating) "提交中..." else "提交工单")
        }
    }
}

@Composable
private fun TicketCard(
    ticket: TicketItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    KuayunListCard(selected = selected, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = ticket.title,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Text(
                text = ticketStatusLabel(ticket.status),
                style = MaterialTheme.typography.labelMedium,
                color = ticketStatusColor(ticket.status),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "优先级：${ticketPriorityLabel(ticket.priority)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ticket.updated_at?.let {
                Text(
                    text = formatTicketTime(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } ?: ticket.created_at?.let {
                Text(
                    text = formatTicketTime(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TicketDetailSection(
    ticket: TicketItem,
    detailLoading: Boolean,
    replyContent: String,
    replying: Boolean,
    onReplyContentChange: (String) -> Unit,
    onSubmitReply: () -> Unit,
    onClose: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "工单详情", fontWeight = FontWeight.Bold)
                TextButton(onClick = onClose) {
                    Text("收起")
                }
            }
            if (detailLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = ticketStatusLabel(ticket.status),
                        color = ticketStatusColor(ticket.status),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = ticketPriorityLabel(ticket.priority),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(text = ticket.content, style = MaterialTheme.typography.bodyMedium)
                ticket.created_at?.let {
                    Text(
                        text = "创建时间：${formatTicketTime(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val replies = ticket.replies.orEmpty()
                if (replies.isNotEmpty()) {
                    KuayunSectionTitle(title = "回复记录")
                    replies.forEach { reply ->
                        TicketReplyBubble(reply = reply)
                    }
                }
                if (ticket.status != "closed") {
                    OutlinedTextField(
                        value = replyContent,
                        onValueChange = onReplyContentChange,
                        label = { Text("继续回复") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = onSubmitReply,
                        enabled = !replying,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (replying) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text(if (replying) "发送中..." else "发送回复")
                    }
                } else {
                    Text(
                        text = "工单已关闭，无法继续回复",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TicketReplyBubble(reply: TicketReplyItem) {
    val isAdmin = reply.admin_id != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isAdmin) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Text(
                text = if (isAdmin) "客服" else "我",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(text = reply.content, style = MaterialTheme.typography.bodyMedium)
            reply.created_at?.let {
                Text(
                    text = formatTicketTime(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val ticketPriorityOptions = listOf(
    "low" to "低",
    "normal" to "普通",
    "high" to "高",
    "urgent" to "紧急",
)

private fun ticketStatusLabel(status: String): String = when (status) {
    "pending" -> "待处理"
    "processing" -> "处理中"
    "resolved" -> "已解决"
    "closed" -> "已关闭"
    else -> status
}

private fun ticketPriorityLabel(priority: String): String = when (priority) {
    "low" -> "低"
    "normal" -> "普通"
    "high" -> "高"
    "urgent" -> "紧急"
    else -> priority
}

@Composable
private fun ticketStatusColor(status: String) = when (status) {
    "pending" -> MaterialTheme.colorScheme.tertiary
    "processing" -> MaterialTheme.colorScheme.primary
    "resolved" -> MaterialTheme.colorScheme.secondary
    "closed" -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
}

private fun formatTicketTime(raw: String): String =
    runCatching {
        val instant = Instant.parse(raw)
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }.getOrElse { raw.take(16).replace("T", " ") }
