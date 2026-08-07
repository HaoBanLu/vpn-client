import SwiftUI

struct TicketsView: View {
    @EnvironmentObject private var auth: AuthStore
    @State private var tickets: [TicketItem] = []
    @State private var loading = false
    @State private var showCreate = false
    @State private var creating = false
    @State private var createTitle = ""
    @State private var createContent = ""
    @State private var createPriority = "normal"
    @State private var selected: TicketItem?
    @State private var replyContent = ""
    @State private var replying = false
    @State private var message: String?

    private let priorities = [
        ("normal", "普通"),
        ("high", "高"),
        ("urgent", "紧急"),
    ]

    var body: some View {
        List {
            if showCreate {
                Section("新建工单") {
                    TextField("标题", text: $createTitle)
                    TextField("详细说明", text: $createContent, axis: .vertical)
                        .lineLimit(3...6)
                    Picker("优先级", selection: $createPriority) {
                        ForEach(priorities, id: \.0) { value, label in
                            Text(label).tag(value)
                        }
                    }
                    Button("提交工单") {
                        Task { await createTicket() }
                    }
                    .disabled(creating || createTitle.trimmingCharacters(in: .whitespaces).isEmpty || createContent.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }

            if tickets.isEmpty && !loading {
                Section {
                    Text("暂无工单").foregroundStyle(.secondary)
                }
            } else {
                Section("工单列表") {
                    ForEach(tickets) { item in
                        Button {
                            Task { await openDetail(item) }
                        } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(item.title).font(.headline).foregroundStyle(.primary)
                                Text("\(FormatLabels.ticketStatus(item.status)) · \(FormatLabels.ticketPriority(item.priority))")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                if let createdAt = item.createdAt {
                                    Text(FormatLabels.formatDateTime(createdAt))
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("我的工单")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(showCreate ? "取消" : "新建") {
                    showCreate.toggle()
                }
            }
        }
        .refreshable { await load() }
        .task { await load() }
        .sheet(item: $selected) { ticket in
            ticketDetailSheet(ticket)
        }
        .alert("提示", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("确定", role: .cancel) { message = nil }
        } message: {
            Text(message ?? "")
        }
    }

    @ViewBuilder
    private func ticketDetailSheet(_ ticket: TicketItem) -> some View {
        NavigationStack {
            List {
                Section {
                    Text(ticket.title).font(.headline)
                    Text(FormatLabels.ticketStatus(ticket.status))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(ticket.content)
                }
                if let replies = ticket.replies, !replies.isEmpty {
                    Section("回复") {
                        ForEach(replies) { reply in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(reply.content)
                                Text(FormatLabels.formatDateTime(reply.createdAt))
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                Section("追加回复") {
                    TextField("输入回复内容", text: $replyContent, axis: .vertical)
                        .lineLimit(2...5)
                    Button("发送回复") {
                        Task { await submitReply(ticketId: ticket.id) }
                    }
                    .disabled(replying || replyContent.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .navigationTitle("工单详情")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("关闭") { selected = nil }
                }
            }
        }
    }

    private func load() async {
        guard let token = auth.token else { return }
        loading = true
        defer { loading = false }
        do {
            tickets = try await APIClient.shared.fetchTickets(token: token).tickets
        } catch {
            message = error.localizedDescription
        }
    }

    private func openDetail(_ item: TicketItem) async {
        guard let token = auth.token else { return }
        do {
            selected = try await APIClient.shared.fetchTicket(token: token, id: item.id)
        } catch {
            message = error.localizedDescription
        }
    }

    private func createTicket() async {
        guard let token = auth.token else { return }
        creating = true
        defer { creating = false }
        do {
            let created = try await APIClient.shared.createTicket(
                token: token,
                title: createTitle.trimmingCharacters(in: .whitespaces),
                content: createContent.trimmingCharacters(in: .whitespaces),
                priority: createPriority
            )
            tickets.insert(created, at: 0)
            createTitle = ""
            createContent = ""
            showCreate = false
            message = "工单已提交"
        } catch {
            message = error.localizedDescription
        }
    }

    private func submitReply(ticketId: UInt64) async {
        guard let token = auth.token else { return }
        replying = true
        defer { replying = false }
        do {
            _ = try await APIClient.shared.addTicketReply(
                token: token,
                ticketId: ticketId,
                content: replyContent.trimmingCharacters(in: .whitespaces)
            )
            replyContent = ""
            selected = try await APIClient.shared.fetchTicket(token: token, id: ticketId)
            await load()
            message = "回复已发送"
        } catch {
            message = error.localizedDescription
        }
    }
}

extension TicketItem: Hashable {
    static func == (lhs: TicketItem, rhs: TicketItem) -> Bool { lhs.id == rhs.id }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }
}
