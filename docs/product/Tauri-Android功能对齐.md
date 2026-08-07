# Tauri 与 Android 功能对齐清单

> **基准**：`apps/android` — 全功能参考实现  
> **Tauri 桌面**：`apps/tauri/src/` + `src-tauri/` — Win / macOS / Linux **共用一套 Vue**  
> **iPhone**：`apps/tauri/platforms/ios/` — **独立 Swift**，不共用 Vue  
> **最后核对**：2026-08-02（Android **3.16.1** / 桌面 **1.5.2**：断网恢复直接完整重连；Android 须先备配置再 KS；桌面不对齐 TUN/KS）

---

## 1. 核心结论

**桌面端会员业务、页面路由、连接页主流程 UI/体验（含连接中可中断与切节点）、PC 壳布局已与 Android 对齐；发版前仍差「Tauri 窗口内系统代理 E2E」验收。iPhone 业务页齐，VPN 仍阻塞于 Mihomo xcframework。**

| 维度 | Tauri 桌面 vs Android | iPhone vs Android |
|------|----------------------|-------------------|
| **页面 / 路由** | ✅ **100%**（19/19，不含分应用直连；子页均在 `/main/*`） | ✅ **95%** |
| **API 封装** | ✅ **~98%**（与 `client.ts` 一致） | ✅ **~95%** |
| **会员业务** | ✅ 已对齐 | ✅ 已对齐 |
| **连接页 UI/体验** | ✅ **已对齐**（Hero、会话切换、连接中可中断/切节点） | ⚠️ 部分对齐 |
| **PC 布局与间距** | ✅ **已收尾**（`KyTabPage`/`KyGrid2`/壳内子页侧栏常驻） | — |
| **连接数据面** | 🚧 **系统代理 MVP**（探测已修，E2E 待验；无 TUN 假连路径） | ❌ **阻塞 xcframework** |
| **Android 独有能力** | ❌ **产品不要求对齐** | ❌ **产品不要求对齐** |

**一句话**：桌面端**业务 + UI/连接体验**可视为齐全；**真正上网**必须在 Tauri 窗口做系统代理 E2E，浏览器只能测 API/页面。

---

## 2. 产品范围（什么算「对齐」）

### 2.1 必须对齐（所有对外端）

- 登录 / 注册 / 找回密码（含邮箱验证码）
- 连接 + `GET /client/config`（Mihomo Clash YAML）
- 节点列表、地区、延迟、选路
- 连接场景、路由模式（全流量 / 分流）、规则直连
- 套餐购买、流量、充值、订单、工单、客服、帮助
- 我的设备、修改密码、诊断日志（`app_debug_enabled`）
- 鉴权失效 / 套餐过期断连
- 应用更新检查（各端安装方式不同）

### 2.2 仅 Android（Tauri **不要求**对齐）

| 能力 | 说明 |
|------|------|
| Mihomo **TUN** 全隧道 | 桌面 MVP 固定 **系统代理**（`DESKTOP_MVP_PROXY_ONLY`） |
| **Kill Switch** | 桌面 Rust 有实现，UI 已隐藏，非发版门禁 |
| **分应用直连** | `VpnService` 按包名绕行；桌面/iOS 无入口 |
| **FCM** 远程推送 | 仅 Android |
| **开机自连** | `VpnBootReceiver`；桌面无此场景 |
| **保护等级 / 系统加固** | 始终开启 VPN、省电白名单等 Android 专属 UX |
| **切网 HEAL / 没网不拆隧道** | Android **3.16.1** / 桌面 **1.5.2**：自动重连开 → **直接完整重连**；Android **先拉配置/缓存再 KS**（禁止先断再调 API）；没网不空转 |
| **系统 VPN 硬门禁** | Android 3.15+ `TunDataPlaneVerifier`；桌面以经系统代理路径为准（无 TUN 同构） |
| **探测 / Failover** | Android：inactive **强制断开** + 默认关自动 failover；桌面：有探测、降级**不断开** + 默认关自动 failover（**非「桌面无探测」**） |
| **应用内 APK 安装闭环** | 桌面用 `tauri-plugin-updater` |

---

## 3. 页面对照表

