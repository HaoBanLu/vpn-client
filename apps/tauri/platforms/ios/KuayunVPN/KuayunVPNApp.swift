import SwiftUI

@main
struct KuayunVPNApp: App {
    @StateObject private var auth = AuthStore.shared

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(auth)
                .task {
                    await auth.restoreSessionIfNeeded()
                }
        }
    }
}

private struct RootView: View {
    @EnvironmentObject private var auth: AuthStore
    @State private var launchPhase: LaunchPhase = .splash

    private enum LaunchPhase {
        case splash
        case privacy
        case main
    }

    var body: some View {
        Group {
            switch launchPhase {
            case .splash:
                SplashView {
                    if PrivacyOnboardingStore.isAccepted {
                        launchPhase = .main
                    } else {
                        launchPhase = .privacy
                    }
                }
            case .privacy:
                PrivacyView {
                    launchPhase = .main
                }
            case .main:
                if auth.isAuthenticated {
                    MainTabView()
                        .task { AccountStore.shared.startNotificationPolling() }
                        .onDisappear { AccountStore.shared.stopNotificationPolling() }
                } else {
                    LoginView()
                }
            }
        }
    }
}
