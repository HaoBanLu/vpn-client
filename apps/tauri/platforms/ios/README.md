# Kuayun VPN — iPhone 客户端

位于 **`apps/tauri/platforms/ios/`**，与 Win/macOS/Linux 同属 Tauri 跨端工程；SwiftUI + Network Extension，业务 API 与 `apps/android` 对齐。

## 前置要求

- macOS + Xcode 15+
- [XcodeGen](https://github.com/yonaskolb/XcodeGen)

```bash
cd apps/tauri
npm run tauri:ios:generate
npm run tauri:ios:build
open platforms/ios/KuayunVPN.xcodeproj
```

CI：`.github/workflows/tauri-ci.yml` → `tauri-ios-build`。

## API 基址

```bash
# Xcode Scheme → Run → Environment Variables
API_BASE_URL=https://your-api.example.com/api/v1
```

## 目录结构

```text
apps/tauri/platforms/ios/
├── KuayunVPN/           # 主 App（SwiftUI）
├── PacketTunnel/        # Network Extension + MihomoRunner
├── Shared/              # App Group 配置
├── native/              # C ABI：mihomo_bridge.h
├── vendor/              # 本地 Mihomo.xcframework（gitignore）
├── project.yml
└── AGENTS.md
```

## 里程碑

| 阶段 | 状态 | 说明 |
|------|------|------|
| Phase A | ✅ | 脚手架 + 登录 + API |
| Phase B | 🚧 | NE + Clash 清洗；**xcframework 真流量待链** |
| Phase C | ✅ | 业务页齐 |
| Phase D | 🚧 | Failover + TestFlight |

## Mihomo / xcframework（P0）

官方 mihomo Release **无** iOS 二进制。完整步骤见：

**[iOS-Mihomo-xcframework接入.md](../../../../docs/product/iOS-Mihomo-xcframework接入.md)**

```bash
cd apps/tauri
npm run tauri:ios:build-xcframework   # macOS 构建（须再补全引擎实现）
# 或把现成 Mihomo.xcframework 放到 platforms/ios/vendor/
npm run tauri:ios:setup-native
npm run tauri:ios:generate && npm run tauri:ios:build
```

无内核时连接会 **明确失败**（不再空隧道假成功）。

## 签名与 Entitlement

上架前申请 **Personal VPN** / Network Extension，并配置 App Group（主 App ↔ Extension 共享 Clash 配置）。
