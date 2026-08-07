# Tauri 桌面客户端重构产品需求（PRD）

> **文档状态**：进行中  
> **核心结论**：`apps/tauri` 为 **Windows + macOS + Linux + iPhone** 四端 monorepo；桌面端 Tauri 2 + Vue 3 + Ky UI；iPhone 为 `platforms/ios/` Swift 原生；以 `apps/android` 为功能基准；**弃用 Tauri Android overlay**。  
> **关联文档**：[App成熟化产品路线](App成熟化产品路线.md)、[App隐私保护与连接安全产品需求](App隐私保护与连接安全产品需求.md)、[跨云客户端打包说明](../../apps/tauri/跨云客户端打包说明.md)  
> **最后更新**：2026-07-05

---

## 1. 核心结论与量化目标

**跨云桌面客户端应成为 Android 的功能子集镜像（业务 100% 对齐、VPN/隐私按桌面能力等价实现），UI 统一 Kuayun 设计体系，代码可维护 3 年以上。**

| KPI | 目标 | 验收 |
|-----|------|------|
| 业务功能对齐 Android | ≥95% 页面与 API 覆盖 | QA 矩阵全绿 |
| PC 布局可用率 | Win/Mac 默认窗口即左栏布局 | 1200×800 开箱即侧栏 |
| UI 依赖精简 | 移除 `ant-design-vue`，包体积降 ≥30% | bundle analyze |
| 共享代码复用 | API/types/format 走 `frontend/shared` | grep 无重复实现 |
| 桌面 VPN 可信 | TUN 或系统代理 + Kill Switch | 断线 5s 内无裸连 |
| 单测覆盖（Rust + TS 核心） | ≥60% connect/desktop 模块 | CI 门禁 |

---

## 2. 背景（SCQA）

### 2.1 情景

- `apps/android` 已迭代至 v3.7，具备完整会员业务、Mihomo TUN、隐私安全、推送与稳定性体系。
- `apps/tauri` 已具备 18 个业务页面，但 UI 混用 Ant Design Vue 与自研 Ky 组件，桌面布局断点异常，VPN 仅系统代理，与 Android 差距大。
- 产品目标：**Win/Mac PC 端 + iPhone**（Android 继续原生维护）。

### 2.2 冲突

| 问题 | 影响 |
|------|------|
| Ant Design Vue 与 VPN 消费级 UI 不匹配 | 双轨样式、维护成本高 |
| `DESKTOP_BREAKPOINT = 10000` | PC 永远走手机底栏 |
| Tauri Android 与 `apps/android` 双轨 sync | 永远追不上主工程 |
| 桌面无 Kill Switch / TUN | 非完整 VPN 体验 |
| iOS Tauri 桩代码 | 无法作为 iPhone 方案 |

### 2.3 疑问

如何在不大改后端的前提下，让 Tauri 桌面端**长期稳定**并对齐 Android？

### 2.4 答案

**方案 A**：Tauri + Vue 3 保留；移除 Ant Design Vue；扩展 Ky UI；抽 shared core；Tauri 仅 desktop；分 Phase 0–4 实施。

---

## 3. 产品范围

### 3.1 在范围

| 端 | 说明 | 代码路径 |
|----|------|----------|
| Windows | Tauri NSIS 安装包，主交付平台 | `src-tauri/` |
| macOS | Tauri `.app`，需实机验证签名与代理/TUN | `src-tauri/` |
| Linux | Tauri AppImage/deb 等，GNOME 代理 + TUN | `src-tauri/` |
| iPhone | SwiftUI + Network Extension | `platforms/ios/`，详见 [iOS PRD](iOS客户端产品需求.md) |

### 3.2 不在范围（本 PRD）

| 项 | 处理 |
|----|------|
| Android | 继续 `apps/android`，Tauri Android overlay **标记 deprecated** |

### 3.3 布局规范

```
PC（≥960px 或 platform=windows/macos）
┌──────────┬────────────────────────────┐
│ 左栏菜单  │ 顶栏：状态 + 断开           │
│ 连接     ├────────────────────────────┤
│ 节点     │        主内容              │
│ 套餐     │                            │
│ 我的     │                            │
└──────────┴────────────────────────────┘
默认窗口：1200×800

Mobile 窄窗 / 未来 iPhone WebView
┌────────────────────┐
│      主内容         │
├────────────────────┤
│ 连接 节点 套餐 我的  │  62px 底栏
└────────────────────┘
```

---

## 4. 功能需求（对标 Android）

### 4.1 Phase 0 — 基础重构（本迭代）

| ID | 功能 | 优先级 | 说明 |
|:--:|------|:------:|------|
| P0-UI-1 | 移除 Ant Design Vue | P0 | 全面 Ky 组件替代 |
| P0-UI-2 | PC 侧栏布局生效 | P0 | 断点 960px + Tauri platform 检测 |
| P0-UI-3 | 默认窗口 1200×800 | P0 | `tauri.conf.json` |
| P0-UI-4 | `frontend/shared` 接入 | P0 | format/types/theme tokens |
| P0-UI-5 | Tauri Android deprecated 文档 | P1 | 打包说明更新 |

### 4.2 Phase 1 — VPN 桌面能力（3–4 周）

