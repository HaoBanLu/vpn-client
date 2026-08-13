# Tauri 与 Android 功能对齐清单

> **统一版本线**：自 **1.2 / code 120** 起跨端迭代  
> **包名**：`com.vpn.kuayun`（iOS：`.app` / `.tunnel`；Group：`group.com.vpn.kuayun`）  
> **Android 发包**：仅 **`apps/tauri`**；**`apps/android` 已存档**（[`ARCHIVE.md`](../../apps/android/ARCHIVE.md)）  
> **最后核对**：2026-08-13（发版 `1.2.23` / code `143`）

---

## 0. `apps/tauri` 功能与平台一览（1.2）

| 端 | 代码 | UI | 数据面 | 发包命令 | 状态 |
|----|------|-----|--------|----------|------|
| Windows | `src/` + `src-tauri/` | Vue Ky | 系统代理 + mihomo | `npm run tauri:win:build` | ✅ 业务齐；E2E 待窗内验收 |
| macOS | 同上 | Vue Ky | 系统代理 | `npm run tauri:mac:build` | ✅ 同左；公证清单另见 |
| Linux | 同上 | Vue Ky | 系统代理 | `npm run tauri:linux:build` | ✅ CI 构建 |
| **Android** | `src/` + `src-tauri/android/` VPN overlay | **同一套 Vue** | Mihomo **TUN**（插件） | `npm run tauri:android:build:release` / Tag CI | ✅ **正式发包**（`apps/android` 已存档） |
| iPhone | `platforms/ios/` | SwiftUI | Packet Tunnel | `npm run tauri:ios:build:ipa` | 🚧 xcframework |

```text
apps/tauri/
├── src/                      # 全端共用 Vue（Win/Mac/Linux/Android WebView）
├── src-tauri/                # 桌面 Rust VPN + 托盘
│   └── android/              # Android VPN Kotlin overlay（sync → gen/android）
├── platforms/ios/            # 独立 Swift（不共用 Vue）
└── 跨云客户端打包说明.md      # 各端打包操作
```

**与 `apps/android` 关系**：**已存档、不再维护**。Tauri Android 仍链接其中的 `mihomo-core` JNI；业务与 UI 一律在 `apps/tauri`。历史 Compose 实现可作能力参考（TUN/KS/分应用），勿再发版。

---

## 1. 核心结论

| 维度 | 桌面 Tauri vs 原生 Android | Tauri Android（Vue）vs 原生 Android |
|------|---------------------------|-------------------------------------|
| **页面 / 路由（会员业务）** | ✅ 齐（19/19，不含分应用） | ✅ 同桌面 Vue；缺「分应用直连」页 |
| **API** | ✅ ~98% | ✅ 同 `client.ts` |
| **连接页体验** | ✅ Hero / 中断 / 切节点 | ✅ 同 Vue；数据面走 TUN 插件 |
| **连接与隐私设置** | ⚠️ 桌面精简（代理/托盘） | ✅ Always-on 引导 + 开机自连 + 省电入口（**不做自研 KS**） |
| **Android 独有能力入口** | ❌ 产品不对齐到桌面 | ✅ 分应用直连已齐；系统加固走 Always-on（非自研 KS） |

**一句话**：会员业务页桌面已齐；**Tauri 接 Android 发包后，缺的是 Android 专属界面与设置**，不是登录/套餐/节点主流程。

---

## 2. 产品范围（什么算「对齐」）

### 2.1 必须对齐（所有对外端）

- 登录 / 注册 / 找回密码（含邮箱验证码）
- 连接 + `GET /client/config`
- 节点列表、地区、延迟、选路
- 连接场景、路由模式、规则直连
- 套餐购买、流量、充值、订单、工单、客服、**帮助中心**、关于
- 我的设备、修改密码、诊断日志（`app_debug_enabled`）
- 鉴权失效 / 套餐过期断连
- 应用更新检查

### 2.2 仅 Android（桌面不对齐；Tauri Android **应对齐**）

| 能力 | 桌面 | Tauri Android 目标 |
|------|------|-------------------|
| Mihomo **TUN** | ❌ 系统代理 MVP | ✅ 插件已有路径 |
| **Kill Switch** / 断网保护 UI | ❌ UI 隐藏 | ❌ **不做自研 KS**；用系统 Always-on + lockdown 引导 |
| **分应用直连** | ❌ | ✅ 列表 + TUN `addDisallowedApplication` |
| **开机自连** | ❌ | ✅ `VpnBootReceiver` + 设置开关 |
| **保护等级 / 省电白名单 / Always-on VPN** | ❌ | ✅ 设置页状态卡 + 系统深链 |
| **FCM** | ❌ | 可选（P2） |

---

## 3. 页面对照表

| 页面 / 路由 | 原生 Android | Tauri 桌面 / Tauri Android Vue | iPhone |
|-------------|--------------|--------------------------------|--------|
| 启动页 | splash | 桌面：主窗隐藏就绪再 show；Android：直进 index | ✅ |
| 隐私同意 | 静默基线 | `/privacy` | ✅ |
| 登录 / 注册 / 找回 | ✅ | ✅ | ✅ |
| 连接 / 节点 / 套餐 / 我的 | ✅ | ✅ | ✅ |
| 流量 / 充值 / 订单 | ✅ | ✅ | ✅ |
| 修改密码 / 设备 / 工单 / 客服 | ✅ | ✅ | ✅ |
| **帮助中心** | ✅（1.2 补「我的」入口） | ✅ `/main/help` | ✅ |
| 规则直连 | ✅ | ✅（Android 全员；桌面调试） | ✅ |
| **分应用直连** | ✅ | ✅ 完整页 + 插件 | ❌ |
| 连接与隐私 | ✅ 完整 | 桌面精简；Android：重连/开机自连/Always-on 引导/省电（无自研 KS） | ⚠️ |
| 诊断日志 | ✅ | ✅（1.2 起非仅桌面） | ✅ |
| 关于 | ✅ | ✅ | ✅ |

