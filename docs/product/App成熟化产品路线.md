# App 成熟化产品路线

> **核心结论**：跨云 Android App 已具备商业 VPN 的**能力骨架**（Mihomo TUN、Kill Switch、自动重连、隐私门控、诊断日志）。假连治理升级为 **P0-11 系统 VPN 硬门禁**：全流量须 `vpn_network_ok=true` 才显示已保护（见 [连接可信-系统VPN硬门禁](连接可信-系统VPN硬门禁产品需求.md)）；`dataplane inactive` 仍立即断开。质量门禁（真机矩阵、崩溃率看板）仍按 P1 推进。  
> **适用范围**：仅 Android（`apps/android`）；Tauri 跨端对齐见 [Tauri-Android功能对齐.md](Tauri-Android功能对齐.md)。  
> **文档状态**：进行中  
> **最后更新**：2026-07-29  
> **单一 backlog 来源**：本文 + [App企业级差距清单](App企业级差距清单.md) §5；实现状态同步至 [功能todo.md](../功能todo.md)

---

## 1. 量化目标（KPI）

| 指标 | 目标 | 验收方式 |
|------|------|----------|
| 曼谷回国节点可用率 | ≥80%（15 节点中 ≥12） | `ssh_debug/test_bangkok_cn_nodes_domestic_return.py` |
| 「已保护」与系统 VPN 数据面一致率 | ≥98% | 已保护时诊断须 `vpn_network_ok=true`；禁止假连组合 |
| 断网恢复自动连成功率 | ≥90% | `apps/android/scripts/vpn-stability-adb-check.sh` ×10 台 |
| 7 日崩溃率 | ≤0.5% | P1 看板（`app_debug_logs` + 可选 Crashlytics） |
| 首连 P95 | <5s | logcat `connect_timing` 采样 |
| 旧版 App 向后兼容 | 升级无闪退 | 无 `device_meta` 上报仍 HTTP 200 |

---

## 2. 已具备能力（摘要，非待办）

- Mihomo TUN 全量代理、`GET /client/config` 拉取 Clash YAML
- 断线自动重连（`VpnSessionStore` + 退避）、Kill Switch、IPv6 防护、鉴权断开
- 探测失败 → `DEGRADED` 保持连接（非主动断开）
- 隐私门控、连接与隐私页、泄露自检、用户通知 P0
- 诊断日志上报 + `device_meta`（版本、机型、TUN 栈）
- Geo 后台下载、周期健康探测、节点 failover 监控
- WG 链路异常节点过滤（`/nodes`、`/client/config`）

详见 [自研App稳定性评估与优化方案.md](自研App稳定性评估与优化方案.md) §8、[App隐私保护与连接安全产品需求.md](App隐私保护与连接安全产品需求.md)。

---

## 3. P0 — 连接可信（约 2 周）

| ID | 功能 | 说明 | 状态 | 主要位置 |
|:--:|------|------|:----:|----------|
| P0-1 | 「已保护」与 TUN 数据面绑定 | bus `degraded` 不被 mixed-port 探测覆盖 | ✅ | `ConnectProbePolicy.kt`、`ConnectViewModel.kt` |
| P0-2 | TUN 栈自动回退产品化 | 自动切换提示 + 稳定性页说明 | ✅ | `VpnTunnelService.kt`、`StabilitySettingsScreen.kt` |
| P0-3 | 周期探测路径一致 | `startPeriodicHealthProbe` 传 `domesticReturn` | ✅ | `ConnectViewModel.kt` |
| P0-4 | 海外回国 DNS 端到端 | 后端 + App DNS 对齐 | ✅ | `subscription_generator_clash_app.go` |
| P0-5 | 坏节点不下发 | WG 异常节点过滤 | ✅ | `node_connectable_health.go` |
| P0-6 | 数据面失效断开 | `dataplane inactive` **立即**断开（非长期假连）；其它探针可 soft-degraded 保隧道 | ✅ | `VpnTunnelService.markDataplaneDegraded` / `disconnectDataplaneInactive` |
| P0-7 | ConnectViewModel 单测 | bus 与探测合并 | ✅ | `ConnectProbePolicyTest.kt` |
| P0-8 | TunDataPlaneVerifier 单测扩展 | 边界用例 | ✅ | `TunDataPlaneVerifierLogicTest.kt` |
| P0-9 | 曼谷验收脚本纳入发版 | KPI ≥12/15 | 📋 | `ssh_debug/test_bangkok_cn_nodes_domestic_return.py` |
| P0-10 | 仪器化冒烟 | degraded 文案 | ✅ | `ConnectDataplaneInstrumentedTest.kt` |
| P0-11 | 系统 VPN 硬门禁（商业轻门禁 B） | 全流量强制 `vpn_network_ok`；出海/回国分探测 URL；禁止 mixed+TUN 字节单独放行 | ✅ | [连接可信 PRD](连接可信-系统VPN硬门禁产品需求.md)、`TunDataPlaneVerifier` |

