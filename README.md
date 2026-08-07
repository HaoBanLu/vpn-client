# vpn-client

跨云 **会员客户端** 独立仓库（自 `vpn` monorepo 拆出）。

| 目录 | 说明 |
|------|------|
| [`apps/tauri`](apps/tauri) | **跨端主工程**：Win/Mac/Linux + **Android 发包**（Vue）+ iPhone |
| [`apps/android`](apps/android) | **已存档**（不再维护）；仅保留 `mihomo-core` 供 Tauri Android 链接 |
| [`.github/workflows`](.github/workflows) | Tauri CI / Tag 发版（`app-release.yml`） |
| [`docs`](docs) | 客户端相关产品与发版文档子集 |

控制面（Go API / 管理后台 / 节点 agent）仍在 **`vpn`** 仓库。客户端只依赖线上 `/api/v1`。

## 平台

| 端 | 状态 | 版本线 |
|----|------|--------|
| Windows / macOS / Linux | 桌面 MVP（系统代理） | **1.2** / code **120** |
| Android | **`apps/tauri` 发包**（包名 `com.vpn.kuayun`） | 同上 |
| iPhone | 业务页齐；VPN 阻塞 xcframework | **1.2** / build **120** |

存档说明：[`apps/android/ARCHIVE.md`](apps/android/ARCHIVE.md)。  
对齐：[`docs/product/Tauri-Android功能对齐.md`](docs/product/Tauri-Android功能对齐.md)。

## 验证

```bash
cd apps/tauri && npm test
```

## 发版

见 [`docs/guides/GitHub自动打包与密钥配置说明.md`](docs/guides/GitHub自动打包与密钥配置说明.md)。

```bash
cd apps/tauri && npm run tauri:android:build:release   # 本机 Android
# Tag v* → GitHub Actions 打 Android + Win + Mac（+ 可选 iOS）
```
