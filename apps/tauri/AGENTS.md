# apps/tauri — AI 协作说明

## 支持端

| 端 | 代码路径 | 说明 |
|----|----------|------|
| Windows / macOS / Linux | `src/` + `src-tauri/` | 业务/UI ✅；上网 🚧 系统代理 E2E |
| **Android** | `src/` + `src-tauri/android/` | **正式发包**；Vue UI + TUN 插件；Tag CI 走本工程 |
| iPhone | `platforms/ios/` | 业务 ✅；VPN 🚧 xcframework |

`apps/android/` **已存档**（[`../android/ARCHIVE.md`](../android/ARCHIVE.md)），仅 `mihomo-core` 仍作 JNI 依赖。  
对齐清单：[`docs/product/Tauri-Android功能对齐.md`](../../docs/product/Tauri-Android功能对齐.md)。

版本以 `src/lib/app-meta.ts` / `package.json` / `tauri.conf.json` 为准（**当前线：`1.2.4` / code `124`**；`tauri.conf` version **必须**完整 semver）。  
包名 / identifier：**`com.vpn.kuayun`**（Android overlay 包：`com.vpn.kuayun.vpn`）。

## 约定

1. 共享业务 UI 与 API 放 `src/`。
2. 桌面 VPN：`src-tauri/src/vpn/`；Android VPN：`src-tauri/android/`（sync → gen）；iPhone：`platforms/ios/PacketTunnel/`。
3. 「我的」子页挂 `/main/*`，保持侧栏。
4. 布局复用 `KyTabPage` / `KyStack` / `KyGrid2`。
5. **桌面不对齐**：TUN UI、Kill Switch UI、分应用、FCM、开机自连。
6. **Android 应对齐**（相对历史 Compose）：分应用、KS/开机自连/保护等级等（见对齐清单 §6）。
7. **全端应对齐**：协议门控、连接中可中断/切节点、关自动 failover、速率 warmup/cap、断网完整重连。
8. 打包见 [跨云客户端打包说明.md](跨云客户端打包说明.md)。
9. 功能状态同步 [`docs/功能todo.md`](../../docs/功能todo.md)。

## 断网 / 网络恢复（强制防回归）

自动重连开启时切网/断网恢复 → **直接完整重连**（防抖），禁止先 HEAL 再赌探测。  
代码：`network-restore-policy.ts`、`connect.ts`。改动须补单测。

## 验证

```bash
cd apps/tauri
npm test
npm run tauri:android:build:release
```