---

## 4. P1 — 质量门禁（约 2–3 周）

| ID | 功能 | 说明 | 状态 |
|:--:|------|------|:--:|
| P1-1 | 真机稳定性 CI / 发版 checklist | `release-gate.sh` + `vpn-stability-adb-check.sh` | ✅ |
| P1-2 | 20 款 ROM 抽测矩阵 | 小米/华为/OPPO/vivo/Samsung；记录推荐 TUN 栈 | ✅ 模板 |
| P1-3 | 连接成功率 / 崩溃率看板 | 聚合 `app_debug_logs`；7 日摘要 API + 后台卡片 | ✅ |
| P1-4 | API 稳定性 P2 | SLO、`retryable` 统计、弱网回归；见 [App接口稳定性治理需求文档.md](App接口稳定性治理需求文档.md) | 🚧 采样✅/弱网脚本✅ |
| P1-5 | 隐私 P2 | 泄露自检历史（App 设置页）；保护等级变更埋点 | ✅ |
| P1-6 | 诊断日志运营闭环 | 后台按 `device_meta.app_version` / `tun_stack` 筛选 | ✅ |
| P1-7 | 发布门禁文档化 | [App-Android-发版检查清单.md](../guides/App-Android-发版检查清单.md) + §7 | ✅ |

---

## 5. P2 — 性能与体验（按需）

| ID | 功能 | 说明 | 状态 |
|:--:|------|------|:--:|
| P2-1 | APK 瘦身至 &lt;40MB | 瘦包 `-PslimNativeLibs=true`（~2.5MB）；默认全量 arm64 ~49MB | ✅ 瘦包 / 🚧 全量 |
| P2-2 | 首连 P95 真机基准 | `ConnectTimingArchive` + 后台 `/app-debug/connect-timing` | ✅ |
| P2-3 | 应用直连热更新 | `AppEvents.vpnConfigChanged` → 已连接轻量 reconnect | ✅ |
| P2-4 | 客户端节点 failover | 选择器已落地；**现行默认关闭自动同区切换**（`AUTO_FAILOVER_ENABLED=false`，2026-07-22） | ✅ 能力保留 / 默认关 |
| P2-5 | FCM 远程推送 | Token 注册 + Firebase Admin 发送 + 充值到账/驳回 | ✅ |

---

## 6. 明确不做 / 已废弃

| 项 | 原因 |
|----|------|
| sing-box / libbox 客户端内核 | 已由 Mihomo 替代（2026-06-22） |
| L4 socat / sing-box 双跳中转 | 已废弃 |
| `client_markets` 缅甸智能选路 | 已废弃 |
| 连接页单 IP/多 IP 展示菜单 | 已回退；API/设置能力保留 |
| MPTCP 多路径 | 长期研究项，不纳入当前迭代 |
| iOS 原生 | 范围外 |
| 快帆 egress IP 池 | 非核心稳定项 |
| App 原生 OpenVPN/WireGuard 协议栈 | 节点侧能力；App 走 Mihomo 即可 |

---

## 7. 发版验收清单（精简）

### 7.1 连接可信（P0 完成后必测）

| # | 步骤 | 期望 |
|---|------|------|
| 1 | 曼谷连芜湖/上海节点 → 打开抖音或浏览器访问国内站 | 可访问；诊断无 `tun_tcp_log=false` |
| 2 | 连接成功 → 飞行模式 10s → 关闭 | 10s 内自动重连或明确「重连中」 |
| 3 | `system` 栈机型连接回国节点 | 失败时自动切 `gvisor` 并提示 |
| 4 | 连接后浏览器无网 / `dataplane inactive` / `vpn_network_ok=false` | **立即**断开并提示换节点或改 gvisor，非「已连接但假连」 |
| 4b | mixed 通但系统 VPN 不通 | **不得**保持已保护（P0-11） |
| 5 | 旧版 App（无 device_meta）+ 新后端 | 不闪退；日志上报 200 |

