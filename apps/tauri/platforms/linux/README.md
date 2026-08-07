# Linux（Tauri 桌面）

## 实现要点

- **系统代理**：优先 GNOME `gsettings`（`system_proxy.rs`）；不支持的桌面环境会**明确报错**，不再假连接成功
- **TUN**：Mihomo tun 栈（与 Win/Mac 共用配置注入逻辑）
- **Kill Switch**：**已实现**（`iptables` 自定义链 `KuayunVPN_KS`；需 root/CAP_NET_ADMIN）

## 构建

```bash
cd apps/tauri
npm run tauri:linux:dev
npm run tauri:linux:build        # .deb
npm run tauri:linux:build:appimage
```

CI 见 `.github/workflows/tauri-ci.yml` → `tauri-linux-build`（ubuntu-22.04）。

## 验证建议

- Ubuntu 22.04+ GNOME
- Kill Switch：`iptables -L KuayunVPN_KS -n` 断线后应有 DROP 规则
- 其他 DE（KDE 等）需补充代理后端或文档标注「仅 TUN 模式」
