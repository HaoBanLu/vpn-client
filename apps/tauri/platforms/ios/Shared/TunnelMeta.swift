import Foundation

/// 主 App 写入、Extension 读取的连接元数据。
struct TunnelMeta: Codable, Equatable {
    var region: String?
    var node: String?
    var routeMode: String?
    var profile: String?
    var updatedAt: Date

    enum CodingKeys: String, CodingKey {
        case region, node, profile
        case routeMode = "route_mode"
        case updatedAt = "updated_at"
    }
}

enum TunnelMetaStore {
    static var metaURL: URL? {
        VPNConfigStore.containerURL?.appendingPathComponent(AppGroupConstants.tunnelMetaFileName)
    }

    static func write(_ meta: TunnelMeta) throws {
        guard let url = metaURL else {
            throw VPNConfigStoreError.appGroupUnavailable
        }
        let data = try JSONEncoder().encode(meta)
        try data.write(to: url, options: .atomic)
    }

    static func read() -> TunnelMeta? {
        guard let url = metaURL,
              let data = try? Data(contentsOf: url),
              let meta = try? JSONDecoder().decode(TunnelMeta.self, from: data)
        else {
            return nil
        }
        return meta
    }
}