| 页面 / 路由 | Android `Routes` | Tauri 桌面 `router` | iPhone Swift |
|-------------|------------------|---------------------|--------------|
| 启动页 | Android splash | 原生 `splash` 窗 + `/splash` redirect | iOS `SplashView` ✅ |
| 隐私同意 | （静默基线 / Connect 门控） | `/privacy` ✅ 基础同意页 | `PrivacyView` ✅ |
| 登录 | `login` | `/login` ✅ | `LoginView` ✅ |
| 注册 | `register` | `/register` ✅ | `RegisterView` ✅ |
| 找回密码 | `forgot_password` | `/forgot-password` ✅ | `ForgotPasswordView` ✅ |
| 连接 | Main Tab | `/main/connect` ✅ | `ConnectView` ✅ |
| 节点 | Main Tab | `/main/nodes` ✅ | `NodesView` ✅ |
| 套餐 | Main Tab | `/main/packages` ✅ | `PackagesView` ✅（Profile 内链） |
| 我的 | Main Tab | `/main/profile` ✅ | `ProfileView` ✅ |
| 流量统计 | `traffic` | `/main/traffic` ✅ | `TrafficView` ✅ |
| USDT 充值 | `recharge` | `/main/recharge` ✅ | `RechargeView` ✅ |
| 充值记录 | `recharge_orders` | `/main/recharge-orders` ✅ | `RechargeOrdersView` ✅ |
| 购买记录 | `purchase_orders` | `/main/purchase-orders` ✅ | `PurchaseOrdersView` ✅ |
| 修改密码 | `change_password` | `/main/change-password` ✅ | `ChangePasswordView` ✅ |
| 我的工单 | `tickets` | `/main/tickets` ✅ | `TicketsView` ✅ |
| 在线客服 | `support` | `/main/support` ✅ | `SupportView` ✅ |
| 帮助中心 | `help` | `/main/help` ✅ | `HelpView` ✅ |
| 我的设备 | `devices` | `/main/devices` ✅ | `DevicesView` ✅ |
| 规则直连 | `direct_bypass_rule` | `/main/direct-bypass-rules` ✅ | `DirectBypassRulesView` ✅ |
| **分应用直连** | `app_direct_connect` | ❌ 不做 | ❌ 不做 |
| 连接与隐私 | `stability_settings` | `/main/stability-settings` ✅ 精简版 | `SettingsView` ✅ 精简版 |
| 诊断日志 | `debug_log` | `/main/debug-log` ✅ | `DebugLogView` ✅ |
| 关于 | `about` | `/main/about` ✅ | `AboutView` ✅ |

> 桌面子页均挂在 `MainShell` 下（`/main/*`），侧栏常驻；旧路径如 `/purchase-orders` 自动重定向。

---

## 4. 连接与 VPN 能力对照

| 能力 | Android | Tauri 桌面 | iPhone | 备注 |
|------|---------|------------|--------|------|
| Mihomo 内核 | ✅ `mihomo-core` TUN | ✅ 子进程 + mixed-port | 🚧 占位 | iOS 缺 xcframework |
| 连接模式 | TUN | **系统代理**（MVP 锁定） | Packet Tunnel | `DESKTOP_MVP_PROXY_ONLY` |
| `/client/config` | ✅ | ✅ | ✅ | 含 scenario / route_mode |
| 连接场景 | ✅ | ✅ | ✅ | overseas / domestic_return 等 |
| 路由模式 | ✅ | ✅ | ✅ | full / split |
| 规则直连 | ✅ | ✅ | ✅ | Clash rules → DIRECT |
| 分应用直连 | ✅ | ❌ | ❌ | 仅 Android |
| Kill Switch | ✅ | 代码有 / UI 关 | 说明文案 | 非 Tauri 门禁 |
| 自动重连退避 | ✅ | ✅ | 部分 | 桌面 3/6/10s |
| 开机自连 | ✅ | ❌ | ❌ | 仅 Android |
| 节点 Failover | ✅ 默认关自动切换 | ✅ 默认关（`AUTO_FAILOVER_ENABLED=false`） | ❌ | 仍记探针；不自动同区切节点 |
| 探测 degraded 策略 | ✅ 数据面 inactive 强制断开 | ✅ 降级不断开（`dataplaneDegradedDisconnectMs=0`） | 部分 | 桌面无 TUN；不做 gvisor |
| 泄露自检 + 历史 | ✅ | ✅ | ✅ 历史列表 | iOS `PrivacyProbeHistoryStore` |
| 会话流量 / 速率 | ✅ warmup+cap | ✅ warmup+cap（`estimateDisplayMbps`） | ❌ | 对齐 3s warmup / 400ms 采样 / 200Mbps 上限 |
| 连接中可中断 | ✅ | ✅ `interruptInFlightConnect` | 部分 | Hero 再点取消 |
| 连接中可切节点 | ✅ | ✅ 仅 `isSwitching` 禁用按钮 | 部分 | |
| 系统托盘 | — | ✅ | — | 桌面专属 |
| 关闭隐藏托盘 | — | ✅ | — | 桌面专属 |
| 恢复上次连接 | — | ✅ | 部分 | `restoreSession` |
| FCM / 推送 | ✅ | ❌ | ❌ | 仅 Android |
| 充值到账轮询通知 | ✅ | ✅ 本地轮询 | ✅ 本地轮询 | Profile Banner |
| 保护等级 UI | ✅ | ❌ | ❌ | Android 成熟化 |
| 隐私 onboarding | ✅ 完整 | 基础 `/privacy` | ❌ | 电池/VPN 设置引导 |
| 应用更新安装 | ✅ APK | ✅ updater | ✅ 版本检查 | 安装方式不同 |

