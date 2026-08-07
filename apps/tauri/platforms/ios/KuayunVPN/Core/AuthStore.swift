import Foundation
import SwiftUI

@MainActor
final class AuthStore: ObservableObject {
    static let shared = AuthStore()

    @Published private(set) var token: String?
    @Published private(set) var user: UserBrief?
    @Published var lastError: String?

    private let tokenKey = "kuayun_ios_token"
    private let userKey = "kuayun_ios_user"
    private var sessionObserver: NSObjectProtocol?

    var isAuthenticated: Bool { token != nil }

    private init() {
        token = UserDefaults.standard.string(forKey: tokenKey)
        if let raw = UserDefaults.standard.data(forKey: userKey),
           let saved = try? JSONDecoder().decode(UserBrief.self, from: raw) {
            user = saved
        }
        sessionObserver = NotificationCenter.default.addObserver(
            forName: .kuayunSessionExpired,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                VPNController.shared.disconnect()
                self?.logout(silent: true)
                self?.lastError = "登录已失效，请重新登录"
            }
        }
    }

    deinit {
        if let sessionObserver {
            NotificationCenter.default.removeObserver(sessionObserver)
        }
    }

    func login(email: String, password: String) async -> Bool {
        lastError = nil
        do {
            let session = try await APIClient.shared.login(email: email, password: password)
            applySession(token: session.token, user: session.user)
            return true
        } catch {
            lastError = error.localizedDescription
            return false
        }
    }

    func applySession(token: String, user: UserBrief) {
        persist(token: token, user: user)
    }

    func restoreSessionIfNeeded() async {
        guard let token, user == nil else { return }
        do {
            let me = try await APIClient.shared.fetchCurrentUser(token: token)
            user = me
            persistUser(me)
        } catch {
            logout(silent: true)
        }
    }

    func logout(silent: Bool = false) {
        VPNController.shared.disconnect()
        token = nil
        user = nil
        UserDefaults.standard.removeObject(forKey: tokenKey)
        UserDefaults.standard.removeObject(forKey: userKey)
        if !silent {
            lastError = nil
        }
    }

    private func persist(token: String, user: UserBrief) {
        self.token = token
        self.user = user
        UserDefaults.standard.set(token, forKey: tokenKey)
        persistUser(user)
    }

    private func persistUser(_ user: UserBrief) {
        if let data = try? JSONEncoder().encode(user) {
            UserDefaults.standard.set(data, forKey: userKey)
        }
    }
}
