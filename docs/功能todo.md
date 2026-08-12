# 功能清单（与代码对齐）

> **单一事实来源**：本文件记录功能实现状态，与路由、页面、节点协议、订阅类型保持一致。  
> 状态：`✅ 已完成` · `🚧 开发中/需联调` · `📋 待开发` · `❌ 未集成`

最后核对：**2026-08-12**（版本线 `1.2.19` / code 以 `app-meta` 为准）

| 功能 / 变更 | 状态 | 日期 | 备注 |
|-------------|------|------|------|
| 发版 1.2.19 / code 139 | ✅ | 2026-08-12 | 分应用直连、连接与隐私 Always-on/开机自连/省电；Android 品牌图标 sync 进 gen；Toast HUD/错误文案等体验 |
| Tauri Android 连接与隐私：Always-on 引导 + 开机自连 + 省电 | ✅ | 2026-08-12 | 产品取舍不做自研 KS；`getStabilityStatus`/深链/`VpnBootReceiver`；文案去掉「防泄露默认」误解 |
| Tauri Android 分应用直连完整页 | ✅ | 2026-08-12 | 插件列包 + TUN disallow；替代占位「即将开放」 |
| 发版 1.2.16 / code 136 | ✅ | 2026-08-09 | Android 连接页会话流量回传 + 时长不重置；官方 SVG Logo；UI/充值对齐；iOS 流量页字段；Tag `v1.2.16` |
| Android 连接页会话流量回传 | ✅ | 2026-08-09 | `VpnPlugin.getStats` → `vpn_stats`；`update_status` 仅进入 Connected 记起点 |
| 发版 1.2.15 / code 135 | ✅ | 2026-08-08 | 主 Tab 品牌头/套餐标题副标题/我的账户条对齐 Compose；Tag `v1.2.15` |
| 发版 1.2.14 / code 134 | ✅ | 2026-08-08 | 修复 CI：Cargo.toml/package.json description 编码损坏 + 导出 probeLatencyMs；Tag `v1.2.14` |
| 发版 1.2.13 / code 133 | ✅ | 2026-08-08 | 界面对齐 Compose（我的/页头/稳定性/节点测速展示）；Tag `v1.2.13`（CI 因 TOML/JSON 损坏失败，见 1.2.14） |
| Tauri Android 界面对齐 Compose（我的/页头/稳定性） | ✅ | 2026-08-08 | Profile 账户条+菜单分组；移动端 Tab 品牌头；稳定性页 Android 文案/隐藏托盘；使用场景选择；规则直连/帮助 Android 全员可见；分应用入口占位 |
| 发版 1.2.12 / code 132 | ✅ | 2026-08-08 | 热修 Android 全屏/字对比度/连接竞态；Tag `v1.2.12` |
| 修 Android 全屏贴边+字看不清+连接竞态 | ✅ | 2026-08-08 | 关 `fullscreen`；safe-area；提亮/加大次要字；`waitForVpnReady` 等 Android TUN 就绪 |
| 发版 1.2.11 / code 131 | ✅ | 2026-08-08 | 含安卓卡启动修复、Logo 统一、PC 黑窗、桌面去独立 Splash；Tag `v1.2.11` |
| 桌面去掉独立 Splash（隐藏主窗就绪再 show） | ✅ | 2026-08-08 | `tauri.conf` 单 main `visible:false`；删 `splash.html`；`boot_reveal_main` 仅 show |
| 修 Android 卡 splash + Logo 不一致 + PC 连接黑窗 | ✅ | 2026-08-08 | `tauri.android.conf.json` 直进 `index.html`；品牌图覆盖 Android mipmap；`kill_switch` netsh 加 `CREATE_NO_WINDOW`；桌面独立 splash 已于同日改为隐藏主窗方案 |
| 发版铁律/踩坑写入打包手册 | ✅ | 2026-08-08 | [`GitHub自动打包与密钥配置说明.md`](guides/GitHub自动打包与密钥配置说明.md) §0/§9；Android 检查清单同步 |
| 修复 Android CI 找不到 universal APK | ✅ | 2026-08-07 | 构建已成功产出 `app-universal-release-unsigned.apk`；收集脚本兼容 universal/arm64；发版 `v1.2.8` |
| 修复 Android Manifest minSdk 冲突 | ✅ | 2026-08-07 | Tauri 默认 24 < mihomo-core 26；`bundle.android.minSdkVersion=26`；发版 `v1.2.7` |
| 修复 Android serialization 插件未声明 | ✅ | 2026-08-07 | sync 向 gen `build.gradle.kts`/`settings.gradle` 注入 kotlin serialization；发版 `v1.2.6` |
| 修复 Android `:mihomo-core` 未 include | ✅ | 2026-08-07 | Tauri 生成 `settings.gradle`（非 .kts）；sync 两边都 patch；发版 `v1.2.5` |
| 修复 Linux isEmpty + Android 桌面 API 门控 | ✅ | 2026-08-07 | `kill_switch` 用 `is_empty`；tray/updater/boot 仅 desktop；发版 `v1.2.4` |
| 修复 v1.2.2 CI：tauri-rust/桌面 smoke/Android 签名 | ✅ | 2026-08-07 | rust 补 WebKit；CI `prepare-tauri-release-build`；Android 入库 keystore+密码 Secrets；发版 `v1.2.3` |
| 修复 Tauri version 非 semver 导致 CI 全挂 | ✅ | 2026-08-07 | `tauri.conf.json` 写 `1.2` 非法；须 `1.2.2`；本地 `cargo check` + `npm test` 已绿 |
| 迁入 frontend/shared 修复 CI | ✅ | 2026-08-07 | 拆仓后缺失 `@shared/theme/tokens` |
| 包名统一 com.vpn.kuayun | ✅ | 2026-08-07 | Tauri `identifier` + Android overlay `com.vpn.kuayun.vpn`；iOS `com.vpn.kuayun.app/.tunnel` |
| 客户端文档瘦身 | ✅ | 2026-08-07 | 删发版跳转 stub；`文档目录`/`开发指南` 仅保留客户端；存档 Android 长文档改为指针 |
| Android 发包迁至 apps/tauri；apps/android 存档 | ✅ | 2026-08-07 | Tag CI 打 Tauri Android；`android-ci` 停用；[`ARCHIVE.md`](../apps/android/ARCHIVE.md) |
| GitHub 发版文档中文命名 | ✅ | 2026-08-07 | 主手册 [`GitHub自动打包与密钥配置说明.md`](guides/GitHub自动打包与密钥配置说明.md)；密钥 [`GitHub发版密钥说明.md`](../.github/GitHub发版密钥说明.md)；旧英文名留跳转 |
| 统一客户端版本线 1.2 | ✅ | 2026-08-07 | 展示名 `1.2`、code `120`：以 `apps/tauri` `APP_VERSION_*` 为准 |
| Tauri 功能梳理 + Android 正式发包路径 | ✅ | 2026-08-07 | [`Tauri-Android功能对齐.md`](product/Tauri-Android功能对齐.md) §0；`tauri:android:build:release` |
| Android「帮助中心」入口补齐 | ✅ | 2026-08-07 | 存档前已合入 Compose；现行入口在 Tauri Vue |
| Tauri 诊断日志入口放开 Android | ✅ | 2026-08-07 | `ProfileView` 与 `app_debug_enabled` 对齐 |
| 客户端仓拆分 vpn-client | ✅ | 2026-08-07 | 本仓为客户端正式工程；控制面仍在 `vpn` |
| Android 3.16.2 掉线可恢复（监督器+看门狗） | ✅ 存档 | 2026-08-07 | 逻辑在存档 `apps/android`；Tauri Android 应对齐插件策略 |
| Android 3.16.1 重连先备配置再 KS | ✅ | 2026-08-01 | `3.16.1`/`54`：先拉配置/缓存再 KS；失败最多 3 次；FAILED 仍可被切网再调度 |
| 数据库备份大文件下载/上传 | ✅ 代码 | 2026-08-04 | 详情下载进度条；axios `timeout:0`+`onDownloadProgress`；上传进度；API Read/WriteTimeout 默认 3600s（原硬编码 10s 导致大文件断流）；**待部署 api+admin** |
| 管理后台总览运营信息架构 | ✅ 代码 | 2026-08-04 | 移出探测四卡→监控页；KPI 改为会员/订阅/收入/节点在线；到期待办可点；自动刷新可配（同节点列表）；PRD [`管理后台总览运营信息架构.md`](product/管理后台总览运营信息架构.md)；**待部署 admin** |
| 管理端会员列表筛选增强 | ✅ 代码 | 2026-08-04 | 活跃(App/VPN/离线)、账号状态、套餐、App 调试、今日新增；统计卡可点；服务端分页前过滤；PRD [`管理端会员列表筛选与批量产品需求.md`](product/管理端会员列表筛选与批量产品需求.md)；**待部署 api+admin** |
| 管理端会员列表批量操作 | ✅ 代码 | 2026-08-04 | 多选；启用/禁用(连带踢线)、App 调试、踢下线、硬删(≤50)；`batch-update`/`batch-revoke-sessions`/`batch-delete`；同上 PRD；**待部署 api+admin** |
| 管理端节点列表刷新间隔可配 | ✅ 代码 | 2026-08-04 | 列表默认 30s、监控默认 60s；可选关闭/10/15/30/60/120；`localStorage` 持久化；文案显示当前间隔 |
| 管理端时间展示统一 | ✅ 代码 | 2026-08-04 | 统一 `YYYY-MM-DD HH:mm:ss`（年-月-日 时:分:秒）；`DATETIME_DISPLAY_FORMAT`；链路灯 `checked_at` 走 formatDateTime |
| 节点连接数按客户端去重 | ✅ 代码 | 2026-08-04 | Clash 会话数误显示为「连接 33」；改为入站用户/源 IP 去重；UI 改标「客户端」；**待滚 node-agent + admin** |
| 限速 class/filter 高并发资源池 | ✅ 代码 | 2026-08-04 | 弃用哈希 700 槽；顺序分配+回收（class≤~3万、prio≤6万）；IP 过期/用户下线删 filter；无活跃 IP 释放 class；`FindActiveVPNUsers` 同步上限 5000；**待滚 node-agent + api** |
| HTB fq_codel / 过小 pfifo 误伤 | ✅ 热更 HK54 | 2026-08-04 | 自测：无叶/UDP≈1106kbps；pfifo40 10s 丢>100万；改为**不挂叶**；410kbps 根因是 pfifo40 丢包而非 1M 配错 |
| 经中转会员带宽限速（入口 tc） | ✅ 代码 | 2026-08-04 | PRD [`中转与直连带宽限速产品需求.md`](product/中转与直连带宽限速产品需求.md)；入口 `vpn_bandwidth` 注入；落地不做 per-user；**待部署香港入口 + 经港测速 ≤1.1M 验收** |
| 管理端节点列表易用优化 | ✅ 代码 | 2026-08-04 | 运行状态改「心跳 Ns 前」；负载/订阅/绑定合并为「订阅与用户」并去掉瞬时 Mbps；直连出口补 `relay_health.ports`；**待部署 admin+api** |
| 落地 WG 回传 TCP 被 UFW 误杀 | ✅ 热修+代码+文档 | 2026-08-04 | 握手/ping 通但 `dial 10.66.0.x:port` 超时；SG3 已放行 wg0 INPUT；PostUp/agent 守卫；**防回归专文** [`docs/guides/WG经中转已知事故与防回归.md`](guides/WG经中转已知事故与防回归.md) A4；**待滚 api+agent** |
| 管理端节点列表「放行端口」列 | ✅ 代码 | 2026-08-04 | `relay_health.ports`：TCP=控制面探测通/不通，UDP=WG 握手推断；去掉写死黄字；**待部署 admin+api** |
| 管理端节点诊断超时/502 | ✅ 代码 | 2026-08-04 | 前端 diagnose 超时 30s→120s；入口 SSH 复用+8s 拨号；端口提示写明 UFW/安全组；**待部署 admin+api** |
| node-agent 限速 IP 映射（两行日志） | ✅ | 2026-08-03 | sing-box `from IP` 与 `[uN] to` 分两行，用 connection id 关联；tc filter 每 IP 只挂一次防膨胀；**SG 3/4/37/39/40/41 已热更**（验证：node4 出现 `5Mbit` class + `overlimits`）；新加坡5(id=42) SSH 仍失败 |
| 节点部署固定 NET_ADMIN（带宽 tc） | ✅ | 2026-08-02 | `docker_runtime_cap_args` 全协议加 `--cap-add=NET_ADMIN`；API 已部署；SG 3/4/37/39/40/41 已重建且 `HTB=1`；**新加坡5(id=42) SSH 密码失败待修** |
| 管理端节点列表慢/偶发 502 | ✅ 代码 | 2026-08-02 | 根因：`BuildRelayHealth` 列表路径同步 `tcpProbe`（5s×N）；改为 stale-while-revalidate + 冷启动 pending；**待部署 vpn-api** |
| node-agent 流量少记/限速不准 | ✅ 代码 | 2026-08-02 | Governor 不再 `QueryStats(reset)`；流量上报独占 reset；tc 补 `dst`；PRD §5.1；与上行「两行日志」一并滚动后生效 |
| Android 3.16.1 重连先备配置再 KS | ✅ | 2026-08-01 | `3.16.1`/`54`：先拉配置/缓存再 KS；失败最多 3 次；FAILED 仍可被切网再调度；模拟器飞行模式恢复已保护 PASS |
| Android 3.16 断网完整重连可靠性 | ✅ | 2026-08-01 | `3.16`/`53`：准备重连后进入执行态禁止被后续 dns 事件 cancel；`network_restored` 重置次数；首轮零退避；次数耗尽/无节点打日志 |
| Android 3.15.7 切网直接完整重连 | ✅ | 2026-08-01 | 发版 `3.15.7`/`52`：自动重连开 → 一律完整重连；**3.16 修执行被掐断** |
| Tauri 1.5.1 断网网卡恢复 | ✅ | 2026-08-01 | `vpn_heal`+探测路径；**已被 1.5.2 默认改完整重连覆盖** |
| Android 3.15.6 切网断网真通恢复 | ✅ | 2026-08-01 | 自愈重绑 `setUnderlyingNetworks`；切网/断网恢复后以 `vpn_network_ok` 为准（mixed SLOW 不再误判已恢复）；WiFi↔蜂窝与断网再连共用同一路径；`3.15.6`/`51`；**已被 3.15.7 默认改完整重连覆盖** |
| 管理后台列表首屏加载态 | ✅ | 2026-08-01 | ConfigProvider 接 zh_CN；40+ 列表页 `loading` 初值 true；工单表补 `:loading`；加载中隐藏空态，避免闪 No data |
| 管理后台中转分组头补齐状态列 | ✅ | 2026-08-01 | 中转组头展示接入路径/运行状态/负载/订阅（与出口行同源）；直连组头与无详情合成头仍占位 |
| Android 3.15.5 没网不拆隧道 | ✅ | 2026-08-01 | 物理网不可用时探活/切网不升级拆隧道重连；有网后再自愈/重连；有网仍失败才提示 |
| Android 3.15.4 切网自动重连 | ✅ | 2026-07-31 | `3.15.4`/`49`：切网自愈后短探测失败→自动重连；接通重连期 Kill Switch；周期探活连续失败亦重连 |
| Android 3.15.3 发版 | ✅ | 2026-07-31 | `3.15.3`/`48`：含硬门禁对齐 + 连接/节点/套餐/关于页 UI 收紧与产品介绍 |
| Android 连接页已连接态一屏收紧 | ✅ | 2026-07-31 | Hero 环缩小、已连接隐藏重复节点副标题；详情卡单行线路+速率时长合并；屏内间距收紧 |
| Android 节点列表紧凑化 | ✅ | 2026-07-31 | 白底列表托 + 标签徽章；行尾紧凑「连接/切换」按钮（非通栏）；去掉整行点击歧义 |
| Android 套餐卡片紧凑化 | ✅ | 2026-07-31 | 「我使用的套餐」去掉双 MetricChip；剩余/到期合并一行；进度条 4dp、按钮 36dp、padding 14 |
| Android 关于页展示安装信息 | ✅ | 2026-07-31 | 「关于跨云」分块显示当前 App 版本名/码与最新版本检查 |
| Android 3.15.2 热修（系统路径与硬门禁对齐） | ✅ | 2026-07-30 | 取消跨云自身强制 `addDisallowedApplication`；`vpn_network_ok` 接受 VALIDATED；过滤 fe80/zone DNS；`setUnderlyingNetworks`；`3.15.2`/`47` |
| Android 3.15.1 热修（VPN 探测可靠性） | ✅ | 2026-07-29 | 系统 VPN 探测 GET+重试；mixed 失败自动回退 gvisor；`3.15.1`/`46` |
| Android 3.15 发版（系统 VPN 硬门禁） | ✅ | 2026-07-29 | `3.15`/`45`：全流量须 `vpn_network_ok`；出海/回国分探测 URL；禁止 mixed+TUN 单独放行；PRD [`连接可信-系统VPN硬门禁产品需求.md`](product/连接可信-系统VPN硬门禁产品需求.md) |
| Android 系统 VPN 硬门禁（防假连） | ✅ | 2026-07-29 | 同上；`TunDataPlaneVerifier` + 单测 |
| iOS 真实 Mihomo 出网路径 | 🚧 | 2026-07-28 | Go `hub.Parse` 桥接 + NEProxySettings/mixed-port；`build-xcframework` 须在 **Mac** 产出；真机签名后可上网 |
| 客户端 CI Tag 自动发包 | ✅ | 2026-08-07 | [`app-release.yml`](../.github/workflows/app-release.yml)；手册已中文命名 [`GitHub自动打包与密钥配置说明.md`](guides/GitHub自动打包与密钥配置说明.md)；密钥 [`GitHub发版密钥说明.md`](../.github/GitHub发版密钥说明.md)；PRD [`客户端CI自动发包产品需求.md`](product/客户端CI自动发包产品需求.md) |
| macOS 系统代理加固 | ✅ | 2026-07-28 | 对所有有 IP 的网卡设 HTTP/HTTPS 代理 + bypass localhost；与 Win 对齐防回环 |
| macOS 桌面验收清单落地 | ✅ | 2026-07-28 | [`macOS桌面验收清单.md`](product/macOS桌面验收清单.md)：P0 系统代理 E2E / P1 体验 / 公证发版；待真机勾选 |
| 桌面主窗口关闭最大化 | ✅ | 2026-07-28 | `WebviewWindowBuilder.maximizable(false)`；仍可拖拽改尺寸 |
| 桌面节点卡对齐 Android（去协议） | ✅ | 2026-07-26 | `KyNodeCard` 不展示协议；地区行/场景标签按筛选简化；列表底边距加大 |
| 桌面连接页内容垂直居中 | ✅ | 2026-07-25 | `connect-page` 在主内容区 flex 垂直居中，避免贴顶留白过大 |
| Android 3.14 发版（回退 3.13 门控） | ✅ | 2026-07-25 | `3.14`/`44`：探测策略同 3.12（`PostConnectVerifyPolicy`）；覆盖已发布问题版 3.13；文档已同步（`文档目录`/`升级管理`/`AGENTS`/`打包说明`/`README`）；arm64 Release 产物 `apps/android/app/release/app-arm64-v8a-release.apk`；模拟器已验证芜湖回国可连 |
| Android 3.13 海外回国就绪门控+失败分类 | ❌ 已废弃 | 2026-07-25 | 已回退：删除 `ProxyPathReadiness` 与分类文案；根因更偏港 Reality 入口，非门控本身；由 **3.14** 覆盖发版 |
| Android 3.12 海外回国首连探测放宽 | ✅ | 2026-07-25 | 3.11 硬门禁 + 2×1.5s 误杀缅甸→港 Reality→芜湖；`PostConnectVerifyPolicy`：海外+回国 4×2.5s settle1.2s；国内仍默认 3 次；防回归写入 `apps/android/AGENTS.md` / `android-app.mdc` |
| 桌面连接中 UI 不被 status 冲掉 | ✅ | 2026-07-25 | 在途忽略 disconnected；底部去掉「正在连接」；按钮波纹对齐 Android 尺寸 |
| 桌面去刷新钮 + 窗口尺寸收紧 | ✅ | 2026-07-25 | 去掉桌面右上角刷新；默认窗 980×680、最小 860×600（侧栏+内容更贴合） |
| 主 Tab 去页头 + 侧栏版本号 | ✅ | 2026-07-25 | `KyTabPage` 移除 BrandHeader；`MainShell` 侧栏 logo 旁显示 v{{version}}；刷新钮浮动不占位 |
| 桌面选节点后连接中 UI 立即统一 | ✅ | 2026-07-25 | `connectPending` 跳转前即置位；Hero 立刻「连接中」；去掉底部重复 hint/蓝条 |
| 桌面连接页布局疏密优化 | ✅ | 2026-07-25 | 压缩 Hero/按钮区留白；会话卡分区 gap=14、隧道流量默认收起对齐 Android；`stack-gap=sm` |
| 桌面连接按钮动画对齐 Android | ✅ | 2026-07-25 | `ConnectPowerButton`：连接中扩散波纹、已保护稳态护盾环、按压缩放、ConnectVisual 色板；连接中可点中断；文案去掉「再点可取消」 |
| 桌面连接门禁对齐 Android | ✅ 已调整 | 2026-08-08 | **全端 Verge 模型**：mihomo/系统代理或 TUN 起来即 Connected；不以外网探针成败挡连接。连后探测仅软诊断（出口 IP/心跳），不拆隧道、不因探针自动切节点 |
| 桌面连接页/流量对齐 Android | ✅ | 2026-08-08 | `ConnectSessionCard` 同行速率+时长；文案「剩余 GB」；速率改用累计字节差+EMA（禁用 `/traffic` 瞬时）；时长 `mm:ss` 补零 |
| 「我的」入口精简 | ✅ | 2026-08-08 | 订阅导出/规则直连/诊断日志仅 `app_debug_enabled` 可见；iOS Profile 同步 |
| Tauri Android 应用内 APK 更新 | ✅ | 2026-08-08 | `VpnPlugin.installApkUpdate` + `AppUpdateInstaller`；FileProvider；前端 `installAppUpdate` 优先走插件 |
| 发版 1.2.9 | ⚠️ | 2026-08-08 | Tag 已打；CI 因 `connect.ts` 未使用变量 TS6133 失败 |
| 发版 1.2.10 | ✅ | 2026-08-08 | 清理 dead code 后重发；Tag `v1.2.10` code `130` |
| 桌面选节点后先跳连接页 | ✅ | 2026-07-25 | 对齐 Android `selectedTab=0` 再 `connectToNode` |
| 桌面上下行偶发显示 — | ✅ | 2026-07-25 | `formatDisplaySpeed` 对齐 Android；EMA 平滑；`secret: ""` 保证 `/connections` 可读；JSON 总量支持 float；优先 Mihomo `/traffic` 瞬时速率 |
| 桌面连接后窗口无响应 | ✅ | 2026-07-25 | Win 系统代理补 `ProxyOverride` 绕过 localhost（避免 WebView/Vite 回环）；探测总预算 ≤12s、优先 curl 且静默 PowerShell；`vpn_connect`/`vpn_probe` 走 `spawn_blocking` |
| 桌面节点选完即连 + 上下行流量修复 | ✅ | 2026-07-25 | 节点页对齐 Android 选完即连；`desktop_traffic` 支持 HTTP chunked，失败打日志并回退上次会话字节；误导文案「下次连接」已改 |
| Tauri 启动 Splash 打磨（方案1 已落地） | ❌ 已替代 | 2026-08-08 | 原独立 `splash.html` 方案；现改为「主窗 visible:false → 就绪 show」 |
| Tauri↔Android 对齐缺口清单回写 | ✅ | 2026-07-24 | [`Tauri-Android功能对齐.md`](product/Tauri-Android功能对齐.md) §6：P0 系统代理 E2E + iOS xcframework；P1 失败反馈/设置文案/updater/Splash；刻意不对齐收口；Splash 标 redirect |
| Tauri 桌面冒烟（dev + 浏览器） | ✅ | 2026-07-24 | `luban7733@gmail.com`：登录/四 Tab/套餐/充值/订单/流量/工单客服帮助/设备/规则直连/连接与隐私/诊断/关于均可用；选节点+地区筛选 OK；帮助可生成 Clash；**VPN 真连仍须 Tauri 窗口人工验** |
| 文档与代码对齐（Vision / 入口证书 / Tauri 版本文案） | ✅ | 2026-07-24 | 功能 todo 废弃「不再强制 Vision」历史结论；入口 `ensureCertificates` 标代码已修；Tauri 打包说明与 lock 对齐 1.1.0；对齐清单刷新同步日期 |
| 规则与文档同步（产物 ignore / AGENTS 索引） | ✅ | 2026-07-23 | `01-safety-git` 明确禁止入库二进制与 ssh/模拟器产物；`AGENTS`/`文档目录`/`android AGENTS` 同步；功能 todo 去掉过时「AGENTS §16」指向，改指 `vpn-protocol-reality.mdc` |
| 仓库产物清理（api 二进制 / 调试截图 / ssh_debug 运行产物） | ✅ | 2026-07-23 | 移出跟踪：`api`/`api.linux`、`_emulator_shots`、`__pycache__`、大量 `ssh_debug` json/txt/yaml/ui dump；`.gitignore` 补根目录 go build、Python 缓存、模拟器截图、ssh 运行产物；本地已删 `api.exe` 与 `ssh_debug/.tools` 缓存 |
| AI 协作规则拆分（AGENTS + .cursor/rules） | ✅ | 2026-07-23 | 根 `AGENTS.md` 短索引；alwaysApply 仅 core/safety/docs/monorepo；Go/Admin/Android/Tauri/协议/测试/产品文风按 globs；删旧四条全量 alwaysApply |
| Android 3.11 防假连：数据面 inactive 强制断开 | ✅ | 2026-07-23 | `dataplane inactive` → `disconnectDataplaneInactive`（`force_disconnect=true`）；非 inactive 探针仍可 soft-degraded 保隧道 |
| Tauri 对齐 Android 3.10/3.11 连接体验 | ✅ | 2026-07-23 | 关自动 failover；Hero 连接中可中断；节点页连接中可切；速率 warmup/cap；切地区不自动测速 |
| 多协议 WG 经中转（sing-box 族） | ✅ | 2026-07-22 | 入口按协议终结 + WG 回传明文 VLESS；OV/WG 仍仅直连；见 [多协议WG经中转产品需求](product/多协议WG经中转产品需求.md)；单测已通过，生产 E2E 待部署后验收 |
| 入口 entry_gateway 缺自签证书导致部署失败 | ✅ | 2026-07-24 | 代码已修：`applyEntryGatewayConfig` 调用 `ensureCertificates`（`cmd/node-agent/composite_runtime.go`）；生产镜像发布后自动生效（此前已手工热修） |
| App 经中转支持 sing-box 全族 | ✅ | 2026-07-22 | Android `3.11`/`41`、Tauri `1.1.0`/`110`：`isRelayCompatible` 不再限 VLESS+Reality；补单测；旧版会滤掉 Trojan 回国线 |
| 连接页切换节点 + 节点页测速体验 | ✅ | 2026-07-22 | 已连接可点「切换」进节点页；切地区不再自动测速；文案「批量测速」；连接按钮通栏主色 |

