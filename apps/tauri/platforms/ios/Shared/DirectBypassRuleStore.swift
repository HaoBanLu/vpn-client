import Foundation

enum DirectBypassRuleType: String, CaseIterable, Identifiable, Codable {
    case domain = "DOMAIN"
    case domainSuffix = "DOMAIN_SUFFIX"
    case domainKeyword = "DOMAIN_KEYWORD"
    case ipCidr = "IP_CIDR"

    var id: String { rawValue }

    var clashType: String {
        switch self {
        case .domain: return "DOMAIN"
        case .domainSuffix: return "DOMAIN-SUFFIX"
        case .domainKeyword: return "DOMAIN-KEYWORD"
        case .ipCidr: return "IP-CIDR"
        }
    }

    var label: String {
        switch self {
        case .domain: return "完整域名"
        case .domainSuffix: return "域名后缀"
        case .domainKeyword: return "域名关键词"
        case .ipCidr: return "IP 段"
        }
    }
}

struct DirectBypassRule: Identifiable, Codable, Equatable {
    let id: UUID
    var type: DirectBypassRuleType
    var value: String
    var enabled: Bool
}

enum DirectBypassRuleStore {
    private static let storageKey = "kuayun_ios_direct_bypass_rules"

    static func load() -> [DirectBypassRule] {
        guard let data = UserDefaults.standard.data(forKey: storageKey),
              let rules = try? JSONDecoder().decode([DirectBypassRule].self, from: data)
        else { return [] }
        return rules
    }

    static func save(_ rules: [DirectBypassRule]) {
        if let data = try? JSONEncoder().encode(rules) {
            UserDefaults.standard.set(data, forKey: storageKey)
        }
    }

    static func enabledRules() -> [DirectBypassRule] {
        load().filter(\.enabled)
    }

    static func toClashLine(_ rule: DirectBypassRule) -> String {
        let base = "- \(rule.type.clashType),\(rule.value),DIRECT"
        return rule.type == .ipCidr ? "\(base),no-resolve" : base
    }

    /// 对齐 Tauri `injectDirectBypassRules`。
    static func inject(into yaml: String, rules: [DirectBypassRule]? = nil) -> String {
        var enabled = (rules ?? enabledRules())
        var seen = Set<String>()
        enabled = enabled.filter { rule in
            let key = "\(rule.type.rawValue):\(rule.value.lowercased())"
            guard !seen.contains(key) else { return false }
            seen.insert(key)
            return true
        }
        guard !enabled.isEmpty else { return yaml }

        let clashLines = enabled.map { "  \(toClashLine($0))" }
        var lines = yaml.components(separatedBy: .newlines)
        var rulesStart = -1
        var lastMatch = -1
        for (index, line) in lines.enumerated() {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed == "rules:" || trimmed.hasPrefix("rules:") { rulesStart = index }
            if rulesStart >= 0 && trimmed.hasPrefix("- MATCH,") { lastMatch = index }
        }
        if rulesStart < 0 {
            return yaml.trimmingCharacters(in: .whitespacesAndNewlines) + "\n\nrules:\n" + clashLines.joined(separator: "\n") + "\n"
        }
        if lastMatch >= 0 {
            lines.insert(contentsOf: clashLines, at: lastMatch)
            return lines.joined(separator: "\n") + "\n"
        }
        lines.append(contentsOf: clashLines)
        return lines.joined(separator: "\n") + "\n"
    }
}
