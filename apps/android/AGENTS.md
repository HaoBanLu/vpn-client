# apps/android — AI 协作说明

会员 Android 客户端（Mihomo TUN）。通用规范见根目录 [`AGENTS.md`](../../AGENTS.md) 与 [`.cursor/rules/android-app.mdc`](../../.cursor/rules/android-app.mdc)。

## 定位

| 项 | 说明 |
|----|------|
| 版本 | 以 `app/build.gradle.kts` 为准；**现行发版 `3.16.2` / `55`**（Application 级 `VpnReconnectSupervisor`：FAILED/dataplane 可恢复；Service 看门狗；DNS 防抖加长） |
| 对齐 | 桌面/iOS 见 [`docs/product/Tauri-Android功能对齐.md`](../../docs/product/Tauri-Android功能对齐.md) |
| 状态 | [`docs/功能todo.md`](../../docs/功能todo.md) |

## 连接可信（强制）

- 全流量模式下，**mixed-port 通 ≠ 用户有网**；「已保护」须 **系统 VPN Network 真实出网**（`vpn_network_ok=true`，含 VALIDATED 或 HTTP 探测）。禁止仅凭 mixed / TUN 字节 / TCP 日志放行。
- 跨云自身**不得**强制 `addDisallowedApplication`：否则系统 VPN 探测与浏览器不同路，换 gvisor/mixed/system 也无效。
- 出海探海外 204；回国探国内站（禁止回国仅用 gstatic）。见 `docs/product/连接可信-系统VPN硬门禁产品需求.md`。
- `dataplane inactive` → 立即断开并提示，禁止 keep_tunnel 假连。
- 默认关闭自动同区 failover；连接失败阻断默认关（设置可开）。
- **海外回国首连门禁（防回归）**：连上前硬探测可保留，但 **禁止** 把首连重试砍得过短（3.11 曾用 2×1.5s，缅甸→港 Reality→国内落地易误杀）。须走 `PostConnectVerifyPolicy`（海外时区 + `domestic_return`：更长 settle/次数；国内首连保持默认 3 次）。改探测策略时补单测并同步本条。

## 切网 / 断网恢复（强制防回归）

> **唯一默认策略（自动重连开）**：断网→有网 / WiFi↔蜂窝 → **直接完整重连**。  
> **禁止**再改回「自愈 + 探测 OK 就算恢复」（luban7733 多次假恢复）。

| 必须 | 禁止 |
|------|------|
| `decide(autoReconnect=true)` → 一律 `SCHEDULE_RECONNECT` | 返回 `HEAL` 或先探测再决定是否重连 |
| `onNetworkRestored` / `onTransportChanged` → `VpnReconnectSupervisor` → `scheduleAutoReconnect` | 默认路径调用 `recoverAfterNetworkChange` |
| **先** `prepareConnectMaterials`（API/缓存）**再** `disconnectHoldingKillSwitch` | 先 KS 再拉 API（必 30s 超时） |
| 防抖结束后进入执行态，**禁止**被后续 dns 事件 `cancel`；dns 用更长防抖 | 准备重连后被风暴掐死（3.15.7/3.16 日志 21:27） |
| 单测 `autoReconnectEnabled_neverReturnsHeal` 保持绿 | 删掉或弱化该防回归测试 |
| 没物理网不空转；事件防抖 1.5s（dns 3s） | 离线连扣重连次数 |
| 关闭自动重连：才允许轻量 HEAL | 用户主动断开后仍自动连 |
| 意外 FAILED / Service 看门狗 → 监督器调度重连 | 恢复决策仅挂在 ConnectViewModel |

代码锚点：`VpnReconnectSupervisor`、`NetworkRestorePolicy`、`ConnectViewModel.scheduleAutoReconnect`。桌面同策略见 `apps/tauri/AGENTS.md`。

## 约定

1. VPN 逻辑在 `app/src/main/java/com/vpn/member/vpn/`；UI 在 `ui/`。
2. 协议门控：`AppProtocolSupport`（与 Tauri `app-protocol-support.ts` 语义对齐）。
3. 隐私：静默基线（`PrivacyBaselineMigrator`）；勿恢复强弹 onboarding。
4. 用户可见变更同步 `docs/功能todo.md`。
5. 发版：`docs/guides/App-Android-发版检查清单.md`。
6. `scripts/_emulator_shots/` 等本地抓包/截图勿提交（已 gitignore）。

## 验证

```bash
cd apps/android
./gradlew :app:testDebugUnitTest
```
