import SwiftUI

struct RegisterView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var auth = AuthStore.shared
    @State private var email = ""
    @State private var password = ""
    @State private var emailCode = ""
    @State private var requireEmailCode = false
    @State private var acceptedTerms = false
    @State private var loading = false
    @State private var sendingCode = false
    @State private var cooldown = 0
    @State private var cooldownTask: Task<Void, Never>?

    var body: some View {
        Form {
            Section("注册") {
                TextField("邮箱", text: $email)
                    .keyboardType(.emailAddress)
                    .autocapitalization(.none)
                SecureField("密码（至少 6 位）", text: $password)
                if requireEmailCode {
                    HStack {
                        TextField("验证码", text: $emailCode)
                            .keyboardType(.numberPad)
                        Button(cooldown > 0 ? "\(cooldown)s" : "发送") {
                            Task { await sendCode() }
                        }
                        .disabled(sendingCode || cooldown > 0 || email.isEmpty)
                    }
                }
            }
            Section {
                Toggle("我已阅读并同意服务条款与隐私政策", isOn: $acceptedTerms)
            }
            if let error = auth.lastError {
                Section { Text(error).foregroundStyle(.red).font(.footnote) }
            }
            Section {
                Button("注册并登录") { Task { await submit() } }
                    .disabled(loading || !acceptedTerms || email.isEmpty || password.count < 6)
            }
        }
        .navigationTitle("注册")
        .task { await loadRegistrationConfig() }
        .onDisappear { cooldownTask?.cancel() }
    }

    private func loadRegistrationConfig() async {
        // 后端若强制邮箱验证码，注册页会展示发送按钮；当前 API 无独立 config 时默认关闭
        requireEmailCode = false
    }

    private func sendCode() async {
        sendingCode = true
        defer { sendingCode = false }
        do {
            try await APIClient.shared.sendEmailCode(email: email, purpose: "register")
            startCooldown(60)
        } catch {
            auth.lastError = error.localizedDescription
        }
    }

    private func startCooldown(_ seconds: Int) {
        cooldownTask?.cancel()
        cooldown = seconds
        cooldownTask = Task {
            while cooldown > 0, !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                cooldown -= 1
            }
        }
    }

    private func submit() async {
        loading = true
        defer { loading = false }
        do {
            let code = requireEmailCode && !emailCode.isEmpty ? emailCode : nil
            let session = try await APIClient.shared.register(email: email, password: password, emailCode: code)
            auth.applySession(token: session.token, user: session.user)
            dismiss()
        } catch {
            auth.lastError = error.localizedDescription
        }
    }
}

struct ForgotPasswordView: View {
    @State private var email = ""
    @State private var message: String?
    @State private var loading = false

    var body: some View {
        Form {
            Section {
                TextField("注册邮箱", text: $email)
                    .keyboardType(.emailAddress)
                    .autocapitalization(.none)
            }
            Section {
                Button("发送重置邮件") { Task { await submit() } }
                    .disabled(loading || email.isEmpty)
            }
            if let message {
                Section { Text(message).font(.footnote) }
            }
        }
        .navigationTitle("找回密码")
    }

    private func submit() async {
        loading = true
        defer { loading = false }
        do {
            try await APIClient.shared.forgotPassword(email: email)
            message = "若邮箱已注册，将收到重置说明"
        } catch {
            message = error.localizedDescription
        }
    }
}
