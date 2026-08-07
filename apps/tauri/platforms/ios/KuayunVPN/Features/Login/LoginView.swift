import SwiftUI

struct LoginView: View {
    @ObservedObject private var auth = AuthStore.shared
    @State private var email = ""
    @State private var password = ""
    @State private var loading = false

    var body: some View {
        NavigationStack {
            Form {
                Section("账号") {
                    TextField("邮箱", text: $email)
                        .textContentType(.username)
                        .keyboardType(.emailAddress)
                        .autocapitalization(.none)
                    SecureField("密码", text: $password)
                        .textContentType(.password)
                }

                if let error = auth.lastError, !error.isEmpty {
                    Section {
                        Text(error).foregroundStyle(.red).font(.footnote)
                    }
                }

                Section {
                    Button { Task { await submit() } } label: {
                        HStack {
                            Spacer()
                            if loading { ProgressView() }
                            else { Text("登录").fontWeight(.semibold) }
                            Spacer()
                        }
                    }
                    .disabled(loading || email.isEmpty || password.isEmpty)
                }

                Section {
                    NavigationLink("注册账号") { RegisterView() }
                    NavigationLink("忘记密码") { ForgotPasswordView() }
                }
            }
            .navigationTitle("跨云 VPN")
        }
    }

    private func submit() async {
        loading = true
        defer { loading = false }
        _ = await auth.login(email: email, password: password)
    }
}

#Preview {
    LoginView()
}
