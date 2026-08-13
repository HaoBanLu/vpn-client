import SwiftUI

struct ProfileView: View {
    @EnvironmentObject private var auth: AuthStore
    @ObservedObject private var account = AccountStore.shared

    var body: some View {
        NavigationStack {
            List {
                if !account.notifications.isEmpty {
                    Section("通知") {
                        ForEach(account.notifications.reversed()) { item in
                            NavigationLink {
                                OrdersView(initialTab: .recharge)
                            } label: {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(item.message).font(.subheadline)
                                    Text(item.orderNo).font(.caption).foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }

                if let user = auth.user {
                    Section("账户") {
                        LabeledContent("邮箱", value: user.email)
                        LabeledContent("余额", value: String(format: "¥%.2f", user.balance))
                        NavigationLink("充值") { RechargeView() }
                        NavigationLink("订单") { OrdersView() }
                    }
                }

                if let sub = account.subscription {
                    Section("当前套餐") {
                        LabeledContent("套餐", value: sub.package?.name ?? "有效套餐")
                        LabeledContent("到期", value: String(sub.expiresAt.prefix(10)))
                        if let usage = account.usage {
                            LabeledContent("剩余流量", value: String(format: "%.1f / %.1f GB", usage.remaining, usage.total))
                        }
                    }
                } else if let error = account.lastError {
                    Section("当前套餐") {
                        Text("网络异常").foregroundStyle(.red)
                        Text(error).font(.footnote).foregroundStyle(.secondary)
                        Button("重试") {
                            Task {
                                if let token = auth.token { await account.refresh(token: token) }
                            }
                        }
                    }
                } else if account.didFetch {
                    Section("当前套餐") {
                        Text("暂无有效套餐")
                        Text("购买套餐后即可使用加速")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                        NavigationLink("去购买套餐") { PackagesView() }
                    }
                }

                Section("商业") {
                    NavigationLink("购买套餐") { PackagesView() }
                    NavigationLink("流量统计") { TrafficView() }
                }

                Section("服务") {
                    NavigationLink("在线客服") { SupportView() }
                    if auth.user?.appDebugEnabled == true {
                        NavigationLink("导出订阅") { HelpView() }
                    }
                }

                Section("设置") {
                    NavigationLink("连接与隐私") { SettingsView() }
                    if auth.user?.appDebugEnabled == true {
                        NavigationLink("规则直连") { DirectBypassRulesView() }
                        NavigationLink("诊断日志") { DebugLogView() }
                    }
                    NavigationLink("登录设备") { DevicesView() }
                    NavigationLink("修改密码") { ChangePasswordView() }
                    NavigationLink("关于") { AboutView() }
                }

                Section {
                    Button("退出登录", role: .destructive) { auth.logout() }
                }
            }
            .navigationTitle("我的")
            .onAppear { account.clearUnreadNotifications() }
            .refreshable {
                if let token = auth.token { await account.refresh(token: token) }
            }
            .task {
                if let token = auth.token { await account.refresh(token: token) }
            }
        }
    }
}
