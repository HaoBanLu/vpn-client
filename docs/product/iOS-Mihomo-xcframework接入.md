# iOS Mihomo xcframework 接入手册

> **目标**：iPhone **真机能上网**（对齐桌面：mixed-port + 代理引流）。  
> **最后更新**：2026-07-28

---

## 0. 出网架构（已实现）

```text
主 App 拉 /client/config
  → App Group 写入 Clash YAML
  → 启动 PacketTunnel
       → NEProxySettings → 127.0.0.1:17890
       → Mihomo（xcframework）读 config.yaml，开 mixed-port
```

与 Windows/macOS 桌面 **同一思路**（系统/隧道代理进 Mihomo），不是 Android 那套 VpnService TUN fd。

---

## 1. 在 Mac 上一键构建并接线

```bash
cd apps/tauri

# 1) 编译真实 mihomo 引擎 → vendor/Mihomo.xcframework
npm run tauri:ios:build-xcframework

# 2) 链入 PacketTunnel + project.yml（MIHOMO_NATIVE）
npm run tauri:ios:setup-native

# 3) 生成工程并编译
npm run tauri:ios:generate
npm run tauri:ios:build

# 4) Xcode 真机：签名 Personal VPN + App Group 后 Run
open platforms/ios/KuayunVPN.xcodeproj
```

桥接源码：`platforms/ios/native/mihomo-bridge/`（`hub.Parse` 启停引擎）。

---

## 2. 验收

| # | 步骤 | 期望 |
|---|------|------|
| 1 | 真机安装并授权 VPN | 系统出现 VPN 配置 |
| 2 | 登录 → 选节点 → 连接 | Extension 启动无 1003/1004 |
| 3 | Safari 打开 ip 查询页 | 出口为节点 IP |
| 4 | 断开 | VPN 图标消失，可再连 |

---

## 3. 常见失败

| 现象 | 处理 |
|------|------|
| `build-xcframework` 非 Darwin | 必须在 Mac 执行 |
| go get mihomo 失败 | 检查网络/代理；可设 `MIHOMO_VERSION=v1.19.0` |
| 连接报「内核未集成」 | 未跑 setup-native 或 Frameworks 为空 |
| 已连接但网页不通 | 查 Extension 日志；确认 mixed-port=17890、NEProxySettings |
| 签名错误 | Apple Developer 开 Personal VPN + App Group |

---

## 4. 与桌面关系

| | Windows / macOS | iOS |
|--|-----------------|-----|
| 引擎 | 子进程 `mihomo` | xcframework 进程内 |
| 引流 | OS 系统代理 | `NEProxySettings` |
| 业务 UI | Vue | SwiftUI |
