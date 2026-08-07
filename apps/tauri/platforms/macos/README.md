# macOS（Tauri 桌面）

与 Windows **共用** `apps/tauri/src` + `src-tauri`；本目录仅作平台索引。

## 实现要点

- **代理模式（MVP）**：`networksetup` 设置 HTTP/HTTPS 系统代理（`system_proxy.rs`）
- **TUN / Kill Switch**：代码保留，UI 不作为发版门禁（与 Windows 一致）
- **安装包**：在 macOS 上 `npm run tauri:mac:build` → `.dmg`

## 构建

```bash
cd apps/tauri
npm run setup
npm run tauri:mac:dev
npm run tauri:mac:build
```

## CI Tag 发版 / 后台更新

- 操作手册：[客户端 GitHub Actions 发版](../../../../docs/guides/客户端GitHub-Actions发版.md)
- Tag `v*` → Release 附件 `kuayun-macos-*.dmg`（+ `.sig` 需配置 `TAURI_SIGNING_*`）
- 后台：`platform=macos`，版本与 `APP_VERSION_*` 一致，Updater 签名贴 `.sig` 全文

## 验收（必做）

请按 **[macOS 桌面验收清单](../../../../docs/product/macOS桌面验收清单.md)** 勾选：

1. P0 系统代理真连 E2E  
2. P1 托盘 / 中断 / 测速  
3. 签名 + Notarization 后再对外分发  

未勾选 P0 前，不要宣称 macOS 已与 Windows 同等完成。