---

## 5. API 封装对照

Tauri 桌面 `apps/tauri/src/api/client.ts` 与 Android `AppRepository` 均已覆盖：

| 模块 | 端点示例 | 桌面 | iOS `APIClient` |
|------|----------|:----:|:---------------:|
| 认证 | `auth/login`, `register`, `forgot-password`, `reset-password` | ✅ | ✅ 缺 `sendEmailCode` 封装 |
| 用户 | `users/me`, `password`, `preferences`, `sessions` | ✅ | ✅ |
| 订阅 | `subscription/active`, `usage`, `token` | ✅ | ✅ |
| 连接 | `client/config`, `nodes`, `batch-latency` | ✅ | ✅ |
| 商业 | `packages`, `orders`, `recharge-orders`, `payment-methods` | ✅ | ✅ |
| 流量 | `traffic/summary`, `daily` | ✅ | ✅ |
| 服务 | `tickets`, `support-config` | ✅ | ✅ |
| 运维 | `app-debug-logs`, `connect-dashboard`, `session/heartbeat` | ✅ | ✅ |
| 更新 | `client/version` | ✅ | ✅ |

---

## 6. 仍缺少 / 待办（按优先级）

> **2026-07-28 再核对**：桌面会员业务 + 连接页主体验已与 Android 对齐；**发版硬缺口**仍是系统代理真连 E2E。macOS 按 [验收清单](macOS桌面验收清单.md) 勾选；iOS 按 [xcframework 接入手册](iOS-Mihomo-xcframework接入.md) 推进。

### P0 — 发版阻塞

| ID | 端 | 项 | 状态 | 说明 |
|----|-----|-----|------|------|
| P0-DESKTOP-E2E | Win | 系统代理真连 E2E | 🚧 | 须在 **Tauri 窗口**完成；见功能 todo |
| P0-MAC-E2E | macOS | 系统代理真连 + 公证 | 🚧 | 清单：[macOS桌面验收清单.md](macOS桌面验收清单.md) |
| P0-IOS-NATIVE | iPhone | Mihomo xcframework 真出网 | 🚧 | 引擎桥接+NEProxySettings 已实现；**Mac 上** `tauri:ios:build-xcframework` → setup-native → 真机签名；[手册](iOS-Mihomo-xcframework接入.md) |
| P0-IOS-VPN-E2E | iPhone | 真机 VPN 验收 | 📋 | 依赖上项 + Apple NE 签名 |

### P1 — 体验 / 可信补齐（非阻塞）

| 端 | 项 | 状态 | 说明 |
|----|-----|------|------|
| 桌面 | 代理失败的用户可见反馈 | 📋 | Android：`dataplane inactive` 强制断；桌面无 TUN、降级默认不断开。宜明确「探针长期失败」提示，勿伪装全隧道已保护 |
| 桌面 | 「连接与隐私」文案边界 | ✅ | 仅自动重连/托盘/恢复会话 + 泄露自检；系统代理边界说明保留；检测摘要会员白话（2026-08-01） |
| 桌面 | 应用内升级链路可运营 | 📋 | updater 代码齐；需 pubkey + 后台发版流程闭环 |
| 桌面 | 独立 Splash（可选） | 📋 | `/splash` 现为 redirect；非 Android 级启动页 |
| iOS | 连接体验（协议门控/中断/速率） | 📋 | 依赖 VPN 可用后再补；远弱于桌面 |