最后核对（历史）：**2026-07-21**（节点协议切换脏字段消毒；Vision 策略后被 07-22 一致性修复 supersede）

| 节点协议切换脏字段清理 | ✅ | 2026-07-21 | `sanitizeNodeProtocolFields`：改协议后清 Reality/flow/Hysteria 互斥字段；前端 `buildProtocolUpdateFields` 空串也下发；订阅生成 Reality 仅限 vless |
| 直连 VLESS「不再强制 Vision」（已废弃） | ❌ | 2026-07-21 | **已被 2026-07-22 一致性修复 supersede**：现行 `ensureRealityDefaults` 在 VLESS+Reality 且 flow 为空时补 `xtls-rprx-vision`（直连=中转）；见下行 Reality Vision 条目与 `vpn-protocol-reality.mdc` |
| 新加坡直连脏数据线上清理 | ✅ | 2026-07-21 | Trojan 残留 Reality/flow 已清；当时曾置空 SG VLESS flow——**勿再批量清 Vision**；现行默认补 Vision |

最后核对（历史）：**2026-07-16**（经港 WG 公网 34500 + 节点列表去噪/按中转分组）

| 经港 WG 公网路径固化（EIP:34500） | ✅ | 2026-07-16 | 入口 Listen/Endpoint=34500；落地高端口；Endpoint 仅公网；入口 host；清 sport RETURN；需求见 `docs/product/经港WG公网路径与节点列表优化需求.md` |
| 节点列表：链路与资源脱钩 + 中转分组 | ✅ | 2026-07-16 | Agent 降级不再抬升链路告警；运行状态绿色短文案；按中转分组；异常才提示放行端口；组头可编辑中转；正常态显示「链路正常」 |

