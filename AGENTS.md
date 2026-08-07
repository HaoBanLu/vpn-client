# AGENTS.md（vpn-client）

客户端独立仓短索引。细则见 `.cursor/rules/` 与 `apps/*/AGENTS.md`。

## 仓库地图

| 路径 | 说明 |
|------|------|
| `apps/android` | 会员 Android（Mihomo TUN） |
| `apps/tauri` | 桌面 + iOS 壳 |
| `docs/` | 客户端文档（SSOT 功能状态：`docs/功能todo.md` 客户端条目） |

控制面仓库：`../vpn`（或组织内 `vpn` remote）— **勿**在此仓改 API / admin / node-agent。

## 执行清单

1. 只改当前任务相关端（Android / 桌面 / iOS）
2. 共享行为以 `docs/product/Tauri-Android功能对齐.md` 为准
3. Android 连接可信 / 重连防回归见 `apps/android/AGENTS.md`（`VpnReconnectSupervisor`）
4. 用户可见变更同步 `docs/功能todo.md`
5. 验证：Android `./gradlew :app:testDebugUnitTest`；Tauri `npm test`

## 安全

- 禁止提交密钥、keystore、`.env`、生产凭据
- 发版 Secrets 仅放 GitHub Secrets
