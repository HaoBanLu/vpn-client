# 跨云 Tauri 跨端客户端（`apps/tauri`）

**Win / macOS / Linux / Android / iPhone** 统一在本目录。

| 平台 | 代码位置 | 技术栈 | 状态 |
|------|----------|--------|------|
| **Windows** | `src-tauri/src/vpn/` | Tauri 2 + Mihomo + 系统代理 | 主交付 |
| **macOS** | 同上 | Tauri 2 + Mihomo | 需实机验证 |
| **Linux** | 同上 | Tauri 2 + Mihomo | 有限支持 |
| **Android** | `src/` + `src-tauri/android/` | Vue + Mihomo TUN 插件 | **正式发包** |
| **iPhone** | [`platforms/ios/`](platforms/ios/) | SwiftUI + NE | VPN 🚧 |

> 旧 Compose 工程 [`apps/android`](../android/) **已存档**（仅 `mihomo-core` 仍被本目录链接）。

## 目录结构

```text
apps/tauri/
├── src/                      # 共享 Vue（桌面 + Android WebView）
├── src-tauri/                # 桌面 Rust VPN + Android overlay
│   └── android/              # Android VPN Kotlin（sync → gen/android）
├── platforms/ios/            # iPhone Swift
├── scripts/                  # 构建 / fetch-mihomo / android-*
└── 跨云客户端打包说明.md
```

## 常用命令

```bash
cd apps/tauri
npm run setup
npm run tauri:dev                      # Windows 桌面
npm run tauri:android:build:release    # Android 正式 APK
npm test
```

详细打包见 [跨云客户端打包说明.md](跨云客户端打包说明.md)。  
CI Tag 发版见 [`docs/guides/GitHub自动打包与密钥配置说明.md`](../../docs/guides/GitHub自动打包与密钥配置说明.md)。