最后核对（历史）：**2026-07-10**（PC 壳内子路由 + Ky 布局组件 + 连接/客服体验；浏览器冒烟通过）

最后核对（Tauri 桌面）：**2026-07-20**（对齐 Android 3.9：会话流量 baseline、未选节点跳转、禁用探测降级断开）

| Tauri Windows 连接页会话流量显示 | ✅ | 2026-07-20 | Rust `traffic_baseline` + `session_traffic()`；前端 1s 轮询 `syncVpnStats()`；连接时 `resetSessionStats()` |
| Tauri 未选节点/套餐连接跳转 | ✅ | 2026-07-20 | `requestNavigateToNodes/Packages` + `MainShell` 消费；节点页「连接此节点」选完即连 |
| Tauri 禁用探测降级自动断开 | ✅ | 2026-07-20 | `dataplaneDegradedDisconnectMs=0`；清除 localStorage 无效「智能选路」节点（**已取代**旧「90s 后断开」） |
| Tauri 连接页 Hero 置顶（对齐 Android） | ✅ | 2026-07-09 | `ConnectHero` + `ConnectQuickStatus` / `ConnectSessionCard` |
| Tauri 连接页移除多IP/单IP 与分流开关 | ✅ | 2026-07-09 | 入口移至「我的」；分流由地区自动决定 |
| Tauri PC 主内容区间距与宽屏排版 | ✅ | 2026-07-10 | `KyStack`/`KyGrid2`/`KyTabPage`；套餐/节点双列；子页收进 `MainShell` 保留侧栏 |
| Tauri 子页壳内路由（侧栏不消失） | ✅ | 2026-07-10 | `/main/*` 子路由 + 旧路径重定向；`PageHeader` 返回+面包屑 |
| Tauri Ky 业务卡片组件 | ✅ | 2026-07-10 | `KyPackageCard`/`KyNodeCard`/`KySubscriptionSummary`/`KySelectedBanner`/`KyAuthFooter` |
| Tauri 未选节点一键连接跳转节点页 | ✅ | 2026-07-10 | 对齐 Android `requestNavigateToNodes`；不再展示「请先选择节点」失败态 |
| Tauri 在线客服页重排 | ✅ | 2026-07-10 | 渠道分卡+间距；`ticket` 进工单新建表单（`?create=1`） |
| Tauri 桌面刷新按钮高度跳动 | ✅ | 2026-07-10 | `KyPullRefresh` 加载态保留 32px 按钮壳 |
| Tauri 品牌头显示版本号 | ✅ | 2026-07-10 | `KyTabPage` → `show-version`；`v{APP_VERSION_NAME}` |
| Tauri 浏览器托盘挂载报错 | ✅ | 2026-07-10 | `tray.ts` 检测 `__TAURI_INTERNALS__`；无运行时跳过 |
| Tauri 套餐页 expires_at 空值崩溃 | ✅ | 2026-07-09 | `subscription.ts` 防空；切换 Tab 不再空白 |
| Tauri Tab 切换空白（路由 Transition） | ✅ | 2026-07-09 | 移除 `MainShell` 路由 `Transition` |
| Tauri Windows 连接探测误判 | ✅ | 2026-07-10 | `desktop_probe.rs`：`curl HEAD` + 重试，对齐 Android `MihomoLocalProbe`；降低误 degraded |
| Tauri 开发模式 API 代理（CORS） | ✅ | 2026-07-10 | `.env.development` → `VITE_API_BASE_URL=/api` + Vite proxy；浏览器可登录测 UI |
| Tauri 登录/请求网络错误提示 | ✅ | 2026-07-09 | `api-error.ts` + `request.ts` 统一文案 |
| Tauri 默认 API 与 Android 对齐 | ✅ | 2026-07-09 | `http://192.229.87.112:44080/api` |

