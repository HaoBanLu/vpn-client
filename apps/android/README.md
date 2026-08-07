# 跨云 Android App

Kotlin + Jetpack Compose 自研客户端，对接控制面 `/api/v1`。

## 开发环境

- Android Studio Ladybug 或更高
- JDK 17
- Android SDK 34

## 接口 API

Debug / Release 构建默认使用同一个线上接口：

```text
https://vpn.eodkko.xyz/api/v1/
```

如需切换测试环境：

- **连本机 Docker API（真机/模拟器 Debug）**：见 [本地API调试联调.md](本地API调试联调.md)
- **改线上域名**：编辑 `app/build.gradle.kts` 顶部 `releaseAppBaseUrl`

## 功能范围（MVP）

- 注册 / 登录 / 自动登录（EncryptedSharedPreferences 存 JWT；可选记住账号密码）
- 套餐列表、余额下单支付、订单状态轮询
- 订阅状态、流量、客户端市场与节点选择
- `GET /client/config` 拉取 **Clash YAML**（Mihomo 内核，支持 `profile`、`market`、`node` 参数）
- `VpnService` + **Mihomo** 真实 TUN 代理
- 连接后分层探测：基础联网 + 海外可达，避免误报节点故障
- 前台通知：当前节点、实时 ↑↓ 速率（KB/s）、连接时长、快捷断开
- **App 调试**（后台对用户开启 `app_debug_enabled`）：「我的 → 诊断日志」本地查看 + 上报

## UI 设计

- App 名称：**跨云**
- 深色科技风主题（`ui/theme/KuayunTheme.kt`）
- 共享组件：品牌头、状态徽章、信息卡片（`ui/components/KuayunComponents.kt`）
- 主路径页面：连接 / 节点 / 套餐 / 我的

## Mihomo 内核

- 模块：`mihomo-core`（Clash Meta Android bridge + native `libbridge.so` / `libclash.so`）
- 集成代码：`app/src/main/java/com/vpn/member/vpn/mihomo/`
- 后端 `GET /client/config` 返回 Clash YAML，与 `/subscription/clash` **同源生成**
- 首次构建需执行：`bash scripts/setup-mihomo-native.sh`（从 CMFA Release 提取 native 库）

## 支持的连接协议

App 通过 **Mihomo + TUN** 建立隧道，支持 VLESS/VMess/Trojan/SS/Hy2 等 Mihomo 可承载协议。节点是否可连由 `AppProtocolSupport.kt` 统一判断。

## 连接流程

1. 用户点击连接 → `ConnectViewModel` 拉取 `/client/config`
2. `VpnTunnelService`：`Clash.load(configDir)` → `Clash.startTun`（`configDir/config.yaml`）
3. 指定节点时 `Clash.patchSelector("GLOBAL", nodeName)`

帮助页可生成 **Clash 订阅链接**（`/subscription/clash`），供第三方 Clash/Mihomo 客户端导入。

## APK 体积

`mihomo-core` 按 ABI 拆分打包（`armeabi-v7a` / `arm64-v8a` / `x86_64`），单 ABI 约 50MB native 库。
