# AGENTS.md（vpn-client）

客户端独立仓短索引。细则见 `.cursor/rules/` 与 `apps/*/AGENTS.md`。

## 仓库地图

| 路径 | 说明 |
|------|------|
| `apps/tauri` | **跨端主工程**：桌面 + Android 发包（Vue）+ iOS |
| `apps/android` | **已存档**（[`ARCHIVE.md`](apps/android/ARCHIVE.md)）；仅 `mihomo-core` 仍被 Tauri 链接 |
| `docs/` | 客户端文档（SSOT：`docs/功能todo.md`） |

控制面仓库：`../vpn` — **勿**在此仓改 API / admin / node-agent。

## 版本线

自 **1.2 / code 120** 起统一迭代（以 `apps/tauri` `app-meta.ts` 为准）。  
包名 / Bundle：**`com.vpn.kuayun`**（详见 `apps/android/ARCHIVE.md`）。

## 执行清单

1. 只改 `apps/tauri`（及文档）；**勿再改 `apps/android` 业务**
2. 共享行为以 `docs/product/Tauri-Android功能对齐.md` 为准
3. 用户可见变更同步 `docs/功能todo.md`
4. 验证：`cd apps/tauri && npm test`

## 安全

- 禁止提交密钥、keystore、`.env`、生产凭据
- 发版 Secrets 仅放 GitHub Secrets；说明见 [`docs/guides/GitHub自动打包与密钥配置说明.md`](docs/guides/GitHub自动打包与密钥配置说明.md)
