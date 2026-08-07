import Foundation

enum APIConfig {
    /// 运行时 API 基址：优先环境变量，其次 Info.plist。
    static var baseURL: URL {
        if let env = ProcessInfo.processInfo.environment["API_BASE_URL"],
           let url = URL(string: env.trimmingCharacters(in: .whitespacesAndNewlines)),
           !env.isEmpty {
            return url
        }
        if let raw = Bundle.main.object(forInfoDictionaryKey: "API_BASE_URL") as? String,
           let url = URL(string: raw.trimmingCharacters(in: .whitespacesAndNewlines)) {
            return url
        }
        return URL(string: "https://vpn.example.com/api/v1")!
    }

    static func endpoint(_ path: String) -> URL {
        let normalized = path.hasPrefix("/") ? String(path.dropFirst()) : path
        return baseURL.appendingPathComponent(normalized)
    }
}
