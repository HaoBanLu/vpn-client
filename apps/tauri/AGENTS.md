# apps/tauri — AI 协作说明

## 支持端

| 端 | 代码路径 | 与 Android 对齐 |
|----|----------|-----------------|
| Windows / macOS / Linux | `src/` + `src-tauri/` | 业务/UI/连接体验 ✅；上网 🚧 系统代理 E2E（Mac 见验收清单） |
| iPhone | `platforms/ios/` | 业务 ✅；VPN 🚧 xcframework 接线中 |

Android 主工程在 `apps/android/`。对齐清单：[`docs/product/Tauri-Android功能对齐.md`](../../docs/product/Tauri-Android功能对齐.md)。  
macOS 验收：[`docs/product/macOS桌面验收清单.md`](../../docs/product/macOS桌面验收清单.md)。  
iOS 内核：[`docs/product/iOS-Mihomo-xcframework接入.md`](../../docs/product/iOS-Mihomo-xcframework接入.md)。

版本以 `src/lib/app-meta.ts` / `package.json` / `tauri.conf.json` 为准（当前线：`1.5.2`）。

## 约定

1. 共享业务 UI 与 API 放 `src/`，禁止在 `platforms/*` 重复实现会员流程。
2. 桌面 VPN 逻辑放 `src-tauri/src/vpn/`；iPhone VPN 放 `platforms/ios/PacketTunnel/`。
3. 「我的」子页必须挂在 `MainShell` 的 `/main/*` 下，保持侧栏；旧顶层路径用 redirect。
4. 布局优先复用 `KyTabPage` / `KyStack` / `KyGrid2` 与业务卡片组件。
5. **不对齐**：TUN 栈 UI、Kill Switch UI、分应用直连、FCM、开机自连。
6. **应对齐**：协议门控、连接中可中断/切节点、关自动 failover、速率 warmup/cap、会话「当前线路/切换」、**断网/网卡恢复完整重连**。
7. 各端说明见 `platforms/README.md`；打包见 [跨云客户端打包说明.md](跨云客户端打包说明.md)。
8. 功能状态同步根目录 [`docs/功能todo.md`](../../docs/功能todo.md)。

## 断网 / 网络恢复（强制防回归）

> 对齐 Android **3.15.7**：自动重连开启时切网/断网恢复 → **直接完整重连**（防抖），禁止先 HEAL 再赌探测。

| 必须 | 禁止 |
|------|------|
| `online` → 防抖后 `schedule_reconnect`（含已连接态） | 仅 `vpn_heal` + 探测 OK 当恢复 |
| 没网不空转重连；`offline` 提示等待 | 「进程还在」就算恢复 |
| 周期探活连续失败（有网）→ 升级重连 | 仅记 degraded 永不重连 |
| 关闭自动重连时仍可轻量 `vpn_heal` | 用户主动断开后仍自动连 |

代码锚点：`network-restore-policy.ts`、`connect.ts` `recoverAfterNetworkOnline`。改动须补单测（`npm test`）。

## 验证

```bash
cd apps/tauri
npm test
```

VPN 真连必须在 **Tauri 窗口**验收，浏览器无法测 `invoke`。
