# 跨云 Android 打包（存档）

> **`apps/android` 已存档，勿再发版。** 详见 [`ARCHIVE.md`](ARCHIVE.md)。

**现行包名 / 发包工程**：

| 项 | 值 |
|----|-----|
| 包名 `applicationId` | **`com.vpn.kuayun`** |
| 工程 | [`apps/tauri`](../tauri/) |
| 本机构建 | `cd apps/tauri && npm run tauri:android:build:release` |
| Tag CI | `.github/workflows/app-release.yml` |
| 手册 | [`跨云客户端打包说明.md`](../tauri/跨云客户端打包说明.md) §8 |

历史 Compose 包名为 `com.vpn.member`，仅作对照，不再维护。