### P1 — 已完成（保留作回归）

| 端 | 项 | 状态 | 说明 |
|----|-----|------|------|
| 桌面 | Hero / 未选节点跳转 / 移除分流开关 | ✅ | 对齐 Android 成熟化 |
| 桌面 | Windows 连接探测 | ✅ | `desktop_probe.rs` |
| 桌面 | PC 壳布局 / 子页侧栏 | ✅ | Ky 组件 + `/main/*` |
| 桌面 | 关自动 failover / 连接中可中断可切节点 | ✅ | 对齐 3.11 |
| 桌面 | 会话速率 warmup/cap / 协议门控 | ✅ | 随 1.1.0 |
| 桌面 | `preflight:desktop` / 浏览器跳过托盘 | ✅ | |

### 已明确不做（刻意不对齐）

- 桌面 / iOS：**TUN、Kill Switch UI、分应用直连、FCM、开机自连、保护等级/系统加固**
- Android **3.16.1** / 桌面 **1.5.2**：**切网/断网恢复直接完整重连**；Android 另有「先备配置再 KS」、硬门禁 / 没网等待
- 强行追求「综合对齐度 100%」
- IP 设置入口回连接页（Android 已移出；桌面 **不做**）

### 3.15.x 展示层对齐（2026-08-01）

| 项 | 状态 | 说明 |
|----|------|------|
| 连接与隐私：检测摘要白话 + 最近 1 条 | ✅ | `StabilitySettingsView` / `privacy-probe-history`；保留系统代理边界说明 |
| 节点列表：行尾紧凑「连接/切换」 | ✅ | `KyNodeCard` 非通栏按钮；语义仍为点按钮才连 |
| 连接页 Hero / 关于版本 / 下载二维码 | ✅ / N/A | 连接与关于此前已齐；二维码在会员 Web，非 Tauri |

### 桌面专属（非对齐项，保留）

- 系统托盘、关窗驻留、启动恢复上次会话
- `tauri-plugin-updater`（安装方式不同于 APK）

---

## 7. 代码索引

```text
apps/android/          # 基准：Kotlin + Mihomo TUN
apps/tauri/
├── src/               # Win/Mac/Linux 共用 Vue（业务 View + Ky 组件）
├── src-tauri/src/vpn/ # 桌面 Mihomo + 系统代理 + Kill Switch（保留）
├── qa-matrix.json     # 跨端验收矩阵
└── platforms/ios/     # SwiftUI + PacketTunnel
```

| 对照项 | Android | Tauri 桌面 | iPhone |
|--------|---------|------------|--------|
| 路由 | `Routes.kt` | `src/router/index.ts`（`/main/*`） | `ProfileView` + Tab |
| API | `AppRepository` | `src/api/client.ts` | `Core/APIClient.swift` |
| VPN | `VpnService` + mihomo | `src-tauri/src/vpn/desktop.rs` | `PacketTunnel/MihomoRunner.swift` |
| 设置 | `StabilitySettingsScreen` | `StabilitySettingsView.vue` | `SettingsView.swift` |

---

## 8. 验证命令

```bash
# 桌面 MVP（Windows 示例）
cd apps/tauri && npm run fetch:mihomo && npm run preflight:desktop && npm run tauri:win:dev
# 登录 → 连接 → 确认系统代理与出口 IP

# 桌面单元测试
cd apps/tauri && npm run test

# iOS 编译（需 macOS）
cd apps/tauri && npm run tauri:ios:generate && npm run tauri:ios:check
```

---

## 9. 关联文档

- [Tauri 桌面 PRD](Tauri桌面客户端重构产品需求.md)
- [iOS PRD](iOS客户端产品需求.md)
- [Mihomo 内核 PRD](自研App嵌入Mihomo内核产品需求.md)
- [功能状态清单](../功能todo.md)

---

## 10. 连接页 UI / PC 布局（2026-07-10）

> **基准实现**：`apps/android/.../ConnectScreen.kt` + `ConnectHero.kt`  
> **当前桌面**：`apps/tauri/src/views/connect/ConnectView.vue` + `ConnectHero.vue`（Hero-first）

