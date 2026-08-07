# apps/tauri/platforms/ios — AI 协作说明

iPhone 端位于 Tauri monorepo 的 `platforms/ios/`，与 Win/Mac/Linux 共享 [iOS 产品需求](../../../../docs/product/iOS客户端产品需求.md) 与后端 API。

## 技术栈

- Swift 5.9+ / SwiftUI
- Network Extension（Packet Tunnel Provider）
- 不复用 Tauri iOS 桩

## 架构约定

1. **Core/**：`APIClient`、`AuthStore`、共享模型；与 Android `/api/v1` 协议一致。
2. **Features/**：按业务模块分目录（Login、Connect、Nodes…）。
3. **PacketTunnel/**：VPN 数据面；主 App 仅通过 `NEVPNManager` + App Group IPC 交互。
4. **Shared/**：App Group 路径与 `VPNConfigStore`；主 App 写配置、Extension 读配置。
5. Clash YAML 注入规则直连逻辑 Phase C 对齐 Android `ClashDirectBypassPatcher`。

## 修改清单

- 新功能完成后更新根目录 `docs/功能todo.md`。
- 接口变更同步 `docs/product/iOS客户端产品需求.md`。

## 验证

- Phase A：`xcodebuild -scheme KuayunVPN -destination 'platform=iOS Simulator,name=iPhone 16' build`
- Phase B：`ConnectView` + `VPNController` + `PacketTunnelProvider`；Mihomo 二进制嵌入后真机验收。
