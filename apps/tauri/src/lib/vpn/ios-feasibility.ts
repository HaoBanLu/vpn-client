/**
 * iOS VPN 可行性评估常量（Spike 结论）。
 * 真正全局 VPN 需 Network Extension + Apple entitlement，Tauri 仅提供 Swift 插件桥接。
 */

export const IOS_VPN_FEASIBILITY = {
  feasible: 'conditional' as const,
  requiredCapabilities: [
    'com.apple.developer.networking.networkextension',
    'packet-tunnel-provider',
  ],
  requiredComponents: [
    'PacketTunnelProvider (Swift)',
    'NetworkExtension.framework',
    'Mihomo / Clash Meta iOS build',
    'App Group for extension ↔ host IPC',
  ],
  tauriRole: 'Swift Plugin 暴露 connect/disconnect/status，不替代系统 VPN 权限',
  blockers: [
    'Apple Developer Program + Network Extension entitlement 申请',
    'App Store 对 VPN 类应用审核更严格',
    '后台保活与 Extension 内存限制',
    '仓库内尚无 Mihomo iOS 产物与签名配置',
  ],
  recommendedMilestone: '桌面 + Android Tauri 稳定后，单独开 iOS spike 分支验证 TestFlight',
  spikeStatus: 'stub-plugin-created' as const,
}