---

## 4. 连接与 VPN 能力对照

| 能力 | 原生 Android | Tauri 桌面 | Tauri Android | iPhone |
|------|-------------|------------|---------------|--------|
| Mihomo | TUN | 子进程 + 系统代理 | TUN 插件 | 🚧 xcframework |
| 规则直连 | ✅ | ✅ | ✅（前端+配置） | ✅ |
| 分应用直连 | ✅ | ❌ | ✅ UI/插件 | ❌ |
| Kill Switch | ✅ | 代码有 / UI 关 | ❌ 产品取舍：Always-on 引导替代自研 KS | 说明 |
| 自动重连 | ✅ 完整重连 | ✅ 完整重连 | ✅ Vue + 原生 rebind | 部分 |
| 开机自连 | ✅ | ❌ | ✅ BootReceiver | ❌ |
| Failover | 默认关 | 默认关 | 同 Vue | ❌ |
| 会话速率 | ✅ | ✅ 字节差+EMA | ✅ 原生 tracker SSOT，页面直读 bps | ❌ |
| 连接中可中断/切节点 | ✅ | ✅ | ✅ Vue | 部分 |
| 系统托盘 / 关窗驻留 | — | ✅ | — | — |
| FCM | ✅ | ❌ | 可选 | ❌ |
| 应用更新 | APK | updater | APK 插件路径 | 版本检查 |

---

## 5. API 封装对照

桌面 / Tauri Android 共用 `apps/tauri/src/api/client.ts`，与原生 `AppRepository` 覆盖面一致（认证、订阅、config、节点、套餐、流量、工单、客服、诊断、版本检查）。

---

## 6. 仍缺少 / 待办（1.2）

### P0 — 发版 / 真连

| ID | 端 | 项 | 状态 |
|----|-----|-----|------|
| P0-DESKTOP-E2E | Win/Mac | 系统代理真连 E2E | 🚧 |
| P0-TAURI-ANDROID-RELEASE | Android | Tag CI 走 `apps/tauri` + 正式签名 | ✅ | `app-release.yml` + `build-tauri-android-release.sh`（2026-08-07） |
| P0-IOS-NATIVE | iPhone | Mihomo xcframework | 🚧 |

### P1 — Tauri Android 对齐原生（界面/能力缺口）

| 项 | 原生有 | Tauri Vue 现状 | 动作 |
|----|--------|----------------|------|
| 分应用直连页 | ✅ | ✅ | 已落地 |
| 连接与隐私：Always-on / 开机自连 / 省电 | ✅ | ✅（2026-08-12） | **产品决策：Android 用系统 Always-on+lockdown 引导，不做自研 Kill Switch** |
| 诊断日志 | ✅ | ✅ 1.2 已对全端开放入口 | 验 Android WebView 上传 |
| 帮助中心入口 | ✅ 1.2 已补 | ✅ | — |

### 已明确：桌面刻意不对齐

TUN UI、Kill Switch UI、分应用、FCM、开机自连、保护等级（**仅桌面**）。

---

## 7. 代码索引

| 对照项 | 原生 Android | Tauri（桌面+Android Vue） | iPhone |
|--------|--------------|---------------------------|--------|
| 路由 | `Routes.kt` | `src/router/index.ts` | Swift Tab |
| API | `AppRepository` | `src/api/client.ts` | `APIClient.swift` |
| VPN | `VpnService` | 桌面 `desktop.rs`；Android `src-tauri/android/.../Vpn*` | PacketTunnel |
| 设置 | `StabilitySettingsScreen` | `StabilitySettingsView.vue` | `SettingsView.swift` |
| 版本 | `app/build.gradle.kts` | `app-meta.ts` + package/tauri.conf | `project.yml` |

---

## 8. 验证命令

```bash
# 桌面
cd apps/tauri && npm test && npm run preflight:desktop && npm run tauri:win:dev

# Android APK（正式路径）
cd apps/tauri && npm run tauri:android:build:release
# Tag 发版 CI 亦构建本路径，见 app-release.yml
```

---

## 9. 关联文档

- [跨云客户端打包说明](../../apps/tauri/跨云客户端打包说明.md)
- [GitHub自动打包与密钥配置说明](../guides/GitHub自动打包与密钥配置说明.md)
- [功能状态清单](../功能todo.md)
- [macOS 桌面验收清单](macOS桌面验收清单.md)
- [iOS Mihomo xcframework 接入](iOS-Mihomo-xcframework接入.md)

---

## 10–12. 连接页 UI / 开发说明 / 齐全度（摘要）

桌面连接页 Hero、PC 壳、`Ky*` 布局、未选节点跳转、会话速率（Android 通知与连接页共用 tracker）、协议门控、默认关 failover、断网完整重连等 **已与原生 Android 成熟化对齐**（详见历史核对 2026-07～08）。  
发版硬缺口仍是：**桌面窗内系统代理 E2E**、**Tauri Android 正式签名与专属设置页**、**iOS xcframework**。