### 10.1 结构对比（Hero 对齐项 ✅）

| 项 | Android（当前） | Tauri 桌面（2026-07-10） | 状态 |
|----|----------------|-------------------------|------|
| **连接大按钮位置** | 品牌头后第一个主元素，居中 | `ConnectHero` 置顶，520px 居中 | ✅ |
| **状态文案** | 按钮下方（未连接/已保护 + 节点） | Hero 标题 + 副标题 | ✅ |
| **多IP / 单IP** | 连接页不展示 | 已移除；API 保留 | ✅ |
| **国内站点直连开关** | 无（按地区自动 split） | 已移除 | ✅ |
| **已连接会话信息** | 速率/时长/余量 | `ConnectSessionCard` | ✅ |
| **未连接快捷状态** | 节点 Tab | `ConnectQuickStatus` → 节点 Tab | ✅ |
| **未选节点点连接** | 跳转节点 Tab | 跳转 `/main/nodes`，不报错 | ✅ |

### 10.2 PC 壳与布局（已收尾）

| 项 | 说明 | 状态 |
|----|------|------|
| **Tab 页统一壳** | `KyTabPage`（品牌头+刷新+Spin+Stack） | ✅ |
| **双列网格** | `KyGrid2`（≥960px）；套餐/节点卡片 | ✅ |
| **业务卡片** | `KyPackageCard` / `KyNodeCard` / `KySubscriptionSummary` | ✅ |
| **子页侧栏常驻** | 子路由进 `MainShell`；`PageHeader` 返回+面包屑 | ✅ |
| **IP 设置入口** | Android 已移出连接页；桌面 **不做** | ❌ 不做 |
| **连接页次要信息双列** | 宽屏并排余量/到期 | 📋 可选 |

### 10.3 PC 自适应现状

| 项 | PRD 目标 | 当前实现 | 状态 |
|----|---------|---------|------|
| 默认窗口 | 1200×800 | `tauri.conf.json` 已配置 | ✅ |
| 左栏 | Win/Mac ≥768px 桌面布局 | `MainShell` 侧栏；子页「我的」高亮 | ✅ |
| 主内容垂直高度 | 可滚动、不塌陷 | `style.css` flex 链 | ✅ |
| Tab 切换不空白 | 路由切换稳定 | 已移除 `Transition` leave 卡死 | ✅ |
| 卡片/区块间距 | 对齐 Android 16dp | `KyStack` + token 间距 | ✅ |
| 连接页宽屏排版 | Hero 居中 | 520px 居中单列 | ✅ |

### 10.4 相关文档时效说明

| 文档 | 是否过时 | 说明 |
|------|---------|------|
| [Tauri-Android功能对齐](Tauri-Android功能对齐.md) | **已更新** | 本文 §10–§12 |
| [Tauri桌面客户端重构产品需求](Tauri桌面客户端重构产品需求.md) | **部分过时** | §2.2 仍写 Ant Design Vue；Ky UI 已落地 |
| [自研App对标快帆功能补齐需求](自研App对标快帆功能补齐需求.md) | **部分过时** | §7.1 多IP/IP 设置；Android 与 Tauri 均已回退 |
| [自研App需求文档](自研App需求文档.md) | **有效** | 连接页无分流开关、Hero 主按钮——Tauri 已执行 |
| [客户端弱网与选路优化方案](客户端弱网与选路优化方案.md) | **有效** | 连接页无 split 开关——Tauri 已移除 |
| [功能todo.md](../功能todo.md) | **已同步** | 2026-07-24 |

---

## 11. 开发与验收说明（2026-07-24）

### 11.1 API 与环境变量

| 场景 | `VITE_API_BASE_URL` | 说明 |
|------|---------------------|------|
| **开发**（`npm run dev` / `tauri:dev`） | `/api`（`.env.development`） | Vite 代理到联调 API（如 `192.229.87.112:44080`），避免浏览器 **CORS** |
| **正式构建** | 与 Android `releaseAppBaseUrl` 一致的完整 URL（见 `apps/tauri/.env`） | 示例联调地址 `http://192.229.87.112:44080/api`，**勿当成公网生产域名** |

