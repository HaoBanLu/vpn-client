# App Android 发版检查清单

> **用途**：Release APK 上线前必跑项（对应 [App成熟化产品路线](../product/App成熟化产品路线.md) P1-7）。  
> **自动化**：`apps/android/scripts/release-gate.sh`（单元测试 + 可选仪器化 + Release 构建）

---

## 1. 发布前结论

- [ ] **versionCode / versionName** 已递增（`app/build.gradle.kts`）
- [ ] **Release 签名** 有效（`scripts/verify-release-signature.sh`）
- [ ] **后端已部署** 且 `migrations/init.sql` 已执行（含 `device_meta` 等列）
- [ ] **旧版 App 兼容**：无 `device_meta` 上报仍 HTTP 200

---

## 2. 自动化门禁（本地/CI）

```bash
cd apps/android
# 模拟器或真机已 adb connect 时跑全量；否则仅单元测试 + Release 构建
bash scripts/release-gate.sh
# 曼谷验收（需 SSH，E1）：
# BANGKOK_ACCEPTANCE=1 bash scripts/release-gate.sh
```

| 步骤 | 命令 | 通过标准 |
|------|------|----------|
| 单元测试 | `:app:testDebugUnitTest` | 0 failed |
| 仪器化冒烟 | `ConnectDataplaneInstrumentedTest` + `AuthStabilitySmokeTest` | 需 adb device |
| 断网脚本 | `vpn-stability-adb-check.sh` | 无 crash；logcat 有 reconnect/heal |
| Release 构建 | `:app:assembleRelease -PreleaseArm64Only=true` | 全量 arm64 APK ~49MB（**默认，不用瘦包**） |
| 瘦包 Release（特殊渠道可选） | `-PslimNativeLibs=true` | ~2.5MB；首连需联网下载 native，**非默认** |

**Tag CI 发版**（[App Release workflow](../../.github/workflows/app-release.yml)）：构建前须 `bash scripts/setup-mihomo-native.sh`（workflow 已内置）；产物 **~49MB** 且含 `libclash.so`/`libbridge.so`。勿分发 **v3.14.4** 及更早未拉 native 的空壳包（~2.5MB）。

---

## 3. 手工验收（真机 ≥3 台，含 1 台国产 ROM）

| # | 场景 | 期望 |
|---|------|------|
| 1 | 登录 → 一键连接 | ≤30s 内连接成功或明确失败原因 |
| 2 | 连接后打开浏览器 | 可上网；连接页非长期「保护降级」 |
| 3 | 飞行模式 10s → 关闭 | 10s 内自动重连或提示重连中 |
| 4 | 另一设备登录踢线 | Kill Switch + 系统通知 |
| 5 | 鉴权断开（改密/登出） | 网络阻断 + 通知 |
| 6 | 回国场景（曼谷/海外） | 连芜湖/上海；抖音或国内站可开 |
| 7 | 连接与隐私 → 泄露自检 | 已连接时通过；历史记录可见 |

ROM 抽测：更新 [`rom-matrix/records.json`](../../apps/android/rom-matrix/records.json) 并运行 `bash apps/android/scripts/rom-matrix-gate.sh`。详见 [App真机ROM抽测矩阵](../product/App真机ROM抽测矩阵.md)。

---

## 4. 曼谷运维验收（有 SSH 时）

```bash
python ssh_debug/test_bangkok_cn_nodes_domestic_return.py
# KPI：≥12/15 节点国内站可达
```

---

## 5. 上线后 7 日观察（P1-3）

| 指标 | 目标 | 查看位置 |
|------|------|----------|
| 崩溃率 | ≤0.5% | **设置 → 注册与邮箱 → 注册策略 → 崩溃率发版门禁**；或 `GET /admin/users/app-debug/crash-health` |
| error 级日志 | 无异常尖峰 | 同上，按 `app_version` / `tun_stack` 筛选 |
| 连接投诉 | 下降 | 客服 + `post_connect_verify` / `tun_tcp_log` |

---

## 6. 产出物

- [ ] `app-arm64-v8a-release.apk`（**默认全量**，勿用 `-PslimNativeLibs=true`）
  - 体积分析：`bash scripts/apk-size-report.sh`
- [ ] 企业级核对：[App企业级差距清单](../product/App企业级差距清单.md) — **E3 崩溃率**、**E2 ROM 矩阵**（`rom-matrix-gate.sh`）、E1 按需
- [ ] 后台 **App 版本管理** 上传并发布
- [ ] 更新 `docs/功能todo.md` 发版日期与版本号