| Tauri Phase 1 连接与隐私页 | ✅ | 2026-07-07 | MVP 精简：系统代理说明 + 自动重连/托盘/泄露自检；TUN/KS 已隐藏 |
| Tauri 连接隐私/节点密度对齐 Android 3.15.x | ✅ | 2026-08-01 | 隐私自检会员白话 + 最近 1 条；节点卡行尾紧凑连接/切换；不对齐 HEAL/没网等待/硬门禁 |
| Tauri Phase 2 规则直连/场景/Failover/隐私 | ✅ | 2026-07-05 | 见 Phase 0–1 条目；IPv6 探测 Rust `privacy.rs` |
| Tauri Phase 0 Ky UI 重构 | ✅ | 2026-07-05 | 移除 ant-design-vue；自研 Ky 组件库 |
| Tauri PC 侧栏布局 | ✅ | 2026-07-05 | 断点 960px；`MainShell.vue` |
| Tauri Phase 1 系统托盘 | ✅ | 2026-07-05 | `src-tauri/src/tray.rs` |
| Tauri Phase 1 自动重连退避 | ✅ | 2026-07-05 | 3 次退避 3/6/10s |
| Tauri Phase 1 degraded 探测 | ✅ | 2026-07-05 | 历史曾 90s 断开；**现行** `dataplaneDegradedDisconnectMs=0`（2026-07-20），仅记 degraded |
| Tauri Phase 1 TUN / Kill Switch | ✅ | 2026-07-05 | Rust `desktop_mode.rs`/`kill_switch.rs`；前端 bridge + 设置页；Win 需管理员 |
| Tauri Phase 1 诊断日志页 | ✅ | 2026-07-05 | `DebugLogView.vue` + `app-debug-log.ts`；Profile 入口（需 `app_debug_enabled`） |
| Tauri Phase 1 tauri-plugin-updater | ✅ | 2026-07-05 | 插件 + `/client/version/tauri-manifest`；`npm run setup:updater`；无 pubkey 时 API 回退 |
| Tauri Phase 4 单元测试 | ✅ | 2026-07-05 | vitest：connection-scenario/direct-bypass/privacy/failover；CI `tauri-ci.yml` |
| Tauri 桌面客户端 | ✅ | 2026-07-05 | Phase 0–2 + updater manifest + CI；见 [Tauri重构PRD](product/Tauri桌面客户端重构产品需求.md) |
| iOS / iPhone Phase A | ✅ | 2026-07-05 | `apps/tauri/platforms/ios/` 脚手架；见 [iOS PRD](product/iOS客户端产品需求.md) |
| iOS / iPhone Phase B | 🚧 | 2026-07-05 | VPN 连接全链路；xcframework 待链 |
| iOS / iPhone Phase C | ✅ | 2026-07-05 | 套餐/流量/Profile/规则直连/注册/诊断 |
| Tauri Linux/macOS 构建脚本 + CI | ✅ | 2026-07-05 | `tauri:linux:build`、`tauri:mac:build`；CI `tauri-linux-build` / `tauri-macos-build` / `tauri-ios-build` |
| 管理后台桌面安装包发布 | ✅ | 2026-07-05 | App 版本页支持 android/windows/macos 筛选上传 + updater_signature |
| 下载页/后台平台 Tab + iOS | ✅ | 2026-07-28 | 管理后台与会员下载页平台 Tab（Android/Windows/macOS/iPhone）；`ios` 上传 `.ipa`；下载页每类型仅最新 published |
| 下载页扫码下载二维码 | ✅ | 2026-07-31 | 会员 `/download`：当前平台最新安装包绝对 URL 二维码（`qrcode.vue`）；无版本不展示 |

