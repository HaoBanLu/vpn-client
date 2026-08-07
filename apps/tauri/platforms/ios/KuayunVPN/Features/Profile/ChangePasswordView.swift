import SwiftUI

struct ChangePasswordView: View {
    @EnvironmentObject private var auth: AuthStore
    @Environment(\.dismiss) private var dismiss

    @State private var oldPassword = ""
    @State private var newPassword = ""
    @State private var confirmPassword = ""
    @State private var loading = false
    @State private var message: String?
    @State private var isSuccess = false

    private var canSubmit: Bool {
        !oldPassword.isEmpty && newPassword.count >= 6 && newPassword == confirmPassword
    }

    var body: some View {
        Form {
            Section {
                SecureField("当前密码", text: $oldPassword)
                SecureField("新密码（至少 6 位）", text: $newPassword)
                SecureField("确认新密码", text: $confirmPassword)
            }

            Section {
                Button("保存") {
                    Task { await submit() }
                }
                .disabled(!canSubmit || loading)
            }

            if let message {
                Section {
                    Text(message)
                        .foregroundStyle(isSuccess ? .green : .red)
                        .font(.footnote)
                }
            }
        }
        .navigationTitle("修改密码")
        .disabled(loading)
    }

    private func submit() async {
        guard let token = auth.token else { return }
        loading = true
        defer { loading = false }
        do {
            try await APIClient.shared.changePassword(
                token: token,
                oldPassword: oldPassword,
                newPassword: newPassword
            )
            isSuccess = true
            message = "密码已更新"
            oldPassword = ""
            newPassword = ""
            confirmPassword = ""
            try? await Task.sleep(nanoseconds: 800_000_000)
            dismiss()
        } catch {
            isSuccess = false
            message = error.localizedDescription
        }
    }
}
