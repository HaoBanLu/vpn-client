# Mihomo iOS Bridge（真实引擎）

本目录是 **可出网** 的 Go 桥接：链入 `github.com/metacubex/mihomo`，导出 `ky_mihomo_*`。

```bash
# 必须在 macOS + Xcode + Go
cd apps/tauri
npm run tauri:ios:build-xcframework
npm run tauri:ios:setup-native
npm run tauri:ios:generate
```

PacketTunnel 使用 **mixed-port + NEProxySettings**（对齐桌面系统代理 MVP），不依赖在 NE 内自建 utun。