| ID | 功能 | Android 对标 |
|:--:|------|-------------|
| P1-VPN-1 | 桌面 TUN 模式（Win wintun / Mac utun） | Mihomo TUN |
| P1-VPN-2 | 系统代理 / TUN 可切换 | — |
| P1-VPN-3 | Kill Switch | `VpnTunnelService` Kill Switch |
| P1-VPN-4 | 自动重连 + 崩溃恢复 | `VpnAutoReconnectPolicy` |
| P1-VPN-5 | 系统托盘 + 最小化保持连接 | 前台服务等价 |
| P1-VPN-6 | `tauri-plugin-updater` | `AppUpdateChecker` |
| P1-VPN-7 | 连接状态机（degraded / 探测） | `ConnectProbePolicy` |

### 4.3 Phase 2 — 业务与隐私（2–3 周）

| ID | 功能 | Android 对标 |
|:--:|------|-------------|
| P2-BIZ-1 | 「连接与隐私」设置页 | `StabilitySettingsScreen` |
| P2-BIZ-2 | 规则直连（域名/进程） | `DirectBypassRuleScreen` |
| P2-BIZ-3 | 泄露自检 | 隐私 PRD 自检项 |
| P2-BIZ-4 | 鉴权断开 + 套餐失效断连 | `PrivacyForceDisconnectEvents` |
| P2-BIZ-5 | Profile 补全：我的设备、连接设置 | `ProfileScreen` |
| P2-BIZ-6 | 诊断日志 | `DebugLogScreen` |
| P2-BIZ-7 | 连接场景（自动/回国/海外） | `ConnectionScenario` |
| P2-BIZ-8 | 节点 Failover 简化版 | `NodeFailoverMonitor` |

### 4.4 Phase 3 — iPhone（独立项目）

见 [iOS客户端产品需求](iOS客户端产品需求.md)。

### 4.5 Phase 4 — 工程质量

- Connect store / desktop VPN Rust 单测
- E2E：连接 / 断连 / 切换节点
- CI：`npm run verify` + 覆盖率

---

## 5. 技术架构

```text
frontend/shared/          # API types、theme tokens、format 工具
apps/tauri/
├── src/
│   ├── components/ky/    # Ky UI 组件库（对齐 KuayunTheme）
│   ├── lib/ui/           # message、confirm 服务
│   ├── lib/layout.ts     # 桌面/移动布局检测
│   ├── stores/           # auth、connect、account
│   └── views/            # 18 个页面
└── src-tauri/
    └── src/vpn/          # desktop.rs、Kill Switch、TUN（Phase 1）
```

### 5.1 UI 选型（已定）

| 保留 | 移除 |
|------|------|
| Vue 3 + TS + Pinia + Vue Router | `ant-design-vue` |
| Vite 6 + Tauri 2 | Tauri Android 日常维护 |
| `@ant-design/icons-vue`（仅图标） | — |
| 自研 Ky 组件 + CSS variables | — |

### 5.2 Design Token

颜色/间距/圆角定义于 `frontend/shared/theme/tokens.ts`，Tauri `style.css` 引用同名 CSS 变量，与 Android `KuayunTheme.kt` 视觉对齐。

---

## 6. 非功能需求

| 项 | 要求 |
|----|------|
| 性能 | 首屏 <2s（桌面） |
| 安全 | JWT 本地加密存储；Kill Switch 默认 ON（Phase 1 后） |
| 兼容 | Win 10+、macOS 12+ |
| 可维护 | 单文件组件 `<script setup>`；handler 薄逻辑 |
| 测试 | Phase 0 后 `npm run build` 必须通过；Phase 4 起单测门禁 |

---

## 7. 验收标准

### Phase 0（本迭代）

- [ ] `npm run build` 无 ant-design-vue 依赖
- [ ] Win 1200×800 默认左栏 + 顶栏
- [ ] 窄窗 (<960px) 底栏 4 Tab
- [ ] 全部 18 页面视觉无回归（人工 smoke）
- [ ] `frontend/shared` 被 format 模块引用
- [ ] `docs/功能todo.md` 已更新

### Phase 1

- [ ] 桌面 TUN 连接成功，探测通过
- [ ] Kill Switch：断网 5s 内无裸连
- [ ] 托盘图标可断开/显示状态
- [ ] 内置 updater 可检测并下载更新

---

## 8. 风险与依赖

| 风险 | 缓解 |
|------|------|
| macOS TUN 需签名/权限 | Phase 1 先做系统代理 fallback |
| Kill Switch 平台差异大 | Win 防火墙规则 + Mac pf 分阶段 |
| 移除 Ant Design 工作量大 | Phase 0 一次性 Ky 组件库 |
| iOS 不能复用 Tauri VPN | 独立 Swift 项目，API 共享 |

---

## 9. 里程碑

| 阶段 | 时间 | 交付 |
|------|------|------|
| Phase 0 | 2026-07-05 起 1–2 周 | Ky UI + PC 布局 + shared |
| Phase 1 | +3–4 周 | TUN/Kill Switch/托盘/updater |
| Phase 2 | +2–3 周 | 隐私/连接设置对齐 Android |
| Phase 3 | 并行 6–10 周 | iOS 独立 App |
| Phase 4 | 持续 | 单测 + CI 门禁 |
