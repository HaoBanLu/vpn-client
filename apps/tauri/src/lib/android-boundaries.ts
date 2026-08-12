/**
 * Android 原生客户端与 Tauri 客户端职责边界。
 * 来源：apps/android 只读审计，用于指导跨端复用与原生插件拆分。
 */

export type BoundaryLayer = 'ui' | 'api' | 'vpn' | 'platform' | 'session' | 'update'

export interface AndroidModuleBoundary {
  layer: BoundaryLayer
  androidPath: string
  tauriStrategy: 'reuse-api' | 'webview-ui' | 'android-plugin' | 'desktop-native' | 'ios-native' | 'not-applicable'
  notes: string
}

/** UI：Compose 页面，Tauri 用 Vue 重写，仅复刻交互与路由结构 */
export const ANDROID_UI_BOUNDARIES: AndroidModuleBoundary[] = [
  { layer: 'ui', androidPath: 'ui/screens/MainShell.kt', tauriStrategy: 'webview-ui', notes: '4 Tab：连接/节点/套餐/我的' },
  { layer: 'ui', androidPath: 'ui/screens/ConnectScreen.kt', tauriStrategy: 'webview-ui', notes: '连接页优先，需对接 VPN Bridge' },
  { layer: 'ui', androidPath: 'ui/screens/AuthScreens.kt', tauriStrategy: 'webview-ui', notes: '登录注册找回密码' },
  { layer: 'ui', androidPath: 'ui/screens/*.kt', tauriStrategy: 'webview-ui', notes: '充值/工单/流量/关于等二级页' },
]

/** API：Retrofit 接口，Tauri 复用 /api/v1 契约 */
export const ANDROID_API_BOUNDARIES: AndroidModuleBoundary[] = [
  { layer: 'api', androidPath: 'data/api/VpnApi.kt', tauriStrategy: 'reuse-api', notes: '全部 REST 端点，含 /client/config' },
  { layer: 'api', androidPath: 'data/api/ApiClient.kt', tauriStrategy: 'reuse-api', notes: 'JWT 拦截、Proxy.NO_PROXY' },
  { layer: 'api', androidPath: 'data/repository/AppRepository.kt', tauriStrategy: 'reuse-api', notes: '业务聚合、配置 bypass、文件上传' },
  { layer: 'api', androidPath: 'data/local/TokenStore.kt', tauriStrategy: 'reuse-api', notes: 'JWT/地区/节点偏好 → Tauri store' },
]

/** VPN：系统级隧道，必须平台原生插件，不可 WebView 替代 */
export const ANDROID_VPN_BOUNDARIES: AndroidModuleBoundary[] = [
  { layer: 'vpn', androidPath: 'vpn/VpnController.kt', tauriStrategy: 'android-plugin', notes: 'connect/disconnect/reconnect 入口' },
  { layer: 'vpn', androidPath: 'vpn/VpnTunnelService.kt', tauriStrategy: 'android-plugin', notes: 'VpnService + Mihomo TUN + 前台通知' },
  { layer: 'vpn', androidPath: 'mihomo-core/*', tauriStrategy: 'android-plugin', notes: 'CMFA core 绑定 + libbridge.so / libclash.so' },
  { layer: 'vpn', androidPath: 'vpn/mihomo/MihomoInitializer.kt', tauriStrategy: 'android-plugin', notes: 'Global + Bridge 初始化' },
  { layer: 'vpn', androidPath: 'vpn/ConnectivityProbe.kt', tauriStrategy: 'reuse-api', notes: '连接后 HTTP 探测，前端/Tauri fetch 可复刻' },
  { layer: 'vpn', androidPath: 'vpn/VpnSessionStats.kt', tauriStrategy: 'android-plugin', notes: 'TUN 流量统计' },
  { layer: 'vpn', androidPath: 'vpn/VpnConnectionBus.kt', tauriStrategy: 'android-plugin', notes: '状态总线 → Bridge 事件' },
  { layer: 'vpn', androidPath: 'vpn/AppDirectConnectStore.kt', tauriStrategy: 'android-plugin', notes: 'VpnPlugin.listInstalledApps/setDirectConnectPackages + TUN addDisallowedApplication' },
  { layer: 'vpn', androidPath: 'vpn/AlwaysOnVpnDetector.kt', tauriStrategy: 'android-plugin', notes: '系统 Always-on/lockdown 状态；产品不用自研 KS' },
  { layer: 'platform', androidPath: 'vpn/VpnBootReceiver.kt', tauriStrategy: 'android-plugin', notes: '开机自连 + StabilityPrefs 会话' },
]

/** 平台能力：通知、更新、会话 */
export const ANDROID_PLATFORM_BOUNDARIES: AndroidModuleBoundary[] = [
  { layer: 'platform', androidPath: 'AndroidManifest.xml', tauriStrategy: 'android-plugin', notes: 'VPN/FGS/通知/安装权限' },
  { layer: 'session', androidPath: 'session/SessionHeartbeatManager.kt', tauriStrategy: 'reuse-api', notes: '前后台心跳，Tauri 生命周期对接' },
  { layer: 'update', androidPath: 'update/AppUpdateInstaller.kt', tauriStrategy: 'android-plugin', notes: 'VpnPlugin.installApkUpdate + AppUpdateInstaller；桌面走 updater 插件' },
]

export const ANDROID_MIHOMO_DEPENDENCY = {
  module: ':mihomo-core',
  sourcePath: 'apps/android/mihomo-core',
  packageName: 'com.github.kr328.clash.core',
  cannotMoveToWeb: true,
  reason: 'JNI native bridge，需 VpnService.openTun 与 protect(fd)',
}

export const CONFIG_PIPELINE = {
  backendEndpoint: 'GET /api/v1/client/config',
  backendGenerator: 'internal/service/subscription_generator.go → GenerateClashForApp',
  androidConsumer: 'ConnectViewModel → AppRepository.getClientConfig → VpnController.connect',
  tauriConsumer: 'connect store → clientApi.getClientConfig(route_mode) → vpnBridge.connect',
}
