/** 连接过程阶段（仅 connecting 态展示，connected 后不再显示）。 */
export type ConnectPhase = 'idle' | 'config' | 'authorize' | 'tunnel' | 'verify'

export function connectPhaseLabel(
  phase: ConnectPhase,
  options?: { isSwitching?: boolean },
): string | null {
  switch (phase) {
    case 'config':
      return '正在获取连接配置…'
    case 'authorize':
      return '等待 VPN 授权…'
    case 'tunnel':
      return options?.isSwitching ? '正在切换 VPN 隧道…' : '正在建立 VPN 隧道…'
    case 'verify':
      return '正在确认连接状态…'
    default:
      return null
  }
}
