# App 真机 ROM 抽测矩阵（P1-2 / E2）

> **核心结论**：发版前每轮至少 **5 台真机通过**、季度覆盖 **20 款**；**机器可读数据源**为 [`apps/android/rom-matrix/records.json`](../../apps/android/rom-matrix/records.json)，本文档为人工阅读副本。  
> **门禁脚本**：`bash apps/android/scripts/rom-matrix-gate.sh`（严格模式 `--strict` 或 `ROM_MATRIX_STRICT=1 release-gate.sh`）

---

## 1. 当前轮次状态

| 项 | 值 |
|----|-----|
| 发版轮次 | `2026-07`（见 JSON `release_round`） |
| 本轮最低通过 | **5** 台 |
| 季度目标覆盖 | **20** 款 |
| 校验命令 | `cd apps/android && bash scripts/rom-matrix-gate.sh` |

更新测试结果时：**先改 `records.json`**，再按需同步下表。

---

## 2. 记录表（与 JSON 同步）

| 品牌 | 机型 | Android | SDK | 推荐 TUN 栈 | 回国节点 | 连接 | 断网恢复 | 假连/降级 | 测试人 | 日期 | 备注 |
|------|------|---------|-----|-------------|----------|------|----------|-----------|--------|------|------|
| Google | Pixel Emulator API 36 | 16 | 36 | gvisor | 芜湖 | ✅ | ✅ | — | dev | 2026-07-01 | CI/模拟器冒烟 |
| 小米 | 待填 | | | gvisor | | | | | | | 电池优化 |
| 华为 | 待填 | | | gvisor | | | | | | | 鸿蒙/EMUI |
| OPPO | 待填 | | | gvisor | | | | | | | |
| vivo | 待填 | | | gvisor | | | | | | | |
| Samsung | 待填 | | | gvisor | | | | | | | |
| 一加 | 待填 | | | gvisor | | | | | | | |
| 荣耀 | 待填 | | | gvisor | | | | | | | |
| realme | 待填 | | | gvisor | | | | | | | |
| 魅族 | 待填 | | | gvisor | | | | | | | |

**图例**：连接/断网恢复 — `pass` / `fail` / `pending`；JSON 中 `connect`/`reconnect` 字段

**季度 20 款扩展**：在 `records.json` 的 `records` 数组追加条目（荣耀、魅族、联想、中兴、Google Pixel 实机等）。

---

## 3. 抽测步骤（每台约 15 分钟）

1. 安装 **全量 Release APK**（勿用瘦包），登录测试账号  
2. **连接与隐私** → 确认 Kill Switch / gvisor  
3. 选回国节点连接 → 浏览器 + 可选抖音  
4. 飞行模式 10s → 关 → 观察自动重连  
5. 在 `records.json` 填写 `connect`/`reconnect`/`tested_at`/`tester`/`release_round`

---

## 4. JSON 字段说明

```json
{
  "id": "xiaomi-k60",
  "brand": "小米",
  "model": "Redmi K60",
  "android": "14",
  "sdk": 34,
  "tun_stack": "gvisor",
  "domestic_node": "芜湖",
  "connect": "pass",
  "reconnect": "pass",
  "degraded_ok": true,
  "tested_at": "2026-07-15",
  "tester": "zhangsan",
  "release_round": "2026-07",
  "notes": ""
}
```

`connect` 为 `pass` 且 `tested_at` 在 90 天内、 `release_round` 匹配当前轮次，计入发版通过数。

---

## 5. 常见问题与栈选择

| 现象 | 建议 |
|------|------|
| 显示已保护但无流量 | 改 **gvisor** 或等待自动切栈提示 |
| system 栈 TUN 不转发 | 默认 gvisor |
| 后台被杀 | 电池优化白名单 + Always-On VPN 引导 |

---

## 6. 与诊断日志联动

后台 **用户详情 → App 诊断日志** 按 `app_version`、`tun_stack` 筛选；**注册与邮箱 → 注册策略 → 崩溃率发版门禁** 核对 E3。
