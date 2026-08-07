import Foundation

/// 隐私政策同意门控（对齐桌面 `PRIVACY_ACCEPTED_KEY`）
enum PrivacyOnboardingStore {
    private static let acceptedKey = "kuayun_ios_privacy_accepted"

    static var isAccepted: Bool {
        UserDefaults.standard.bool(forKey: acceptedKey)
    }

    static func markAccepted() {
        UserDefaults.standard.set(true, forKey: acceptedKey)
    }
}
