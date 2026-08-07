# vpn-client

跨云 **会员客户端** 独立仓库（自 `vpn` monorepo 拆出）。

| 目录 | 说明 |
|------|------|
| [`apps/android`](apps/android) | Android 正式版（Kotlin + Compose + Mihomo TUN） |
| [`apps/tauri`](apps/tauri) | 桌面 Win/Mac/Linux（Vue + Tauri）+ iPhone（`platforms/ios` Swift） |
| [`.github/workflows`](.github/workflows) | Android CI / Tauri CI / Tag 发版 |
| [`docs`](docs) | 客户端相关产品与发版文档子集 |

控制面（Go API / 管理后台 / 节点 agent）仍在 **`vpn`** 仓库。客户端只依赖线上 `/api/v1`（本地联调改各端 base URL）。

## 平台

| 端 | 状态 |
|----|------|
| Android | 主交付；现行约 `3.16.2` |
| Windows / macOS | 桌面 MVP（系统代理） |
| Linux | CI 构建；Tag 发版次要 |
| iPhone | 业务页齐；VPN 阻塞 Mihomo xcframework |
| Tauri Android overlay | **废弃**，勿发版 |

## 验证

```bash
# Android
cd apps/android && ./gradlew :app:testDebugUnitTest

# Tauri
cd apps/tauri && npm test
```

## 发版

见 [`docs/guides/客户端GitHub-Actions发版.md`](docs/guides/客户端GitHub-Actions发版.md)。Secrets 见 [`.github/RELEASE_SECRETS.example.md`](.github/RELEASE_SECRETS.example.md)。