> **跨端产品范围（2026-07-23 修订）**：详见 [Tauri-Android功能对齐](product/Tauri-Android功能对齐.md)。**路由/API/会员业务/PC 壳布局/连接体验四项** 已对齐。发版前阻塞项：**桌面系统代理 E2E**、**iOS xcframework**。TUN / Kill Switch / FCM / 分应用直连 **仅 Android**。

| Tauri 页面对齐 Android | ✅ | 2026-07-10 | 19/19 路由（不含分应用直连）；子页均在 `/main/*` 壳内 |
| Tauri API 对齐 Android | ✅ | 2026-07-07 | `client.ts` ≈ Android Repository |
| Tauri 桌面 MVP（系统代理） | 🚧 | 2026-07-10 | UI/bridge/探测已落地；待 Win/Mac/Linux **Tauri 窗口** E2E |
| Tauri 桌面 UI 三端一致 | ✅ | 2026-07-05 | 一套 `apps/tauri/src/` |
| Tauri 桌面设置页精简 | ✅ | 2026-07-05 | 隐藏 TUN/Kill Switch；`DESKTOP_MVP_PROXY_ONLY` |
| Tauri 桌面 TUN/Kill Switch 对齐 Android | ❌ 不对齐 | 2026-07-05 | 产品范围；Rust 代码保留 |
| Tauri / iOS FCM 推送 | ❌ 不做 | 2026-07-05 | 仅 Android |
| Tauri 分应用直连 | ❌ 不做 | 2026-07-05 | 仅 Android OS 能力 |
| iOS Mihomo xcframework 集成 | 🚧 | 2026-07-28 | 真实引擎桥接已写；**须在 Mac** `npm run tauri:ios:build-xcframework` 后 setup-native；见 [接入手册](product/iOS-Mihomo-xcframework接入.md) |
| iOS 业务页对齐 Android | ✅ | 2026-07-07 | 含 Splash/隐私/充值通知/自检历史 |
| iOS P1 体验补齐 | ✅ | 2026-07-07 | Splash/Privacy/通知轮询/自检历史 |
| Tauri 桌面连接页速率展示 | ✅ | 2026-07-23 | `estimateDisplayMbps`：3s warmup / ≥400ms 采样 / 200Mbps 上限（对齐 Android） |
| Tauri 桌面 preflight 脚本 | ✅ | 2026-07-07 | `npm run preflight:desktop` |
| iOS Phase B VPN 数据面 | 🚧 | 2026-07-07 | UI/API 齐；无 xcframework 实际上网不通 |

| App v3.7 重登后连接态重置 | ✅ | 2026-07-03 | 别处登录/登出/重新登录时 `VpnConnectionBus.resetForSessionEnd()`；修复误显示「连接失败」 |
| App v3.7 海外回国优化 | ✅ | 2026-07-03 | 海外时区回国强制 gvisor；失败文案细分（海外模式/换节点）；全流量跳过 geodata CDN 下载 |
| 管理后台用户列表删除 | ✅ | 2026-07-03 | `DELETE /v1/admin/users/:id`；级联清理订单/订阅/流量等；不可删管理员 |
| WG 回传端口 51999 + 后台配置 | ✅ | 2026-07-03 | 默认 `51999`；`设置→节点策略→新建默认` 全局项 + 节点详情 `wg_listen_port` 覆盖；`init.sql` 批量迁移 51820→51999 |
| WG 入口 bridge SNAT（PostUp + 部署脚本） | ✅ | 2026-07-03 | 历史方案：入口 bridge + CIP SNAT |
| WG 入口曾强制 host（17131b9） | ⚠️已回退 | 2026-07-15 | EIP 机器 host WG 回程 SNAT 不可靠，导致经港全挂；生产已紧急回退 bridge |
| WG 入口默认 bridge + 禁 userland-proxy | ✅ | 2026-07-15 | `resolveNodeInstallNetwork`：**不再**对 `role=relay` 强制 host；部署写 `userland-proxy:false`、固定 `:51999`、双栈 CIP SNAT + `34500→51999` 兼容；落地仍 host；Agent 仅单 peer 钉 Endpoint |
| WG 入口 PostUp 禁用裸调 iptables-legacy | ✅ | 2026-07-16 | `3711835` 引入的 PostUp 在 agent 镜像无 `iptables-legacy` 时导致 wg-quick exit 127 并删除 wg0；改为 `command -v` 判断且 `|| true`，避免拖垮接口 |
| WG 入口 bridge：禁容器挂 EIP + peer 排序 + 不 publish WG UDP | 🔧待发布 | 2026-07-16 | 部署后仍异常根因：(1) `FindWGPeersByEntry` 无 ORDER BY → conf hash 抖动 → **每分钟** wg-quick 全量重拉；(2) PostUp `ip addr add EIP/32` 在 bridge 容器内导致出站源错乱；(3) `userland-proxy:false`+`-p51999/udp` 时 dockerd 占端口不转发（Recv-Q 黑洞）。已改为：稳定排序、去掉挂 EIP、WG 靠 any-dest DNAT、不 docker-publish UDP。生产已紧急 DNAT-only + strip-EIP |
| WG 落地 host 保端口（默认） | ✅ | 2026-07-15 | 入口：nft+legacy SNAT；落地 EIP：RETURN 保 sport、禁止 SNAT→公网；入口 Agent 多 peer 不钉 Endpoint；批量修复 `fix_hk_exit_endpoint_snat_batch.py`。注：部分云主机 host WG 出站绕过 nat / `51999` 源地址异常，需绑定公网 /32 或换 listen 口 |
| 节点 Docker 引擎安装国内友好 | ✅ | 2026-07-15 | 默认 `mirror_first`：`download.docker.com` → 阿里云/清华 → `get.docker.com` 60s 短超时；后台可改策略；无需配源 URL |
| WG 经中转部署与巡检 SOP | ✅ | 2026-07-05 | [guides/WG经中转节点部署与巡检.md](guides/WG经中转节点部署与巡检.md)；入口默认 bridge（禁 userland-proxy）；落地 host；验收 `:51999` 握手 |
| WG 落地 host 网络 + 去掉 PostUp MASQUERADE | ✅ | 2026-07-05 | `resolveNodeInstallNetwork` 强制 WG 落地 `--network host`；`buildBackhaulExitWGServerConf` 仅 FORWARD；`ensure_host_wg_snat` 覆盖 exit |
| WG 入口 ListenPort 与 Docker 发布对齐 | ✅ | 2026-07-03 | 入口 wg 固定 **51999**（原 51820 已迁移）；需重部署香港入口 |
| 节点诊断 WG 握手项 | ✅ | 2026-07-03 | `relay_wg_handshake` / `relay_wg_handshake_entry`；WG 模式公网 8341 拒绝标为正常；`node_diagnose_wg.go` |

> **Android 成熟化 backlog**：见 [App成熟化产品路线](product/App成熟化产品路线.md)（E1–E6）与 [App企业级差距清单](product/App企业级差距清单.md)

| 管理后台总览页运营精简 | ✅ | 2026-07-01 | 待办区+4 KPI+预警+需关注节点；移除实时监控/流量/冗余 KPI；`dashboard/index.vue` |
| 管理后台财务菜单 IA | ✅ | 2026-07-01 | 支付与财务下新增「套餐订单」；Top/Mix 补齐业务子菜单；finance 默认 USDT 审核；订单 keyword 搜索；**修复余额流水页 `loadTransactions` 缺失导致空白** |
| init.sql 线上非破坏性迁移 | ✅ | 2026-07-01 | 移除自动 `DROP TABLE client_markets`；`migrations/init.sql` 可重复执行且不删除业务数据，历史清理改为人工脚本 |
| App 企业级差距清单（E1–E6） | 📋 | 2026-07-01 | 对标 ExpressVPN/NordVPN/快帆；见 [差距清单](product/App企业级差距清单.md)；默认全量 APK |
| App 成熟化 E2 ROM 真机矩阵 | 🚧 | 2026-07-01 | `rom-matrix/records.json` + `rom-matrix-gate.sh`；当前 1/5 通过，待填真机 |
| App 成熟化 E3 崩溃率发版门禁 | 🚧 | 2026-07-01 | `GET .../crash-health` + 后台卡片 + `release-crash-health-hint.sh`；样本不足时 REVIEW |
| App 成熟化 E4 仪器化 CI | 🚧 | 2026-07-01 | `.github/workflows/android-ci.yml` nightly + PR 单元测试/Release 构建 |

