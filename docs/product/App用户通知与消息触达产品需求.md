# App 用户通知与消息触达产品需求

> **核心结论**：任何会导致 VPN 断开或网络阻断的账户/订阅事件，必须在 **系统通知栏 + 应用内** 双通道告知原因与下一步操作；否则用户会「莫名上不了网」，体验不可接受。  
> **适用范围**：Android 会员 App（本期）；Tauri / iOS 另开任务。  
> **文档状态**：✅ P0 本地系统通知已落地；**P2-5 FCM**（Token 注册 + 服务端 Firebase 发送 + 充值到账/驳回推送）2026-07-01  
> **更新日期**：2026-06-29；**修订**：2026-07-01  
> **关联文档**：[App隐私保护与连接安全产品需求](App隐私保护与连接安全产品需求.md)、[App接口稳定性治理需求文档](App接口稳定性治理需求文档.md)、[App升级管理需求文档](App升级管理需求文档.md)、[自研App需求文档](自研App需求文档.md)

---

## 1. 背景与问题（SCQA）

### 1.1 情景（Situation）

- App 已具备会话心跳、鉴权失效处理、Kill Switch、充值订单轮询等能力。
- VPN 连接态有前台服务通知；账户类事件此前主要依赖应用内 Dialog / Toast。

### 1.2 冲突（Complication）

- 用户在其它 App 前台时，若被后台踢下线或套餐失效，VPN 断开 + Kill Switch 可能阻断网络，但 **无系统通知解释原因**。
- `SessionEvents` 原先仅在 `MainActivity` 内订阅，进程级触达不完整。
- `POST_NOTIFICATIONS` 仅在连接 VPN 时申请，账户安全类通知可能无权限展示。

### 1.3 疑问（Question）

哪些场景必须通知用户？如何通过本地系统通知 + 应用内提示，让用户始终知道「为何断网、下一步做什么」？

### 1.4 答案（Answer）

建立 **App 用户通知分级体系**，第一期落地 **Android 本地系统通知（P0）**；远程推送（FCM）放入 P2。

---

## 2. 目标与 KPI

| 指标 | 目标 |
|------|------|
| P0 场景后台触达率 | 已授权通知权限时 **100%** 发出系统通知 |
| 未授权通知权限 | 登录页冷启动 **必须** 展示上次登出/断开原因 Banner |
| 用户投诉「莫名断网」 | 较基线下降（定性：踢线/套餐失效场景有明确文案） |

---

## 3. 通知分级

| 级别 | NotificationChannel | Importance | 场景 |
|------|---------------------|------------|------|
| P0 账户安全 | `account_security` | HIGH | `SESSION_REVOKED`、`LOGIN_ON_ANOTHER_DEVICE`、Token 失效 |
| P0 订阅状态 | `account_status` | DEFAULT | `subscription_expired`、`traffic_exceeded`、心跳强制断开 |
| P1 资金订单 | `order_finance` | DEFAULT | 充值到账 `paid`、驳回 `rejected` |
| P1 产品运营 | `app_updates` | LOW | 可选 App 更新（后台补通知，前台仍用 Dialog） |
| P2 远程推送 | — | — | FCM + `POST /client/push-token`（**本期不做**） |

与 VPN 前台服务通道 `跨云 VPN`（`IMPORTANCE_LOW`）分离，避免账户安全通知被淹没。

---

## 4. 场景清单与文案

与 [App隐私保护与连接安全产品需求 §14.1](App隐私保护与连接安全产品需求.md) 对齐：

