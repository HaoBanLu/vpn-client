import Foundation

struct APIEnvelope<T: Decodable>: Decodable {
    let code: Int
    let message: String
    let data: T?
}

struct UserBrief: Codable, Equatable {
    let id: UInt64
    let email: String
    let balance: Double
    let status: String?
    let role: String?
    let appDebugEnabled: Bool?

    enum CodingKeys: String, CodingKey {
        case id, email, balance, status, role
        case appDebugEnabled = "app_debug_enabled"
    }
}

struct AuthData: Decodable {
    let token: String
    let user: UserBrief
}

struct LoginRequest: Encodable {
    let email: String
    let password: String
    let deviceName: String?
    let clientPlatform: String?

    enum CodingKeys: String, CodingKey {
        case email, password
        case deviceName = "device_name"
        case clientPlatform = "client_platform"
    }
}

struct ClientConfigData: Decodable {
    let format: String
    let region: String?
    let node: String?
    let bandwidthLimitMbps: Int?
    let config: String

    enum CodingKeys: String, CodingKey {
        case format, region, node, config
        case bandwidthLimitMbps = "bandwidth_limit_mbps"
    }
}

struct RegionItem: Decodable, Identifiable, Hashable {
    var id: String { code }
    let code: String
    let name: String?
    let count: Int?
}

struct RegionsData: Decodable {
    let regions: [RegionItem]
}

struct NodeItem: Decodable, Identifiable, Hashable {
    let id: UInt64
    let name: String
    let region: String
    let regionName: String?
    let country: String?
    let status: String?
    let protocolType: String?
    let accessMode: String?
    let latencyMs: Int?

    enum CodingKeys: String, CodingKey {
        case id, name, region, country, status, protocol
        case regionName = "region_name"
        case accessMode = "access_mode"
        case latencyMs = "latency_ms"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(UInt64.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        region = try c.decode(String.self, forKey: .region)
        regionName = try c.decodeIfPresent(String.self, forKey: .regionName)
        country = try c.decodeIfPresent(String.self, forKey: .country)
        status = try c.decodeIfPresent(String.self, forKey: .status)
        protocolType = try c.decodeIfPresent(String.self, forKey: .protocol)
        accessMode = try c.decodeIfPresent(String.self, forKey: .accessMode)
        latencyMs = try c.decodeIfPresent(Int.self, forKey: .latencyMs)
    }
}

struct NodesData: Decodable {
    let nodes: [NodeItem]
}

struct MFARequiredData: Decodable {
    let mfaRequired: Bool

    enum CodingKeys: String, CodingKey {
        case mfaRequired = "mfa_required"
    }
}

enum APIClientError: LocalizedError {
    case invalidURL
    case httpStatus(Int, String)
    case business(code: Int, message: String)
    case decoding(Error)
    case unauthorized

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "无效的 API 地址"
        case let .httpStatus(code, message):
            return "HTTP \(code): \(message)"
        case let .business(_, message):
            return message
        case let .decoding(error):
            return "响应解析失败: \(error.localizedDescription)"
        case .unauthorized:
            return "登录已失效，请重新登录"
        }
    }

    var isUnauthorized: Bool {
        if case .unauthorized = self { return true }
        return false
    }
}
