import Foundation

/// iOS Network Extension 专用 YAML 补丁：对齐桌面「mixed-port + 代理」，关闭 TUN（由 NEProxySettings 引流）。
enum ClashIosConfigPatcher {
    static func patchForNetworkExtension(_ yaml: String) -> String {
        var result = upsertMixedPort(yaml, port: 17890)
        result = upsertExternalController(result, port: 17893)
        result = upsertBool(result, key: "allow-lan", value: false)
        result = upsertScalar(result, key: "bind-address", value: "127.0.0.1")
        result = upsertTunDisabled(result)
        if !result.contains("clash-for-android:") {
            result = result.trimmingCharacters(in: .whitespacesAndNewlines)
                + "\n\nclash-for-android:\n  append-system-dns: false\n"
        }
        return result.trimmingCharacters(in: .whitespacesAndNewlines) + "\n"
    }

    private static func upsertMixedPort(_ yaml: String, port: Int) -> String {
        upsertScalar(yaml, key: "mixed-port", value: "\(port)")
    }

    private static func upsertExternalController(_ yaml: String, port: Int) -> String {
        upsertScalar(yaml, key: "external-controller", value: "127.0.0.1:\(port)")
    }

    private static func upsertBool(_ yaml: String, key: String, value: Bool) -> String {
        upsertScalar(yaml, key: key, value: value ? "true" : "false")
    }

    private static func upsertScalar(_ yaml: String, key: String, value: String) -> String {
        var lines = yaml.lines
        if let idx = lines.firstIndex(where: { $0.trimmingCharacters(in: .whitespaces) == "\(key):" }) {
            lines[idx] = "\(key): \(value)"
            return lines.joined(separator: "\n")
        }
        lines.insert("\(key): \(value)", at: 0)
        return lines.joined(separator: "\n")
    }

    private static func upsertTunDisabled(_ yaml: String) -> String {
        var lines = yaml.lines
        if let start = lines.firstIndex(where: { $0.trimmingCharacters(in: .whitespaces) == "tun:" }) {
            var end = start + 1
            while end < lines.count {
                let line = lines[end]
                if !line.isEmpty && !line.hasPrefix(" ") && !line.hasPrefix("\t") { break }
                end += 1
            }
            lines.removeSubrange(start..<end)
        }
        let block = [
            "tun:",
            "  enable: false",
        ]
        lines.insert(contentsOf: block, at: 0)
        return lines.joined(separator: "\n")
    }
}

private extension String {
    var lines: [String] { components(separatedBy: .newlines) }
}