| Docker 生产部署 compose.prod | ✅ | 2026-06-28 | `docker-compose.prod.yml` + 前端 Dockerfile；`scripts/docker-prod-up.sh` |

| 新加坡直连 Reality 握手域名修复 | ✅ | 2026-06-26 | `www.microsoft.com` 证书过大导致 sing-box Reality 恒失败；默认改为 `www.github.com` 并自动纠正存量节点 |
| 会员节点列表与 `/client/config` 同源过滤 | ✅ | 2026-06-26 | `GET /nodes` 走 `GetConnectableNodesForUser`；不再展示不可连节点 |
| 订阅单活（防多条 active 脏数据） | ✅ | 2026-06-26 | `ReplaceActiveSubscription` + `FindActiveByUserID` 自愈；`FindActiveVPNUsers` 按用户去重 |
| App 连接探测失败策略（P0-2） | ✅ | 2026-06-27 | 探测 FAILED → `ProbeStatus.DEGRADED` **保持连接**，不再主动断开；见 `ConnectViewModel.startProbe` |
| App 诊断日志 device_meta | ✅ | 2026-07-01 | `POST /users/me/app-debug-logs` 可选 `device_meta`；旧 App 不传兼容 |
| WG 链路异常节点 App 过滤 | ✅ | 2026-07-01 | `node_connectable_health.go`；`/nodes` 与 `/client/config` 过滤 |
| WG 回传握手纳入节点健康状态 | ✅ | 2026-07-02 | 修复 `calculateNodeHealth`：`exit_wg` 若 `last_handshake_sec=-1/超时` 不再显示 `healthy`，改为 `unhealthy/degraded`；补充单测 |
| App 成熟化 P0（连接可信） | ✅ | 2026-07-23 | P0 已落地并迭代：数据面校验 + **inactive 立即断开**（取代长期假连/旧 90s 策略）；见 [成熟化路线](product/App成熟化产品路线.md) §3 |
| App 成熟化 P1（质量门禁） | ✅ | 2026-07-01 | `release-gate.sh`、发版清单、ROM 矩阵、诊断摘要+筛选、泄露自检/保护等级历史、API 错误遥测+后台 SLO 采样 |
| App 接口稳定性治理 P2 采样 | 🚧 | 2026-07-01 | Android `ApiErrorTelemetry` + `api_error` 诊断日志；后台 `/app-debug/api-stability`；弱网脚本纳入 release-gate；全量 SLO 告警待做 |
| App 成熟化 P2（性能体验） | 🚧 | 2026-07-01 | P2-2~5 ✅；P2-1 瘦包模式 `-PslimNativeLibs=true`（~3MB）/ 默认 ~49MB |
| 曼谷回国加速验收（P0-9） | 🚧 | — | 需曼谷 SSH：`ssh_debug/test_bangkok_cn_nodes_domestic_return.py`；KPI ≥12/15 |

> **弱网验收**：见 [客户端弱网与选路优化方案](product/客户端弱网与选路优化方案.md) §13。

> **多协议集成路线**：见 [多协议集成需求文档](product/多协议集成需求文档.md)（Phase 1 代理协议补全 → Phase 2 直连运营 → Phase 3 OV/WG）。

---

## 1. 节点与协议

| 功能 | 状态 | Phase | 代码/说明 |
|------|------|:-----:|-----------|
| 默认协议 VLESS + Reality + Vision | ✅ | — | `node_defaults_config.go`；订阅 Clash 主路径 |
| 节点协议 VMess / Trojan / Hysteria2 | ✅ | — | 后台可选；节点 inbound 由 node-agent 生成 |
| Shadowsocks 节点 | ✅ | **1** | 后台可创建；SS 订阅默认启用（弱网备用） |
| WireGuard 节点（wireguard-tools 原生栈） | ✅ | **3** | node-agent `wg-quick` + `/subscription/wireguard` |
| OpenVPN 节点（PKI + node-agent 自动启动） | 🚧 | **3** | 部署脚本已识别 openvpn 进程；**生产 E2E 待验证** |
| OpenVPN PKI 重新生成 | ✅ | **3** | `POST /admin/nodes/:id/regenerate-openvpn-pki` |
| node-defaults 含 hy2/ss | ✅ | **1** | 默认配置页已对齐单节点协议下拉 |
| relay 协议校验（relay⇒sing-box 族；拒 OV/WG） | ✅ | **1** | `validateNodeRelayProtocol`；见 [多协议WG经中转](product/多协议WG经中转产品需求.md) |
| 节点诊断（协议/UDP/TLS） | ✅ | **2** | `node_diagnose_protocol.go` |
| 会员订阅 relay/直连说明 | ✅ | **2** | 会员页双场景订阅链接 + 分流文案；App 节点标签与场景提示 |
| 订阅 profile 分流（overseas_weak / domestic_return） | ✅ | **2** | Clash 订阅 + App profile；见 [订阅分流产品需求](product/订阅分流产品需求.md) |
| 曼谷 Clash vs App 对照测速脚本 | ✅ | **2** | `ssh_debug/test_bangkok_clash_vs_app.py`；原始结果 `last_bkk_compare.txt` |
| 自研 App Mihomo 内核迁移 | ✅ | **P0** | `/client/config` 返回 Clash YAML；Android/Tauri `mihomo-core`；已移除 libbox 与 sing-box 订阅 |
| Trojan/Hy2 直连运营 SOP | 📋 | **2** | 不经 relay；生产联调清单待补 |
| OpenVPN / WireGuard 服务端自动部署 | 🚧 | **3** | OV：Agent + 部署脚本已支持 openvpn；WG 原生栈 |
| L4 socat 中转 | ❌ | — | **已移除**（2026-06-22）；relay 下发 `relay_migration_standby` |
| sing-box 双跳中转（已废弃） | ❌ | — | 已移除 |
| WireGuard 节点间回传中转 | 🚧 | — | WG 链路灯；**经中转出口跳过落地直连误报**；心跳公网 IP 缓存防阻塞 |
| 出口直连 `access_mode=direct` | ✅ | — | 新加坡等直连节点 |
| 出口经中转 `access_mode=relay` | ✅ | — | 绑定 `relay_node_id` + 自动端口池；**sing-box 族均可**；`relay_address` 系统维护 |
| SSH 一键部署 + PHASE 日志 | ✅ | 2026-06-25 | 入口网关发布各落地 relay_listen_port + **51999/udp**；WG 链路健康可识别「容器正常但未映射端口」 |
| 节点批量创建与 SSH 部署 | ✅ | 2026-06-25 | `/nodes/list` 批量创建部署 + **选中节点批量部署**；镜像单次打包；见 [节点批量部署 PRD](product/节点批量部署产品需求.md) |
| 节点 Web SSH 终端 | ✅ | 2026-06-28 | ticket + WebSocket PTY；已修复 WaitGroup panic→502；生产宿主机 Nginx（宝塔）须配置 Upgrade，见 `deploy/nginx/outer-proxy-vpn.example.conf`；2026-07-02 增加多层保活、取消空闲/最长时长自动断开 |
| 节点诊断（含中转链路） | ✅ | — | `node_diagnose*.go` |
| 管理后台「协议使用说明」 | ✅ | — | `/settings/protocol-guide`；协议能力矩阵、会员/运维 SOP；节点编辑「协议配置」联动提示 |

---

## 2. 订阅与客户端格式

| 订阅类型 | 默认启用 | 状态 | 说明 |
|----------|:--------:|------|------|
| Clash Meta | ✅ | ✅ | `/api/v1/subscription/clash?profile=`；**海外/回国分流** + 节点池分组；第三方 Mihomo/Clash 客户端 |
| ~~Sing-box~~ | — | ❌ | **已移除**（2026-06-22）；统一 Clash 订阅 |
| V2Ray | ✅ | ✅ | `vless://`（Reality 参数完整）+ `vmess://`；与 Clash 同源节点过滤；v2rayN 可导入 |
| 管理后台订阅链接域名 | ✅ | 2026-07-07 | 优先使用「门户配置」`admin_url` 生成 `https://.../api/v1/subscription/*` |
| Shadowsocks | ❌ | 🚧 | 有 API；需 SS 节点 + 后台启用 |
| Trojan | ❌ | 🚧 | 有 API；Reality 节点不暴露 Trojan fallback |
| WireGuard | ❌ | ✅ | `/subscription/wireguard`；需 WG 节点 + 后台启用 |
| OpenVPN | ❌ | 🚧 | `/subscription/openvpn`；含 CA inline + auth-user-pass；需 OV 节点 + 后台启用 |