- 仅配置到 `/api`，业务路径为 `/api/v1/...`。
- `scripts/desktop-dev-windows.ps1` 会依次加载 `.env` → `.env.development`（后者覆盖前者）。
- Docker MCP 浏览器经 `host.docker.internal` 访问时，`vite.config.ts` 已放行 `allowedHosts`。

### 11.2 浏览器 vs Tauri 窗口

| 能力 | 浏览器 `http://127.0.0.1:5173` | Tauri 桌面窗口 |
|------|-------------------------------|----------------|
| 登录 / 套餐 / 节点 / 我的子页 UI | ✅（需开发代理） | ✅ |
| VPN 连接（`vpn_connect`） | ❌（`invoke` 不可用） | ✅ |
| 代理健康探测 | ❌ | ✅（`desktop_probe.rs`） |
| 系统托盘 | 跳过（无 Tauri 运行时） | ✅ |

**验收连接**：必须在 **Tauri 窗口**点「一键连接」，不能只在浏览器验证。

### 11.3 Windows 连接探测（2026-07-10 修复；策略 2026-07-20/23 再对齐）

- **问题**：原 PowerShell `Invoke-WebRequest` 经本地代理探测易误报失败 → `degraded` → 曾触发 90s 自动断开。
- **修复**：`desktop_probe.rs` 使用 `curl.exe -I` + 重试；**现行** `dataplaneDegradedDisconnectMs=0`（降级不断开）+ `AUTO_FAILOVER_ENABLED=false`。
- **验证**：`npm run tauri:dev` → 连接后 Hero 显示「已保护」，终端不应持续刷 PowerShell 代理错误。

---

## 12. `apps/tauri` 功能齐全度总览（2026-07-24）

### 12.1 桌面（Win / macOS / Linux）— 结论

| 类别 | 齐全？ | 说明 |
|------|--------|------|
| 认证（登录/注册/找回） | ✅ | 2026-07-24 浏览器冒烟：登录 → `/main/connect` |
| 四 Tab + 我的全部子页 | ✅ | 连接 Hero / 节点地区+批量测速 / 套餐 / 我的 |
| 套餐购买 / 流量 / 订单 | ✅ | |
| 节点选择 / 测速 / 选路 | ✅ | 「连接此节点」= **选完即连**（对齐 Android）；冒烟见大陆 VLESS + 新加坡 Trojan |
| 连接场景 / 规则直连 | ✅ | |
| 连接页 UX（Hero、中断、切节点、会话切换） | ✅ | 对齐 Android 3.10/3.11（代码+单测） |
| 协议门控（sing-box 中转） | ✅ | |
| 自动 failover | ✅ 默认关 | 与 Android 一致 |
| 会话速率护栏 | ✅ | warmup + 上限 |
| PC 壳（侧栏、双列、子页面包屑） | ✅ | |
| 系统代理连接 + 探测代码 | ✅ 代码齐 | **E2E 须 Tauri 窗口**（浏览器无 `invoke`） |
| 托盘 / 自动重连 / 恢复会话 | ✅ | |
| 应用更新 updater | ✅ | 需配置 pubkey |
| TUN / Kill Switch / 分应用 / FCM | ❌ 不做 | 产品范围 |

**桌面发版门禁只剩：在真实 Tauri 窗口完成「登录 → 选节点 → 一键连接 → 出口 IP / 代理生效」E2E。**

**本机 2026-07-24 抽检**：`npm test` 49 通过；`preflight:desktop` 通过；`tauri:dev` 已拉起 `vpn-tauri.exe` + Vite `5173`；浏览器完成登录/连接页/节点列表冒烟；**VPN 真连未在自动化中验收**（须人工点窗口）。

### 12.2 iPhone（`platforms/ios`）— 结论

| 类别 | 齐全？ | 说明 |
|------|--------|------|
| 业务页 / API | ✅ 基本齐 | |
| Packet Tunnel 脚手架 | ✅ | |
| Mihomo xcframework | 📋 | **P0 阻塞** |
| 真机 VPN | 📋 | 依赖上项 + 签名 |

### 12.3 建议验收顺序

1. `cd apps/tauri && npm run test && npm run preflight:desktop`
2. `npm run tauri:win:dev`（或 mac/linux）→ 完整连接 E2E
3. （可选）浏览器冒烟：登录 + 各 Tab + 我的子页（不测 VPN）
4. iOS：`tauri:ios:setup-native` → 真机 VPN