| 触发源 | app_code / reason | 通知标题 | 通知正文 | 点击跳转 |
|--------|-------------------|----------|----------|----------|
| 401 会话撤销 | `SESSION_REVOKED` | 登录状态已失效 | 请重新登录后再次连接 | 登录页 |
| 他端登录 | `LOGIN_ON_ANOTHER_DEVICE` | 账号在其他设备登录 | 本机 VPN 已断开，请重新登录 | 登录页 |
| Token 过期等 | （无 code） | 登录已过期 | 为保护隐私，已断开 VPN。请重新登录 | 登录页 |
| 心跳套餐失效 | `subscription_expired` | 套餐已到期 | VPN 已断开，请续费后重新连接 | 主界面 / 套餐 |
| 心跳流量用尽 | `traffic_exceeded` | 流量已用尽 | VPN 已断开，请升级套餐或等待重置 | 主界面 / 流量 |
| Kill Switch（鉴权触发） | — | 跨云 · 网络已阻断 | **副标题**：因登录失效，网络已暂停（等原因） | 打开 App |
| 充值到账 | `paid` | USDT 充值已到账 | 订单 {orderNo} 已确认，余额已更新 | 充值订单 |
| 充值驳回 | `rejected` | USDT 充值被驳回 | 订单 {orderNo} 请查看驳回原因 | 充值订单 |
| 可选更新 | — | 发现新版本 | {versionName} 可更新 | 关于 / 更新 |

---

## 5. 触达规则

### 5.1 进程级监听

- 在 `VpnMemberApp.onCreate` 启动 `UserNotificationCoordinator`，订阅：
  - `SessionEvents.invalidated`
  - `PrivacyForceDisconnectEvents.events`
  - `AppEvents.rechargeStatusChanged`
- 不依赖 `MainActivity` 是否在前台。

### 5.2 双通道

| 通道 | 说明 |
|------|------|
| 系统通知 | 后台必达（有权限时）；5 分钟内同 `dedupeKey` 去重 |
| 应用内 | 保留 Dialog（会话失效）、Toast（套餐断开）、Snackbar（充值） |

### 5.3 冷启动补发

- 会话失效时持久化 `LastInvalidationStore`（DataStore）。
- 登录页展示一次性 Banner，用户进入后 `consume` 清除。

### 5.4 通知权限

- 登录/注册成功后引导申请 `POST_NOTIFICATIONS`（不仅连接 VPN 时）。
- 拒绝时不阻塞登录；文案说明「开启通知可在后台接收账户与安全提醒」。

### 5.5 Kill Switch 文案

- 鉴权/套餐触发的 `disconnectForAuth()` 前写入 `AuthDisconnectReasonStore`。
- VPN 前台 Kill Switch 通知展示原因副标题，避免仅显示「网络已阻断」。

---

## 6. 技术约定（Android）

```
notification/
  UserNotificationCoordinator.kt   # 统一发通知、去重、订阅事件
  UserNotificationContent.kt       # 文案映射（可单测）
data/local/
  LastInvalidationStore.kt         # DataStore 冷启动补发
  AuthDisconnectReasonStore.kt     # Kill Switch 副标题
```

- 点击通知：`PendingIntent` → `MainActivity`，`extra` 携带 `nav_route`。
- 本期 **不做** FCM、不做应用内消息中心列表页（「我的」角标继续承载充值提醒）。

---

## 7. 验收标准

| # | 场景 | 预期 |
|---|------|------|
| 1 | 后台被踢线（`SESSION_REVOKED`） | 通知栏 P0 消息；点击进登录页 |
| 2 | VPN 连接中套餐过期 | 系统通知 + Toast；Kill Switch 含原因 |
| 3 | 冷启动 | 登录页 Banner 展示上次登出原因 |
| 4 | 充值到账（后台） | `order_finance` 通知 |
| 5 | 仪器化测试 | `SessionEvents.publish` 后 `NotificationManager` 可查到通知 |

---

## 8. 分期路线

| 阶段 | 内容 | 状态 |
|------|------|------|
| P0 | 本地系统通知 + 冷启动 Banner + Kill Switch 副标题 | 本期 |
| P1 | App 更新后台通知、连接持续失败提醒 | 待排期 |
| P2 | FCM 远程推送、`POST /client/push-token` | 待排期 |
| — | Tauri / iOS 通知 parity | 另开任务 |

---

## 9. 变更记录

| 日期 | 版本 | 说明 |
|------|------|------|
| 2026-06-29 | V1 | 初版：P0 本地通知需求与验收 |
