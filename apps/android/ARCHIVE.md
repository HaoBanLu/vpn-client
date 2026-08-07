# apps/android — 存档（ARCHIVED）

> **状态**：自 **2026-08-07** 起 **不再维护、不再作为发版工程**。  
> **现行 Android 发包**：[`apps/tauri`](../tauri/)（包名 **`com.vpn.kuayun`**）。

## 为什么还在仓库里

| 保留内容 | 用途 |
|----------|------|
| `mihomo-core/` + `scripts/setup-mihomo-native.sh` | Tauri Android 构建仍依赖此 JNI 模块 |
| `keystore/`（若仓库跟踪） | CI 正式签名可回退读取 |
| Compose 源码 | 历史参考（历史包名 `com.vpn.member`） |

## 禁止

- 勿再改业务、勿再发本目录产物上架
- 新需求一律改 **`apps/tauri`**

## 包名

| 端 | 包名 / Bundle ID |
|----|------------------|
| Android / 桌面 identifier | `com.vpn.kuayun` |
| iOS App | `com.vpn.kuayun.app` |
| iOS Tunnel | `com.vpn.kuayun.tunnel` |
| iOS App Group | `group.com.vpn.kuayun` |
