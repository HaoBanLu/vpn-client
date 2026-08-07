import Foundation

struct ExitIpInfo {
    let ip: String
    let country: String?
    let region: String?
    let city: String?
}

enum ExitIpProbe {
    private static let geoURL = URL(string: "https://ip-api.com/json/?fields=status,query,country,regionName,city")!

    static func probe(timeout: TimeInterval = 8) async -> ExitIpInfo? {
        var request = URLRequest(url: geoURL)
        request.httpMethod = "GET"
        request.cachePolicy = .reloadIgnoringLocalCacheData
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { return nil }
            struct Payload: Decodable {
                let status: String?
                let query: String?
                let country: String?
                let regionName: String?
                let city: String?
            }
            let json = try JSONDecoder().decode(Payload.self, from: data)
            guard json.status == "success", let ip = json.query?.trimmingCharacters(in: .whitespacesAndNewlines), !ip.isEmpty else {
                return nil
            }
            return ExitIpInfo(ip: ip, country: json.country, region: json.regionName, city: json.city)
        } catch {
            return nil
        }
    }
}

struct PrivacyLeakProbeResult {
    let exitIp: String?
    let exitIpLooksProtected: Bool
    let ipv6LocalActive: Bool
    let dnsReachable: Bool
    let passed: Bool
}

enum PrivacyLeakProbe {
    private static let baselineKey = "kuayun_ios_privacy_baseline_ip"
    private static let dnsURL = URL(string: "https://cloudflare-dns.com/dns-query?name=example.com&type=A")!

    static func saveBaselineIp(_ ip: String?) {
        guard let ip, !ip.isEmpty else { return }
        UserDefaults.standard.set(ip, forKey: baselineKey)
    }

    static func loadBaselineIp() -> String? {
        UserDefaults.standard.string(forKey: baselineKey)
    }

    static func evaluate(
        exitIp: String?,
        baselineIp: String?,
        ipv6LocalActive: Bool,
        dnsReachable: Bool
    ) -> PrivacyLeakProbeResult {
        let protected = exitIp != nil && (baselineIp == nil || exitIp!.lowercased() != baselineIp!.lowercased())
        let passed = protected && !ipv6LocalActive && dnsReachable
        return PrivacyLeakProbeResult(
            exitIp: exitIp,
            exitIpLooksProtected: protected,
            ipv6LocalActive: ipv6LocalActive,
            dnsReachable: dnsReachable,
            passed: passed
        )
    }

    static func run() async -> PrivacyLeakProbeResult {
        async let exit = ExitIpProbe.probe()
        async let dns = probeDns()
        let baseline = loadBaselineIp()
        let exitInfo = await exit
        let dnsOk = await dns
        // iOS 无 Rust IPv6 探测；NE 全隧道下通常无本地 IPv6 泄露，保守标 false
        return evaluate(
            exitIp: exitInfo?.ip,
            baselineIp: baseline,
            ipv6LocalActive: false,
            dnsReachable: dnsOk
        )
    }

    static func formatMessage(_ result: PrivacyLeakProbeResult) -> String {
        if result.passed { return "自检通过：出口 IP \(result.exitIp ?? "-")" }
        var parts = ["自检未完全通过"]
        if !result.exitIpLooksProtected { parts.append("出口 IP 异常") }
        if result.ipv6LocalActive { parts.append("IPv6 风险") }
        if !result.dnsReachable { parts.append("DNS 异常") }
        return parts.joined(separator: " · ")
    }

    private static func probeDns() async -> Bool {
        var request = URLRequest(url: dnsURL)
        request.setValue("application/dns-json", forHTTPHeaderField: "Accept")
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse else { return false }
            return (200..<300).contains(http.statusCode)
        } catch {
            return false
        }
    }
}
