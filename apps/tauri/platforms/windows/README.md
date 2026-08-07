# Windows（Tauri 桌面）

## 实现要点

- **TUN**：wintun（`desktop_mode.rs`），需管理员权限时 UAC 提权
- **Kill Switch**：Windows 防火墙出站阻断（`kill_switch.rs`）
- **安装包**：NSIS（`tauri.conf.json` → `targets: ["nsis"]`）
- **Mihomo**：`src-tauri/resources/bin/windows-amd64/mihomo.exe`

## 构建

```bash
cd apps/tauri
npm run tauri:win:build   # 或 npm run tauri:build
```

## 已知限制

- 首次 TUN/Kill Switch 需以管理员运行或安装时配置
- 内置 updater 需配置 `TAURI_UPDATER_PUBKEY`（见 `npm run setup:updater`）
- 连接后健康探测在 `src-tauri/src/vpn/desktop_probe.rs`；勿用 PowerShell 直连代理测速（已废弃，易误判）