| 客户端 | 状态 | 说明 |
|--------|------|------|
| 会员 Web 门户 | ✅ | `frontend/member`；仅展示 Clash 等有效订阅类型 |
| 管理后台 | ✅ | `frontend/admin` |
| Android App | ✅ 生产强化 | `apps/android`；Mihomo TUN + 稳定性 P0–P3（2026-06-27）：断网自动重连、Kill Switch、开机自启、周期探测、配置热重载；见 [稳定性评估与优化方案](product/自研App稳定性评估与优化方案.md) |
| App 断线自动重连（P0） | ✅ | 2026-06-27 | `VpnSessionStore` + `ConnectViewModel.scheduleAutoReconnect`；最多 3 次退避 |
| App 探测失败策略统一（P0） | ✅ | 2026-06-27 | 探测失败 → `ProbeStatus.DEGRADED` 保持连接，不再主动断开 |
| App 稳定性 P1–P3 | ✅ | 2026-06-27 | 网络切换 heal、Service 恢复、30s 连接超时、Kill Switch、Boot 自启、崩溃恢复标记；P3-5 MPTCP 未做 |
| App 性能优化 P1–P4 | ✅ | 2026-06-27 | Geo 后台下载、通知 5s、探测 120s/60s、TUN 栈开关、`ConnectTimingTracker`；见 [稳定性方案 §9](product/自研App稳定性评估与优化方案.md#9-性能优化2026-06-27) |
| App APK 瘦身（geo 剔除） | 🚧 | 2026-06-27 | arm64 Release **49MB**（-8MB）；目标 &lt;40MB 待 `libclash.so` 优化或 AAB |
| App 隐私门控（geodata 下载） | ✅ | 2026-06-27 | 注册 `acceptPrivacy`；已登录迁移；`MihomoGeoAssetManager.scheduleInstall` 统一检查；无新页面 |
| App 出口 IP 探测（自研 API + 多源兜底） | ✅ | 2026-06-27 | `GET /client/exit-ip` + `ExitIpProbe` 优先自研 API，兜底 ip-api/ip.sb/Cloudflare/ipify |
| App 隐私保护与连接安全（商业 VPN 对标） | ✅ | 2026-06-28 | P0/P1 已落地；P2 泄露自检历史（设置页最近 5 条）✅ 2026-07-01；保护等级变更埋点/Tauri 待做；见 [PRD](product/App隐私保护与连接安全产品需求.md) |
| App 连接与隐私页 UX 优化 | ✅ | 2026-06-28 | `KuayunBackHeader` 统一头部；默认保护只读展示；系统加固待办可点击；未连接时 `BASELINE_READY` 状态 |
| App 连接与隐私页会员向精简 | ✅ | 2026-08-01 | 去掉保护等级英文变更日志与不可操作「默认保护」清单；未完整时主状态不再绿「已保护」；连接设置仅留自动重连；检测记录最多 3 条 |
| App 连接与隐私页场景化 UX | ✅ | 2026-08-01 | 未连接弱化「保护未完整」；开机恢复并入连接设置；加固三项加白话副标题；检测只留最近 1 条会员向摘要 |
| App 连接主流程 UX 优化 | ✅ | 2026-06-28 | 无节点/无套餐一键连接自动跳转对应 Tab + Snackbar；移除单 IP/多 IP 展示菜单；设备/应用直连/规则直连/诊断日志页 `KuayunPageScaffold` 头部统一 |
| App UX 仪器化验收 | ✅ | 2026-06-28 | `AppUxFlowInstrumentedTest`（跳转/无 IP/子页返回头）+ `PrivacyFeaturesInstrumentedTest`；模拟器 11 项通过（无套餐账号跳过选节点用例） |
| App 鉴权式断开 / 登出 Kill Switch | ✅ | 2026-06-28 | `ACTION_DISCONNECT_AUTH`；Boot/崩溃恢复校验登录；登出 wipe 配置 |
| App 接口稳定性治理（全链路） | 🚧 | 2026-06-29 | P0/P0.5 鉴权全链路 `AuthRequestSupport`；**P1 Android**：`ApiRequestSupport` + `callApiRead` 幂等读重试 + 全 ViewModel 统一 `mapError`；P2 SLO/弱网回归待实施 |
| App 用户通知 P0（本地系统通知） | ✅ | 2026-06-29 | `UserNotificationCoordinator` + 多 Channel；会话踢线/套餐断开/充值/冷启动 Banner；见 [PRD](product/App用户通知与消息触达产品需求.md) |
| App 被挤下线界面提示 + 息屏误挂起修复 | ✅ | 2026-07-20 | 踢线：Application 层断 VPN + `LastInvalidationStore.peek` 冷启动弹 Dialog；息屏：已连接不 `suspendCore`（`MihomoSuspendPolicy`）；数据面强制断开补通知 |
| App 断网恢复决策补强（CONNECTING 不卡死） | ✅ | 2026-07-20 | `NetworkRestorePolicy`：连接中断网恢复也走自动重连；仪器化飞行模式 flap 测试 |
| App 切网自愈升级自动重连 | ✅ | 2026-07-31 | `PostHealRecoveryPolicy`：HEAL 后短探测；失败则重连并可选保持 KS（防真实 IP） |
| App 没网等待有网再重连 | ✅ | 2026-08-01 | 无物理网不累计探活失败、不拆隧道；`NetworkMonitor.hasValidatedPhysicalInternet` 门控 |
| App 探针 soft-fail 策略（历史） | ✅ | 2026-07-20 | 曾取消「任意 degraded 90s 断隧道」；**现行**：仅非数据面类探针可 keep_tunnel；`dataplane inactive` 见上行「3.11 防假连」强制断开 |
| App 刚连上流量显示异常修复 | ✅ | 2026-07-20 | reset 冲 TrafficNow；CONNECTING/连上清空会话流量总线；通知栏与连接页统一 `formatSpeed` |
| App 连接体验四项优化（连接中可中断/切节点/快速失败） | ✅ | 2026-07-21 | 连接中再点大按钮中断隧道；节点页连接中可切节点；首次校验失败立即 FAILED；断网提示仅在「连接失败阻断」开启时显示（2026-07-22 默认关） |
| App 连接页速率虚高修复 | ✅ | 2026-07-23 | 累计差/时间差；3s warm-up；≥400ms 采样；展示上限 **200 Mbps**（早期 500Mbps 上限已收紧） |
| App 隐私引导弹窗移除 | ✅ | 2026-07-21 | `PrivacyOnboardingStore.shouldShowOnboarding()` 恒 false；Kill Switch/IPv6 等仍由 `PrivacyBaselineMigrator` 静默开启 |
| App 连接失败默认不断网 + 关闭自动 failover | ✅ | 2026-07-22 | `blockOnConnectFailure` 默认 false；基线 v2 迁移关闭旧用户该项；`NodeFailoverMonitor.AUTO_FAILOVER_ENABLED=false`；设置中仍可手动开断网保护 |
| 新加坡 VLESS Reality 国内不可达 → 改 Trojan | ✅ | 2026-07-22 | 根因：CN IP 连 Reality:8341 被 `REALITY: processed invalid`；普通线路 Trojan 正常。已将 BGP/新加坡1–5 切为 Trojan TLS:8341；订阅已下发；控制面 Mihomo E2E 验证 |
| 节点协议脏数据防回归（init.sql/管理端默认） | ✅ | 2026-07-22 | 删除 init.sql 强制非 Reality→Reality+Vision；管理端禁止 microsoft SNI；新加坡1 协议轮换验证 sanitize |
| Reality 直连/中转 Vision 一致性修复 | ✅ | 2026-07-22 | 根因修正：曾清空直连 Vision 而中转入口仍默认 Vision；`ensureRealityDefaults`/sing-box 用户缺省恢复 xtls-rprx-vision；防回归见 `.cursor/rules/vpn-protocol-reality.mdc` |
| VLESS Reality 密钥自愈 | ✅ | 2026-07-21 | `RealityKeypairMatches` + `HealAllRealityKeypairs`；API 启动扫描修复公/私钥不匹配；node-agent 轮询 `/nodes/config` 自动 reload |
| 新加坡 VLESS「误判不可达」修复 | ✅ | 2026-07-21 | 服务端密钥/E2E 已通；App：首次探测改 2 次短重试；TUN 有转发证据时放宽数据面判定；探测优先 GET generate_204 |
| App 我的设备删除/踢下线 | ✅ | 2026-07-02 | 会员设备列表仅展示 active 会话；踢下线后从列表移除；已下线记录可物理删除；Android 失败 Snackbar |
| App 连接页去除保护降级主区展示 | ✅ | 2026-07-02 | 已连接统一显示「已保护+节点」；探测仅后台日志/failover，不再驱动黄色降级 UI |
| App 探测误报治理（流量感知） | ✅ | 2026-07-02 | 会话流量充足时以真实转发为准清 bus degraded；与现行「inactive 立即断开」并存（`ConnectProbePolicy`） |
| App 连接页流量展示方案 A | ✅ | 2026-07-02 | 主区当前速率 Mbps + 已连接时长 + 套餐余量；本次隧道流量可展开；`ConnectNodeDetailCard` |
| App 应用内更新安装闭环 | ✅ | 2026-07-02 | 待安装 APK 持久化；授权返回自动/弹窗「立即安装」；关于页「继续安装」兜底 |
| App 更新弹窗重复提示修复 | ✅ | 2026-07-03 | 待安装版本 ≤ 当前版本时自动清除 pending；存 versionCode；启动/检查更新时 reconcile |
| App VPN 通知点击闪退修复 | ✅ | 2026-07-02 | MainActivity singleTop + 统一 PendingIntent；修复 pendingConnect 缺失；onResume 延后处理待安装 |
| 管理后台节点列表列合并 | ✅ | 2026-07-02 | 「接入路径」合并出口+订阅入口并分行显示 WG 内网；「状态/链路」合并运行状态与 WG 链路 |
| App 全流量弱网免 geodata 阻塞 | ✅ | 2026-06-29 | `route_mode=full` 跳过 geodata 等待；后端全流量仅 `MATCH,GLOBAL`；无 ruleset 时剥离 CDN rule-providers（缅甸/曼谷等） |
| 时区与时间一致性（API JSON + 规范） | ✅ | 2026-06-28 | `pkg/biztime`、response Normalize、TIMESTAMPTZ 迁移、前端统一 `@/utils/time`；见 [时区规范](guides/时区与时间规范.md) |
| 管理后台用户运营页 | ✅ | `/business/users`；单用户开启 App 调试；「注册与邮箱」跳转；批量开关见 `/settings/registration`；列表顶部会员统计卡片 |
| 用户列表订阅状态筛选修复 | ✅ | 2026-07-04 | 订阅状态筛选下推 SQL，分页前过滤，与统计卡片口径一致 |
| 管理后台节点列表筛选优化 | ✅ | 2026-07-04 | 地区 Tab 筛选；节点列仅显示地区名；运行状态列合并为一行摘要 |
| WG 入口链路检测优化 | ✅ | 2026-07-04 | 仅评估已绑定落地 peer；忽略 wg0 残留；部分异常为告警非 fail |
| Tauri 桌面客户端 | ✅ | `apps/tauri` Win/Mac/Linux/iPhone monorepo；Ky UI + Mihomo + Phase 0–2；Android 见 `apps/android` |
| iOS / iPhone | 🚧 | `apps/tauri/platforms/ios`；Swift + Network Extension |

---

## 3. 后端业务模块

| 模块 | 状态 | 主要位置 |
|------|------|----------|
| 认证（注册/登录/邮箱验证码/找回密码） | ✅ | `routes/auth.go` |
| JWT 会话 / 设备 / 心跳 | ✅ | `user_session.go` |
| 会员登录有效期后台可配（默认永久） | ✅ | 2026-06-27 | `login_security.member_session_ttl_hours`；`GET /auth/session-config`；管理端安全策略页 |
| 管理员 MFA / 审计 / 登录安全 | ✅ | `mfa.go`、`audit_log.go` |
| RBAC 角色权限 | ✅ | `permission.go`、管理端路由 meta |
| 套餐 / 订单 / 订阅 | ✅ | business 路由 + 双端页面 |
| 代理分销（预存款开通会员） | 🚧 | 2026-07-16 | Phase 1+2 代码已落地：管理员代理 CRUD/充扣款、代理工作台（用户/余额/批量开通/下级订单）、数据隔离；待全链路联调验收；见 [代理分销 PRD](product/代理分销产品需求.md) |
| 会员带宽限速（套餐默认 + 订阅覆盖） | ✅ | `bandwidth_limit_mbps`；订阅头/Hysteria2 up·down + App 字段 + 节点 tc；经中转限入口；流量与限速职责分离；HTB 池化（2026-08-04）；见 [会员带宽限速 PRD](product/会员带宽限速产品需求.md) §0 |
| 订阅 token / 用量 | ✅ | `subscription*.go` |
| 节点 CRUD / 心跳 / 流量 | ✅ | `routes/node.go` |
| 节点监控 SSE | ✅ | 管理端 `/nodes/monitor` |
| 用户节点绑定 | ✅ | `user_node_binding.go` |
| 支付回调（支付宝/微信/Stripe） | 🚧 | 路由存在；真实渠道需联调 |
| USDT 充值 / 自动确认 | ✅ | 2026-06-29 | TronGrid 测试连接；三端自动模式 UI（等待确认+收起加速匹配）；状态文案统一；需生产 TronGrid 联调 |
| 余额 / 退款 / 对账 | ✅ | 财务模块；**套餐订单**在「用户与订阅」与「支付与财务」双入口；**USDT 充值**仅在财务 |
| 工单 / 公告 / 客服配置 | ✅ | 双端 |
| 运营/财务统计 | ✅ | statistics 路由 |
| App 诊断日志 | ✅ | `users.app_debug_enabled`、`app_debug_logs` |
| 数据库备份 / 定时任务 | ✅ | maintenance 路由 |
| Swagger | ✅ | `/swagger/index.html` |

---

## 4. 文档索引

| 文档 | 用途 |
|------|------|
| [文档目录.md](文档目录.md) | 核心文档导航 |
| 本文件 | **功能状态清单** |
| [product/代理分销产品需求.md](product/代理分销产品需求.md) | **代理预存款开通会员**（对标快航） |
| [product/多协议集成需求文档.md](product/多协议集成需求文档.md) | **多协议 Phase 1–3 路线与验收** |
| [product/自研App嵌入Mihomo内核产品需求.md](product/自研App嵌入Mihomo内核产品需求.md) | **客户端 Mihomo 迁移 PRD** |
| [architecture/数据库设计.md](architecture/数据库设计.md) | 表结构 |
| [product/*.md](product/) | 各模块 PRD |
| [guides/*.md](guides/) | 部署与开发指南 |
| 根目录 [README.md](../README.md) | 项目说明与快速开始 |
| 根目录 [AGENTS.md](../AGENTS.md) | AI 协作短索引（细则见 `.cursor/rules/`；非功能清单） |

---

## 5. 已知文档与代码差异（维护时注意）

| 项 | 文档说法 | 代码事实 |
|----|----------|----------|
| 中转运行时 | WireGuard 入口网关 | **L4 已移除**；relay 待机；见 [WireGuard 落地计划](product/WireGuard中转落地实施计划.md) |
| 节点 inbound | 部分旧文档写 sing-box | node-agent 仍用 sing-box 进程承载 VLESS inbound（**服务端内部实现**，与客户端 Mihomo 无关） |
| OpenVPN / WireGuard | OV 服务端已集成 node-agent | WG 原生栈；OV 生产 E2E 待验证 |
| 快速开始 | 曾独立 `guides/快速开始.md` | 已删除；见 README + 开发指南 |

---

## 6. 待办（产品/技术）

| 项 | 状态 | Phase | 备注 |
|----|------|:-----:|------|
| Mihomo 客户端统一（App/Tauri/订阅） | ✅ | **P0** | 见 [Mihomo PRD](product/自研App嵌入Mihomo内核产品需求.md) |
| 缅甸/弱网：SS 直连备用节点 | ✅ | **2** | init.sql 默认启用 SS |
| 缅甸智能选路节点池（mm→sg/hk） | 已废弃 | **2** | 客户端市场功能已移除（2026-06-22） |
| 节点场景标签运营编辑 | ✅ | **2** | 后台节点编辑 `scene_tags_manual` |
| VPN 连接质量运营看板 | ✅ | **2** | Dashboard + `GET /v1/admin/statistics/vpn-quality` |
| Tauri 客户端弱网对齐 | ✅ | **2** | Mihomo + 探测/SLOW/split/profile |
| 会员中心客户端市场与订阅类型过滤 | 已废弃 | **2** | 客户端市场已移除；sing-box 类型仍过滤 |
| 客户端连接配置（DNS/TUN 后台可配） | ✅ | | 驱动 App `/client/config`、Clash/WG/OVPN 订阅 |
| OpenVPN / WireGuard 独立栈 | 🚧 | **3** | OV 控制面+Agent 已接；独立订阅；App 不做 |
| WireGuard 中转实施 | 🚧 | — | 生产需重部署入口+落地并刷新订阅 |
| 支付/USDT 生产联调 | 🚧 | |
| OpenVPN 生产 E2E 联调 | 📋 | **3** | 见下方联调清单 |
| 曼谷 Mihomo 回国回归 | 🚧 | **P0** | `test_bangkok_cn_sites.py` 目标 ≥12/13 |
| 节点 inbound 迁移（sing-box → 待定） | 📋 | — | 仅影响 VPS 服务端；客户端已统一 Mihomo |

### OpenVPN / WireGuard 生产联调清单

1. 已有库升级：再次执行 `migrations/init.sql`（幂等，不删数据）
2. 重建节点镜像（含 openvpn）并 SSH 部署 openvpn / wireguard 节点
3. 部署日志应出现 `PHASE=check_singbox status=OK detail=openvpn process ok` 或 `sing-box process ok`
4. 后台启用 `openvpn` / `wireguard` 订阅类型
5. 会员拉订阅 → OpenVPN Connect / WireGuard 官方客户端导入并连接
6. 防火墙放行 UDP/TCP 端口（OV）或 UDP **51999**（WG 经中转，见 [WG 运维 SOP](guides/WG经中转节点部署与巡检.md)）
