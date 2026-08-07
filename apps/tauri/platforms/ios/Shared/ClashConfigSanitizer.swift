import Foundation

enum ClashConfigSanitizerError: LocalizedError {
    case empty
    case jsonInsteadOfYaml
    case missingProxies
    case emptyProxies

    var errorDescription: String? {
        switch self {
        case .empty: return "Clash 配置为空"
        case .jsonInsteadOfYaml: return "配置格式错误，期望 Clash YAML"
        case .missingProxies: return "配置缺少 proxies 段"
        case .emptyProxies: return "proxies 段为空"
        }
    }
}

/// 对齐 Android `ClashConfigSanitizer`：写入 Mihomo 前清洗远程 geo/ruleset 依赖。
enum ClashConfigSanitizer {
    static func prepareForTunnel(
        rawYaml: String,
        geoReady: Bool = false,
        rulesetsReady: Bool = false
    ) throws -> String {
        var yaml = rawYaml.trimmingCharacters(in: .whitespacesAndNewlines)
        if !geoReady {
            yaml = stripRemoteGeoRules(yaml)
            yaml = stripGeoipDnsFallback(yaml)
        }
        if rulesetsReady {
            yaml = localizeRuleProviders(yaml)
        } else {
            yaml = stripRemoteRuleProviders(yaml)
        }
        yaml = preferOverseasFriendlyDns(yaml)
        yaml = ClashIosConfigPatcher.patchForNetworkExtension(yaml)
        yaml = DirectBypassRuleStore.inject(into: yaml)
        try validateClashYaml(yaml)
        return yaml
    }

    static func validateClashYaml(_ yaml: String) throws {
        if yaml.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            throw ClashConfigSanitizerError.empty
        }
        if yaml.trimmingCharacters(in: .whitespaces).hasPrefix("{") {
            throw ClashConfigSanitizerError.jsonInsteadOfYaml
        }
        let hasProxies =
            yaml.contains("\nproxies:") ||
            yaml.hasPrefix("proxies:") ||
            yaml.contains("\nproxy-providers:")
        if !hasProxies {
            throw ClashConfigSanitizerError.missingProxies
        }
        let proxiesBody = yaml
            .components(separatedBy: "proxies:")
            .dropFirst()
            .first?
            .components(separatedBy: "proxy-groups:").first?
            .components(separatedBy: "proxy-providers:").first ?? ""
        let namePattern = #/(?m)^\s*-\s*name:\s*\S+/#
        if proxiesBody.firstMatch(of: namePattern) == nil {
            throw ClashConfigSanitizerError.emptyProxies
        }
    }

    static func stripRemoteGeoRules(_ yaml: String) -> String {
        let lines = yaml.lines
        let filtered = lines.filter { line in
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            return !trimmed.hasPrefix("- GEOSITE,") && !trimmed.hasPrefix("- GEOIP,")
        }
        return filtered.joined(separator: "\n")
    }

    static func stripGeoipDnsFallback(_ yaml: String) -> String {
        let pattern = /(?m)^(\s*)geoip:\s*true\s*$/
        guard yaml.firstMatch(of: pattern) != nil else { return yaml }
        return yaml.replacing(pattern, with: "$1geoip: false")
    }

    static func stripRemoteRuleProviders(_ yaml: String) -> String {
        var result = yaml
        if result.contains("rule-providers:") {
            result = result.replacing(
                /(?ms)^rule-providers:.*?(?=^\S|\z)/,
                with: ""
            ).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        let lines = result.lines.filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("- RULE-SET,") }
        let joined = lines.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
        return joined.isEmpty ? joined : joined + "\n"
    }

    static func localizeRuleProviders(_ yaml: String) -> String {
        guard yaml.contains("rule-providers:") else { return yaml }
        return yaml
            .replacing(
                /(?ms)(  (?:reject|cn):\n    )type: http\n    behavior: domain\n    url: [^\n]+\n    path: (\.\/ruleset\/[^\n]+)\n    interval: \d+/,
                with: "$1type: file\n    behavior: domain\n    path: $2"
            )
            .replacing("./ruleset/", with: "./providers/ruleset/")
    }

    /// 弱网海外：移除易超时的国内 DNS（对齐 Android）。 */
    static func preferOverseasFriendlyDns(_ yaml: String) -> String {
        let chinaMarkers = ["doh.pub", "alidns", "223.5.5.5", "114.114.114.114"]
        var inNameserver = false
        var changed = false
        let filtered = yaml.lines.filter { line in
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.hasPrefix("nameserver:") {
                inNameserver = true
                return true
            }
            if inNameserver && trimmed.hasPrefix("fallback:") {
                inNameserver = false
                return true
            }
            if inNameserver && trimmed.hasPrefix("- ") {
                let drop = chinaMarkers.contains { marker in
                    trimmed.localizedCaseInsensitiveContains(marker)
                }
                if drop { changed = true }
                return !drop
            }
            if inNameserver && !trimmed.isEmpty && !trimmed.hasPrefix("-") && !line.hasPrefix("  ") {
                inNameserver = false
            }
            return true
        }
        guard changed else { return yaml }
        return filtered.joined(separator: "\n")
    }
}

private extension String {
    var lines: [String] { components(separatedBy: .newlines) }
}
