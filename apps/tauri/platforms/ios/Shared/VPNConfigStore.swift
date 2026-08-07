import Foundation

enum VPNConfigStoreError: LocalizedError {
    case appGroupUnavailable
    case writeFailed(String)

    var errorDescription: String? {
        switch self {
        case .appGroupUnavailable:
            return "App Group 不可用，请检查签名与 Entitlement"
        case let .writeFailed(reason):
            return "写入 VPN 配置失败: \(reason)"
        }
    }
}

/// 在 App Group 容器中读写 Clash 配置，供主 App 与 Packet Tunnel 共享。
enum VPNConfigStore {
    static var containerURL: URL? {
        FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: AppGroupConstants.identifier
        )
    }

    static var configURL: URL? {
        containerURL?.appendingPathComponent(AppGroupConstants.clashConfigFileName)
    }

    static func writeConfig(_ yaml: String) throws {
        guard let url = configURL else {
            throw VPNConfigStoreError.appGroupUnavailable
        }
        do {
            try yaml.write(to: url, atomically: true, encoding: .utf8)
        } catch {
            throw VPNConfigStoreError.writeFailed(error.localizedDescription)
        }
    }

    static func readConfig() -> String? {
        guard let url = configURL,
              let text = try? String(contentsOf: url, encoding: .utf8)
        else {
            return nil
        }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    static func clearConfig() {
        guard let url = configURL else { return }
        try? FileManager.default.removeItem(at: url)
    }
}
