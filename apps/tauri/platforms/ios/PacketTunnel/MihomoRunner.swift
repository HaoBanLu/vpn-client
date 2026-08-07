import Foundation
import os.log

#if MIHOMO_NATIVE
import Darwin

@_silgen_name("ky_mihomo_start")
func ky_mihomo_start(_ configDir: UnsafePointer<CChar>) -> Int32

@_silgen_name("ky_mihomo_stop")
func ky_mihomo_stop()

@_silgen_name("ky_mihomo_is_running")
func ky_mihomo_is_running() -> Int32
#endif

/// Mihomo 数据面：优先 xcframework C bridge（`MIHOMO_NATIVE`），否则尝试 Bundle 可执行文件（仅开发）。
/// 二者皆无时返回明确错误，避免「空隧道当已连接」。
final class MihomoRunner {
    static let shared = MihomoRunner()

    private let log = Logger(subsystem: "com.vpn.kuayun.tunnel", category: "MihomoRunner")
    private var process: Process?
    private var isRunning = false
    private var usingNative = false

    private init() {}

    func start(configYAML: String, completion: @escaping (Error?) -> Void) {
        stop()

        let prepared: String
        do {
            prepared = try ClashConfigSanitizer.prepareForTunnel(rawYaml: configYAML)
        } catch {
            completion(error)
            return
        }

        let configDir: URL
        do {
            configDir = try ensureConfigDirectory()
            try prepared.write(to: configDir.appendingPathComponent("config.yaml"), atomically: true, encoding: .utf8)
        } catch {
            completion(error)
            return
        }

        #if MIHOMO_NATIVE
        let code = configDir.path.withCString { ky_mihomo_start($0) }
        if code == 0 {
            usingNative = true
            isRunning = true
            log.info("Mihomo native bridge started dir=\(configDir.path, privacy: .public)")
            completion(nil)
            return
        }
        log.error("Mihomo native bridge failed code=\(code)")
        completion(Self.makeError(code: 1004, message: "Mihomo 原生内核启动失败（code=\(code)）"))
        return
        #else
        guard let binaryPath = resolveBinaryPath() else {
            log.error("Mihomo not available: no xcframework (MIHOMO_NATIVE) and no bundled binary")
            completion(Self.makeError(
                code: 1003,
                message: "Mihomo 内核未集成。请在 Mac 执行 npm run tauri:ios:setup-native（需先放入/构建 xcframework）"
            ))
            return
        }

        let proc = Process()
        proc.executableURL = URL(fileURLWithPath: binaryPath)
        proc.arguments = ["-d", configDir.path]
        proc.standardOutput = FileHandle.nullDevice
        proc.standardError = FileHandle.nullDevice
        proc.terminationHandler = { [weak self] finished in
            self?.log.info("Mihomo exited code=\(finished.terminationStatus)")
            self?.isRunning = false
        }

        do {
            try proc.run()
            process = proc
            isRunning = true
            usingNative = false
            log.info("Mihomo process started pid=\(proc.processIdentifier)")
            completion(nil)
        } catch {
            log.error("Mihomo spawn failed: \(error.localizedDescription)")
            completion(error)
        }
        #endif
    }

    func stop() {
        #if MIHOMO_NATIVE
        if usingNative {
            ky_mihomo_stop()
            usingNative = false
            isRunning = false
            return
        }
        #endif
        if let process, process.isRunning {
            process.terminate()
        }
        process = nil
        isRunning = false
        usingNative = false
    }

    private func ensureConfigDirectory() throws -> URL {
        let base = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        let dir = base.appendingPathComponent("mihomo", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    private func resolveBinaryPath() -> String? {
        if let bundled = Bundle.main.path(forResource: "mihomo", ofType: nil) {
            return bundled
        }
        if let bundled = Bundle.main.path(forResource: "mihomo-ios", ofType: nil) {
            return bundled
        }
        return nil
    }

    private static func makeError(code: Int, message: String) -> NSError {
        NSError(domain: "KuayunVPN", code: code, userInfo: [NSLocalizedDescriptionKey: message])
    }
}
