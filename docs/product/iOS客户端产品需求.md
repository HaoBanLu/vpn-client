# iOS 原生客户端产品需求（PRD）

> **文档状态**：Phase B 进行中（UI/API 就绪，数据面待 xcframework）  
> **核心结论**：iPhone 为 **SwiftUI + Network Extension** 独立工程；**Mihomo 选型正确**，真实代理阻塞于 **iOS native 库集成**，非「选错内核」。**不要求**与 Android 在 Kill Switch / FCM 上对齐。  
> **开发环境**：Swift 代码可在任意 OS 编辑；**编译、签名、真机 VPN 调试需 macOS + Xcode**；Windows 本机无法完成 iOS 打包与真机验网。
> **关联文档**：[Tauri桌面客户端重构产品需求](Tauri桌面客户端重构产品需求.md)、[App隐私保护与连接安全产品需求](App隐私保护与连接安全产品需求.md)、[自研App嵌入Mihomo内核产品需求](自研App嵌入Mihomo内核产品需求.md)  
> **最后更新**：2026-07-05

---

## 1. 核心结论与量化目标

**跨云 iPhone 客户端交付「会员业务 + Mihomo 代理连接」；Kill Switch/FCM 仅 Android 需要，iOS 不对齐。MVP 阻塞项：Mihomo xcframework + Mac 构建环境。**

| KPI | 目标 | 验收 |
|-----|------|------|
| 核心业务 | 登录/连接/节点/套餐/我的 | 与桌面/API 一致 |
| VPN 真实连通 | 连接后出口 IP 变化 | 依赖 xcframework 集成后真机测 |
| 打包 | Simulator 构建 + TestFlight | macOS + 开发者账号 |

---

## 2. 背景（SCQA）

### 2.1 情景

- Android 与 Tauri 桌面端已统一 Mihomo + `/client/config` Clash YAML。
- Tauri iOS 仅 Spike 桩，无 PacketTunnelProvider，不可上架。
- 产品需覆盖 **Win/Mac（Tauri）+ Android（Kotlin）+ iPhone（Swift）**。

### 2.2 冲突

| 问题 | 影响 |
|------|------|
| Tauri 无法提供 NE VPN | 无法作为 iPhone 方案 |
| iOS 后台与 Kill Switch 模型不同 | 不能直接移植 Android VpnService |
| App Store VPN 类目审核严格 | 需独立隐私说明与 entitlement |

### 2.3 疑问

如何在 Apple 生态内交付与 Android 等价的 VPN 体验？

### 2.4 答案

新建 `apps/tauri/platforms/ios/`（SwiftUI App + Network Extension Target），复用后端 API 与 Clash 配置；分 Phase A–D 交付。

---

## 3. 产品范围

### 3.1 在范围（MVP）

| 模块 | 说明 |
|------|------|
| 会员认证 | 登录/注册/忘记密码/会话失效 |
| 连接 | 一键连接、节点列表、智能选路、连接场景 |
| 套餐 | 购买/流量/订单/充值 |
| 账户 | 修改密码、我的设备 |
| 服务 | 在线客服、工单、帮助中心（Clash 订阅导出） |
| 规则直连 | 域名/IP 规则（非分应用直连） |
| 诊断 | App 调试日志（`app_debug_enabled`） |
| 更新 | App Store + `GET /client/version` |

### 3.2 不在范围（产品决定）

- Kill Switch / On-Demand 与 Android 对齐（iOS 可选，非 MVP）
- FCM / 远程推送（仅 Android）
- 分应用直连（仅 Android）
- iPad 专属布局（兼容即可）
- OpenVPN / WireGuard 客户端原生协议

---

## 4. 技术架构

```text
apps/tauri/platforms/ios/
├── KuayunVPN/              # SwiftUI 主 App
│   ├── Features/           # Connect, Nodes, Profile, Packages
│   ├── Core/               # API, Auth, Theme (对齐 shared tokens)
│   └── Resources/
├── PacketTunnel/           # Network Extension
│   ├── MihomoRunner.swift  # 嵌入/调用 mihomo-core (ios arm64)
│   └── TunnelProvider.swift
└── project.yml             # XcodeGen → KuayunVPN.xcodeproj
```

### 4.1 API 复用

与 Android/Tauri 共用：

- `POST /v1/auth/login`
- `GET /v1/client/config`
- `GET /v1/nodes`
- `GET /v1/users/me/preferences`（连接场景）
- `POST /v1/users/me/app-debug-logs`
- `GET /v1/client/version`

### 4.2 VPN 数据面

1. 主 App 拉取 Clash YAML，注入规则直连（同 Android `ClashDirectBypassPatcher`）。
2. 写入 App Group 共享目录，NE 读取并启动 Mihomo TUN。
3. 连接状态通过 `NEVPNStatus` + 自定义 IPC（与 Android `VpnConnectionBus` 等价）。

### 4.3 为何当前「连上但上不了网」

| 已完成 | 未完成（阻塞真实代理） |
|--------|------------------------|
| 登录、拉 `/client/config`、Clash 清洗 | **Mihomo iOS xcframework**（无官方 Release 二进制） |
| App Group 写配置、NE 路由/DNS | PacketTunnel 内启动 native Mihomo |
| VPN 授权弹窗、状态「已连接」 | 真机签名 + Network Extension entitlement |

**与是否使用 Windows 开发无关的核心阻塞**：缺 iOS 原生 Mihomo 库。  
**与 Mac 相关的阻塞**：Xcode 编译、真机安装、TestFlight 上传必须在 macOS 完成。

### 4.4 开发环境要求

| 工作 | Windows | macOS |
|------|---------|-------|
| 编辑 Swift 源码 | ✅ | ✅ |
| `xcodebuild` / 模拟器 | ❌ | ✅ |
| 真机 VPN 调试 | ❌ | ✅ + 开发者账号 |
| 集成 xcframework | ❌ 无法本地验 | ✅ |

---

## 5. 里程碑

| 阶段 | 周期 | 交付 |
|------|------|------|
| Phase A | 2 周 | 工程脚手架 + 登录 + API 层 |
| Phase B | 3 周 | Packet Tunnel + Mihomo 连接 MVP |
| Phase C | 2 周 | 节点/套餐/Profile 业务页 + 充值/工单/设备/改密/客服/帮助 |
| Phase D | 2 周 | 隐私/诊断/TestFlight |

---

## 6. 风险与依赖

| 风险 | 缓解 |
|------|------|
| NE entitlement 审批慢 | 提前申请 Personal VPN |
| Mihomo iOS 二进制体积 | 与 Android 共用构建流水线 |
| 国区 App Store VPN 政策 | 准备合规说明与测试账号 |

---

## 7. 验收标准（MVP）

- [ ] TestFlight 可安装，登录后连接成功
- [ ] 切换节点不断连重连
- [ ] Kill Switch / On-Demand 配置可开关
- [ ] 泄露自检与 Android 结果一致
- [ ] `docs/功能todo.md` 登记 iOS 条目
