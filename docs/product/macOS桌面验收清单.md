# macOS 桌面验收清单（对齐 Windows）

> **目标**：在苹果电脑上把 `apps/tauri` 桌面端验到与当前 Windows 同等可用，再做签名公证发版。  
> **代码基准**：与 Windows **共用** `src/` + `src-tauri/`（系统代理走 `networksetup`）。  
> **最后更新**：2026-07-28

---

## 0. 结论口径

| 级别 | 含义 |
|------|------|
| **P0 通过** | 可给内部试用：登录 → 选节点 → 连接 → 系统代理生效 → 浏览器出网 |
| **P1 通过** | 可准备对外：托盘/重连/断开清理/更新检查无致命问题 |
| **发版通过** | P0+P1 + Developer ID 签名 + Notarization + `.dmg` 可安装 |

当前仓库：**代码齐、缺本清单勾选证据** → 未勾选前不要宣称「macOS 已完成」。

---

## 1. 环境准备（一次性）

在 **macOS 真机**（Intel 或 Apple Silicon）：

```bash
cd apps/tauri
npm run setup                 # 含 fetch:mihomo → darwin-amd64 / darwin-arm64
npm run preflight:desktop
npm run tauri:mac:dev
```

核对：

- [ ] `src-tauri/resources/bin/darwin-*/mihomo` 存在且可执行
- [ ] 窗口能打开（Splash → 主窗）
- [ ] 侧栏显示「跨云 v{version}」

---

## 2. P0 — 系统代理真连 E2E（发版门禁）

| # | 步骤 | 期望 | 结果 |
|---|------|------|------|
| 1 | 登录有效会员账号 | 进入连接页 | ☐ |
| 2 | 节点页选一在线节点（如新加坡） | 跳转连接页并进入「连接中」波纹 | ☐ |
| 3 | 等待「已保护」 | Hero 绿态；会话卡出现 | ☐ |
| 4 | 终端执行 `networksetup -getwebproxy Wi-Fi`（网卡名按实际改） | `Enabled: Yes`，Server=`127.0.0.1`，Port=mixed-port | ☐ |
| 5 | 浏览器访问 `https://api.ip.sb/ip` 或 ipinfo | 出口 IP 为节点侧，非本机宽带 | ☐ |
| 6 | 点「断开」 | 回到未连接；`Enabled: No` 或代理已清 | ☐ |
| 7 | 异常杀进程 / 强退 App 后再查代理 | 不应残留系统代理（或托盘「断开」可清） | ☐ |

**失败时抓：**

```bash
# 应用日志 / 控制台
log stream --predicate 'subsystem contains "vpn" OR process == "跨云"' --level debug

# 代理状态
networksetup -listallnetworkservices
networksetup -getwebproxy "Wi-Fi"
networksetup -getsecurewebproxy "Wi-Fi"
```

---

## 3. P1 — 体验与壳能力

| # | 项 | 期望 | 结果 |
|---|-----|------|------|
| 1 | 连接中再点按钮 | 可中断，回未连接 | ☐ |
| 2 | 连接中切节点 | 中断并连新节点 | ☐ |
| 3 | 托盘显示/断开/退出 | 菜单可用；关窗可藏托盘 | ☐ |
| 4 | 节点批量测速 | 延迟数字更新 | ☐ |
| 5 | 套餐/充值/工单只读冒烟 | 页面可开、API 无 401 死循环 | ☐ |
| 6 | 关于页版本号 | 与 `tauri.conf.json` / 侧栏一致 | ☐ |
| 7 | 最大化按钮 | 禁用（`maximizable(false)`） | ☐ |
| 8 | 启动恢复会话（若设置开启） | 行为符合「连接与隐私」文案 | ☐ |

---

## 4. 打包与公证（发版）

```bash
cd apps/tauri
npm run tauri:mac:build
# 产物一般在 src-tauri/target/release/bundle/dmg/
```

| # | 项 | 结果 |
|---|-----|------|
| 1 | 本地未签名 `.dmg` 可安装打开 | ☐ |
| 2 | Developer ID Application 签名 | ☐ |
| 3 | `spctl --assess --type execute` 通过 | ☐ |
| 4 | `xcrun notarytool submit` + staple | ☐ |
| 5 | 干净 Mac（或另一用户）打开无 Gatekeeper 阻拦 | ☐ |
| 6 | 后台 `platform=macos` 上传安装包 + updater 签名 | ☐ |
| 7 | 旧版 → 新版 updater 路径抽测 | ☐ |

密钥与 updater：见 [`跨云客户端打包说明.md`](../../apps/tauri/跨云客户端打包说明.md) §14；`npm run setup:updater`。

---

## 5. 与 Windows 刻意差异（勿当缺口）

- 数据面均为 **系统代理 MVP**（非 TUN UI）
- Kill Switch：Mac 为 `pfctl` 实现，**UI 不暴露**，非本清单门禁
- 探测命令：Mac 用系统 `curl -x`，Windows 用专项脚本

---

## 6. 验收记录模板

```text
日期：
机器：MacBook / macOS 版本 / 芯片：
构建：tauri:mac:dev 或 dmg 版本号：
账号/节点：
P0：通过 / 失败（现象）：
P1：通过 / 失败：
公证：未做 / 通过：
记录人：
```

勾选完成后：把摘要回写 [`docs/功能todo.md`](../功能todo.md)（「macOS 系统代理 E2E」→ ✅）并更新 [`Tauri-Android功能对齐.md`](Tauri-Android功能对齐.md) §6。
