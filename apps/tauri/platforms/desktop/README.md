# 桌面端（Windows / macOS / Linux）

三端共用 **同一套 Tauri + Vue 工程**，平台差异由 Rust `cfg(target_os = ...)` 与打包目标区分。

## 代码位置

| 模块 | 路径 |
|------|------|
| Rust VPN 核心 | [`../../src-tauri/src/vpn/`](../../src-tauri/src/vpn/) |
| Kill Switch | [`../../src-tauri/src/vpn/kill_switch.rs`](../../src-tauri/src/vpn/kill_switch.rs)（当前 **仅 Windows**） |
| 系统代理 | [`../../src-tauri/src/vpn/system_proxy.rs`](../../src-tauri/src/vpn/system_proxy.rs) |
| Mihomo 二进制 | [`../../src-tauri/resources/bin/`](../../src-tauri/resources/bin/) |
| 前端连接态 | [`../../src/stores/connect.ts`](../../src/stores/connect.ts) |
| Tauri 配置 | [`../../src-tauri/tauri.conf.json`](../../src-tauri/tauri.conf.json) |

## 分平台说明

- [Windows](../windows/README.md) — wintun、防火墙 Kill Switch、NSIS 安装包
- [macOS](../macos/README.md) — utun/代理、签名、Kill Switch 待实现
- [Linux](../linux/README.md) — gsettings 代理、桌面环境限制

## 构建

```bash
cd apps/tauri
npm run tauri:dev      # 开发
npm run tauri:build    # 安装包（当前脚本以 Windows 为主，macOS/Linux 需在对应 OS 上执行 tauri build）
```
