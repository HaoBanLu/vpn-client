# 连接可信：系统 VPN 硬门禁产品需求

> **结论**：全流量下「已保护」必须以**系统 VPN Network 真实出网**成功为准（商业轻门禁 B）；禁止仅凭 mixed-port / TUN 字节增长放行。  
> **范围**：仅 Android（`apps/android`）。  
> **状态**：已完成（代码 + 单测落地）；切网/断网恢复真通判定见 §7.1（2026-08-01）  
> **最后更新**：2026-08-01  
> **关联**：[App成熟化产品路线](App成熟化产品路线.md) P0-11 · [App隐私保护与连接安全](App隐私保护与连接安全产品需求.md) §数据面

---

## 1. 项目目标 / 商业价值

会员 App 宣称「已保护」时，浏览器等其它应用须真实可上网，避免反复出现「已连接、下载 0、网页打不开」的假连，降低客诉与对节点误判。

**KPI**

| 指标 | 目标 |
|------|------|
| 全流量假连（已保护且 `vpn_network_ok=false`） | 目标 **0**（诊断日志不应再出现该组合且仍保持已连接） |
| 节点正常时成功连接 | 系统路径通即可显示已保护 |
| 首连耗时 | 不承诺快于 Clash；相对现状可接受多 1～数秒真实出网确认 |

---

## 2. 情景与冲突（SCQA）

- **情景**：TUN / Mihomo 已起，节点后台健康，控制面 Mihomo 实测可通。  
- **冲突**：App 进程旁路 TUN（mixed-port）探测通，系统 VPN 不通，仍显示已保护。  
- **疑问**：如何让「已保护」与用户体感一致？  
- **答案**：对标商业 VPN——系统路径真实出网成功才算已保护（方案 B）；不做 Clash 式零门禁。

---

## 3. 成功标准（按场景）

| 场景 | 系统 VPN 探测目标 | 通过条件 | 失败行为 |
|------|-------------------|----------|----------|
| 出海全流量（如连新加坡） | 海外 204（gstatic / cloudflare） | **`vpn_network_ok=true`** | `dataplane inactive` → 立即断开并提示换节点 |
| 回国全流量（海外→国内落地） | **国内**站（如 qq.com / 国内可达 204） | **`vpn_network_ok=true`**（探测 URL 须国内） | 同上 |
| 分流 split | 维持相对放宽 | 国内或海外或 TUN 活跃之一即可 | 可放宽通过并记 warn |

**明确禁止（全流量）**

- 仅 `overseas_mixed` / `domestic_mixed` 通而 `vpn_network_ok=false` 仍显示已保护  
- 仅 `tun_download_grew` / `traffic_grew` / `tun_tcp_log` 在无系统 VPN 成功时放行  

mixed-port、TUN 计数可保留诊断，**不得单独作为全流量成功条件**。

---

## 4. 地域（中国 / 缅甸）

- **同一业务场景、同一套成功标准**；不按国家白名单区分能否连接。  
- 仅保留 [`PostConnectVerifyPolicy`](../../apps/android/app/src/main/java/com/vpn/member/vpn/PostConnectVerifyPolicy.kt)：海外时区 + 回国时 **settle/重试更长**（防 Reality 慢握手误杀），**不改变「什么叫成功」**。  
- 禁止再用 gstatic 作为回国场景唯一系统探测目标（会误杀缅甸回国）。

---

## 5. 非目标

- 不追求 Clash Verge 式「TUN 起即已连接」速度  
- 不默认打开自动同区 failover  
- 本迭代不做完整「隧道已建立 / 网络就绪」两阶段 Hero UI（可列后续体验项）  
- 不改节点侧过滤逻辑（节点健康 ≠ 手机数据面）

---

## 6. 验收

| # | 场景 | 期望 |
|---|------|------|
| 1 | 出海：mixed 通 + `vpn_network_ok=false` | **不得**保持已保护；应断开 |
| 2 | 出海：节点通且系统 VPN 通 | 显示已保护，浏览器可开网页 |
| 3 | 缅甸回国：系统 VPN 探国内站通 | 可已保护；不得因 gstatic 失败单独判死 |
| 4 | 中国用户连新加坡 vs 缅甸用户连新加坡 | 成功标准相同 |
| 6 | WiFi→蜂窝或断网再连：mixed 通 + `vpn_network_ok=false` | **必须**自动重连；不得假装已恢复 |
| 7 | 切网自愈 | 须重绑 `setUnderlyingNetworks`；见 §7.1 |

---

## 7. 实现要点

- `TunDataPlaneVerifier.probeViaVpnNetwork(domesticReturn)`：按场景选 URL；亦接受 VPN Network `NET_CAPABILITY_VALIDATED`
- `evaluateDataplanePass`：全流量（出海/回国）强制 `vpnNetworkOk`
- **跨云自身不得强制 `addDisallowedApplication`**：否则探测与浏览器不同路，换 TUN 栈（gvisor/mixed/system）无效
- 失败文案沿用现有 dataplane inactive 断开路径  
- 防回归：`TunDataPlaneVerifierLogicTest` + `apps/android/AGENTS.md`

### 7.1 切网 / 断网恢复

**结论（3.15.7）**：自动重连开启时，WiFi↔蜂窝 / 断网再连 → **直接完整重连**（防抖），不再「先 HEAL 再赌探测」。  
防回归：`NetworkRestorePolicy.decide(autoReconnect=true)` 不得返回 HEAL；单测 `autoReconnectEnabled_neverReturnsHeal`。  
若仍走轻量探测（仅自动重连关闭），切网后 `probeVpnNetworkOk` **禁止仅认 VALIDATED**，须 HTTP。

| 必须 | 禁止（线上已踩坑） |
|------|-------------------|
| 切网/网恢复 → `scheduleAutoReconnect` | 仅 HEAL + mixed/探测 OK 当恢复 |
| `onLost` 也要通知 transport | 只等 VALIDATED 从无到有 |
| 没物理网不空转重连 | 离线连扣重连次数 |

案例：2026-08-01 `luban7733` 多次 HEAL 假恢复。实现见 `NetworkRestorePolicy` / `3.15.7`。