### 7.2 稳定性（引用 [稳定性评估 §5](自研App稳定性评估与优化方案.md)）

- Release 连接/断开 20 次无 crash
- 鉴权踢线后 Kill Switch 阻断
- 用户手动断开后开飞行模式 → **不**自动重连

### 7.3 曼谷脚本

```bash
python ssh_debug/test_bangkok_cn_nodes_domestic_return.py
# 期望：≥12/15 节点国内站可达
```

### 7.4 Release Gate（自动化）

```bash
cd apps/android
SKIP_CONNECTED_TESTS=1 bash scripts/release-gate.sh   # 无模拟器：单元测试 + 全量 arm64 Release（~49MB）
# 有模拟器且 Docker API 在 10.0.2.2:48080：
bash scripts/release-gate.sh -PdebugApiBase=http://10.0.2.2:48080/
```

> **不使用瘦包**：勿加 `-PslimNativeLibs=true`（首连需下载 native，影响核心业务）。

### 7.5 企业级发版补充（E 系列）

| 项 | 发版前 |
|----|--------|
| E1 曼谷脚本 | 有 SSH 时跑 `test_bangkok_cn_nodes_domestic_return.py`，≥12/15 |
| E2 ROM 矩阵 | 核对 [App真机ROM抽测矩阵](../product/App真机ROM抽测矩阵.md) 本轮机型 |
| E3 崩溃率 | 后台「注册与邮箱 → 注册策略 → 崩溃率发版门禁」为 **PASS** 或已记录 REVIEW 原因；API `GET .../crash-health` |

完整差距见 [App企业级差距清单](../product/App企业级差距清单.md)。

完整清单见 [App-Android-发版检查清单.md](../guides/App-Android-发版检查清单.md)；ROM 抽测见 [App真机ROM抽测矩阵.md](App真机ROM抽测矩阵.md)。

---

## 8. 与现有 PRD 关系

| 文档 | 角色 |
|------|------|
| **本文** | 可执行 backlog：P0/P1/P2 + E 系列 |
| [App企业级差距清单](App企业级差距清单.md) | **企业级对标全景**、评分、E1–E6 建议顺序 |
| [自研App稳定性评估与优化方案.md](自研App稳定性评估与优化方案.md) | 历史差距分析 + §8 已实现清单 + §5 详细验收 |
| [App隐私保护与连接安全产品需求.md](App隐私保护与连接安全产品需求.md) | 隐私细节；P2 见本文 §4 |
| [App接口稳定性治理需求文档.md](App接口稳定性治理需求文档.md) | API 全链路；P2 见本文 P1-4 |
| [自研App嵌入Mihomo内核产品需求.md](自研App嵌入Mihomo内核产品需求.md) | 架构基线（已完成） |
| [自研App需求文档.md](自研App需求文档.md) | **历史 MVP 参考**，勿作现状依据 |
| [功能todo.md](../功能todo.md) | 全项目实现状态；与本文同步 |

---

## 9. 实现顺序（代码，历史 P0）

1. P0-3 周期探测 `domesticReturn`
2. P0-1 / P0-6 UI 与数据面、degraded 超时
3. P0-2 TUN 栈提示
4. P0-4 DNS 后端 + App 联调
5. P0-7 / P0-8 / P0-10 测试
6. P0-9 曼谷脚本验收

---

## 10. 企业级补齐（E 系列）

> 全景与评分见 [App企业级差距清单](App企业级差距清单.md)。**APK 默认全量包**，不用瘦包。

| ID | 功能 | 说明 | 状态 |
|:--:|------|------|:--:|
| E1 | 曼谷脚本发版必过 | `BANGKOK_ACCEPTANCE=1` + checklist | 📋 |
| E2 | ROM 真机矩阵填表 | `rom-matrix/records.json` + gate；目标 5/20 | 🚧 1/5 |
| E3 | 7 日崩溃率发版门槛 | `crash-health` API + 后台卡片 + gate 提示 | 🚧 |
| E4 | 仪器化/弱网 CI | `.github/workflows/android-ci.yml` | 🚧 |
| E5 | FCM 生产 E2E | 到期/公告模板 + 真机 | 🚧 |
| E6 | 首连 P95 发版对照 | connect-timing + 发版记录 | 🚧 |

**建议下一迭代代码顺序**：E2（真机填表至 5/20）→ E5（FCM 生产 E2E）→ E6（首连 P95 门禁）→ E1（曼谷验收挂钩）。
