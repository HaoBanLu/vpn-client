import Foundation

struct PrivacyProbeHistoryEntry: Identifiable, Codable, Equatable {
    var id: TimeInterval { atMillis }
    let atMillis: TimeInterval
    let passed: Bool
    let exitIp: String?
    let summary: String
}

/// 泄露自检历史（最近 10 次），对齐桌面 `privacy-probe-history.ts`
enum PrivacyProbeHistoryStore {
    private static let storageKey = "kuayun_ios_privacy_probe_history"
    private static let maxEntries = 10

    static func load() -> [PrivacyProbeHistoryEntry] {
        guard let data = UserDefaults.standard.data(forKey: storageKey),
              let entries = try? JSONDecoder().decode([PrivacyProbeHistoryEntry].self, from: data) else {
            return []
        }
        return entries
    }

    static func append(result: PrivacyLeakProbeResult) {
        let entry = PrivacyProbeHistoryEntry(
            atMillis: Date().timeIntervalSince1970 * 1000,
            passed: result.passed,
            exitIp: result.exitIp,
            summary: PrivacyLeakProbe.formatMessage(result)
        )
        let next = [entry] + load()
        let trimmed = Array(next.prefix(maxEntries))
        if let data = try? JSONEncoder().encode(trimmed) {
            UserDefaults.standard.set(data, forKey: storageKey)
        }
    }

    static func clear() {
        UserDefaults.standard.removeObject(forKey: storageKey)
    }
}
