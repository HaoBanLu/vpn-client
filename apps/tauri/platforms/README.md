# Tauri 各端代码索引

本目录收纳 **平台专属工程与说明**；Win/macOS/Linux 的 Rust/VPN 实现仍在 [`../src-tauri/`](../src-tauri/)（Tauri 约定），此处做清晰分区与文档入口。

## 支持端一览

| 端 | 目录 | 源码主路径 | 构建入口 |
|----|------|------------|----------|
| Windows | [`windows/`](windows/) | `src-tauri/src/vpn/`、`kill_switch.rs` | `npm run tauri:build` |
| macOS | [`macos/`](macos/) | 同上 + `system_proxy.rs` | `npm run tauri:build`（需在 macOS） |
| Linux | [`linux/`](linux/) | 同上 + gsettings 代理 | `npm run tauri:build` |
| iPhone | [`ios/`](ios/) | SwiftUI + PacketTunnel | `npm run ios:generate` → Xcode |

## 共享层

| 层级 | 路径 | 说明 |
|------|------|------|
| UI | [`../src/`](../src/) | Vue 3 + Pinia；Win/Mac/Linux 共用 |
| API | [`../src/api/`](../src/api/) | 与 Android 共用 `/api/v1` |
| VPN 逻辑（TS） | [`../src/lib/vpn/`](../src/lib/vpn/) | 连接场景、规则直连、Failover |
| Tauri 壳 | [`../src-tauri/`](../src-tauri/) | Rust commands、托盘、桌面 mihomo |

## Android 说明

会员 **Android 正式版** 在 [`../../android/`](../../android/)（Kotlin + Compose）。  
`src-tauri/android/` 仅为历史 Tauri overlay，**不要**与主 Android 工程混用。
