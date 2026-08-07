# 跨云 Tauri 跨端客户端（`apps/tauri`）

**Win / macOS / Linux / iPhone** 四端代码统一在本目录；共享 Vue 3 前端 + 各平台原生壳。

| 平台 | 代码位置 | 技术栈 | 状态 |
|------|----------|--------|------|
| **Windows** | `src-tauri/src/vpn/` + `resources/bin/` | Tauri 2 + Mihomo + wintun | 主交付 |
| **macOS** | 同上 + `system_proxy.rs` | Tauri 2 + Mihomo + utun/代理 | 需实机验证 |
| **Linux** | 同上 + gsettings 代理 | Tauri 2 + Mihomo | 有限支持（GNOME 等） |
| **iPhone** | [`platforms/ios/`](platforms/ios/) | SwiftUI + Network Extension | Phase A 脚手架 |

> **Android** 主客户端在 [`apps/android`](../android/)，不在此目录维护（历史 Tauri Android overlay 见 `src-tauri/android/`，已 deprecated）。

## 目录结构

```text
apps/tauri/
├── README.md                 # 本文件
├── package.json              # npm 脚本（桌面 / 校验 / iOS 生成）
├── src/                      # 共享 Vue 3 前端（Win/Mac/Linux WebView）
├── src-tauri/                # Tauri Rust 壳 + 桌面 VPN（Win/Mac/Linux）
│   ├── src/vpn/              # desktop.rs、kill_switch、system_proxy …
│   ├── resources/bin/        # 各平台 mihomo 二进制
│   └── android/              # deprecated overlay，勿作主 Android 工程
├── platforms/                # 各端原生工程索引（见 platforms/README.md）
│   ├── desktop/              # 桌面三端说明（代码在 src-tauri）
│   ├── ios/                  # iPhone Swift + PacketTunnel
│   ├── linux/                # Linux 桌面环境说明
│   ├── macos/                # macOS 签名/TUN 说明
│   └── windows/              # Windows 管理员/Kill Switch 说明
├── scripts/                  # 构建、fetch-mihomo、verify
└── 跨云客户端打包说明.md      # 详细打包与联调
```

## 常用命令

```bash
cd apps/tauri
npm run setup              # 依赖 + 下载当前平台 mihomo

# Windows
npm run tauri:dev          # 或 tauri:win:dev
npm run tauri:build        # 或 tauri:win:build（NSIS）

# macOS
npm run tauri:mac:dev
npm run tauri:mac:build    # .dmg

# Linux
npm run tauri:linux:dev
npm run tauri:linux:build  # .deb
npm run tauri:linux:build:appimage

# iPhone（需 macOS）
npm run tauri:ios:generate
npm run tauri:ios:build    # xcodegen + Simulator 编译（CI 同款）

npm run verify             # Windows 校验脚本
npm run test               # 前端单测
```

## API 与开发环境

| 文件 | 用途 |
|------|------|
| `.env` | 生产构建：`VITE_API_BASE_URL=http://192.229.87.112:44080/api`（与 Android 对齐） |
| `.env.development` | 本地开发：`VITE_API_BASE_URL=/api`，由 Vite 代理到远端，**避免浏览器 CORS** |
| `.env.local` | 个人覆盖（不提交） |

- 业务请求路径：`VITE_API_BASE_URL + /v1/...`（不要写成 `/api/v1` 作为 base）。
- **浏览器**（`http://127.0.0.1:5173`）可测登录与页面 UI；**VPN 连接**必须在 **Tauri 桌面窗口**验证。
- 子页路由在 `/main/*`（如 `/main/support`），旧路径会重定向；桌面侧栏在子页中保持可见。
- Windows 连接后健康探测见 `src-tauri/src/vpn/desktop_probe.rs`（`curl HEAD` + 重试）。

## 功能齐全度（摘要）

| 范围 | 状态 |
|------|------|
| 会员业务 / 四 Tab / 我的子页 | ✅ 齐全（对齐 Android，不含分应用直连） |
| PC 壳布局（侧栏、双列、Ky 组件） | ✅ 已收尾 |
| 桌面系统代理连接 | 🚧 代码齐，待 **Tauri 窗口 E2E** |
| iPhone VPN | 📋 阻塞 Mihomo xcframework |

详见 [Tauri-Android功能对齐 §12](../../docs/product/Tauri-Android功能对齐.md)。

详细命令见 [跨云客户端打包说明.md](跨云客户端打包说明.md)。

## 关联文档

- [Tauri 桌面客户端 PRD](../../docs/product/Tauri桌面客户端重构产品需求.md)
- [Tauri 与 Android 对齐清单](../../docs/product/Tauri-Android功能对齐.md)
- [iOS 客户端 PRD](../../docs/product/iOS客户端产品需求.md)
- [功能 todo](../../docs/功能todo.md)