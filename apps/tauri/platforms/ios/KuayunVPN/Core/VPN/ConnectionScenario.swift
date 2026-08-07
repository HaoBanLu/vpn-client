import Foundation

enum ConnectionScenario: String, CaseIterable, Identifiable {
    case auto
    case returnHome = "return_home"
    case overseas

    var id: String { rawValue }

    var label: String {
        switch self {
        case .auto: return "自动"
        case .returnHome: return "回国加速"
        case .overseas: return "海外访问"
        }
    }

    static func normalize(_ raw: String?) -> ConnectionScenario {
        let v = raw?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() ?? ""
        if v == "return_home" || v == "return-home" || v == "domestic_return" { return .returnHome }
        if v == "overseas" || v == "overseas_weak" { return .overseas }
        return .auto
    }
}

enum ClientProfile: String {
    case domesticReturn = "domestic_return"
    case overseasWeak = "overseas_weak"
}

enum AppRouteMode: String, CaseIterable, Identifiable {
    case full
    case split

    var id: String { rawValue }

    var label: String {
        switch self {
        case .full: return "全流量"
        case .split: return "分流"
        }
    }
}

struct ResolvedConnectionConfig {
    let profile: ClientProfile
    let routeMode: AppRouteMode
}

enum ConnectionScenarioResolver {
    static func inferDomesticReturn(region: String?, accessMode: String?) -> Bool {
        let code = region?.lowercased() ?? ""
        if code == "cn" || code == "china" || code.contains("中国") { return true }
        return accessMode?.lowercased() == "relay"
    }

    static func resolve(
        scenario: ConnectionScenario,
        nodeRegion: String?,
        accessMode: String?
    ) -> ResolvedConnectionConfig {
        switch scenario {
        case .returnHome:
            return .init(profile: .domesticReturn, routeMode: .full)
        case .overseas:
            return .init(profile: .overseasWeak, routeMode: .full)
        case .auto:
            if inferDomesticReturn(region: nodeRegion, accessMode: accessMode) {
                return .init(profile: .domesticReturn, routeMode: .full)
            }
            return .init(profile: .overseasWeak, routeMode: .full)
        }
    }
}
