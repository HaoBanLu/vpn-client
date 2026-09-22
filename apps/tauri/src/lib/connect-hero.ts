import type { VpnConnectionState, VpnProbeStatus } from '@/lib/vpn/types'
import type { ConnectPhase } from '@/lib/vpn/connect-phase'
import { connectPhaseLabel } from '@/lib/vpn/connect-phase'

export interface ConnectHeroCopy {
  title: string
  subtitle: string
  buttonLabel: string
  variant: 'default' | 'connected' | 'connecting'
  titleTone: 'default' | 'success' | 'warning' | 'error' | 'info'
  connected: boolean
  connecting: boolean
}

export function displayNodeLabel(node: string | null | undefined): string {
  const trimmed = node?.trim()
  if (!trimmed) return ''
  return trimmed
}

/**
 * 连接页探针文案：只展示经隧道的响应时间。
 * 不用「延迟」一词；也不展示「入口」TCP 探测（常被机房/模拟器测成 1ms）。
 */
function formatLatencyHint(tunnelLatencyMs?: number | null, _entryLatencyMs?: number | null): string {
  void _entryLatencyMs
  if (tunnelLatencyMs && tunnelLatencyMs > 0) return `响应 ${tunnelLatencyMs}ms`
  return ''
}

/**
 * 连接页 Hero 文案。
 * 隧道已建立时主标题固定「已保护」，不因探针/高延迟改成「不稳定」「失败」吓人话术。
 */
export function resolveConnectHeroCopy(input: {
  connectionState: VpnConnectionState
  connectPending?: boolean
  isSwitching?: boolean
  selectedNode?: string | null
  tunnelLatencyMs?: number | null
  entryLatencyMs?: number | null
  connectPhase?: ConnectPhase
  emptyReason?: 'no_subscription' | 'load_error'
  probeStatus?: VpnProbeStatus
  recoveringConnection?: boolean
  effectivelyProtected?: boolean
  networkReachable?: boolean
  autoReconnectEnabled?: boolean
}): ConnectHeroCopy {
  const {
    connectionState,
    connectPending = false,
    isSwitching = false,
    selectedNode,
    tunnelLatencyMs,
    entryLatencyMs,
    connectPhase = 'idle',
    emptyReason,
    probeStatus: _probeStatus = 'idle',
    recoveringConnection = false,
    effectivelyProtected: _effectivelyProtected,
    networkReachable = true,
    autoReconnectEnabled = true,
  } = input
  void _probeStatus
  void _effectivelyProtected

  if (emptyReason === 'load_error') {
    return {
      title: '加载失败',
      subtitle: '请检查网络后重试',
      buttonLabel: '重新加载',
      variant: 'default',
      titleTone: 'error',
      connected: false,
      connecting: false,
    }
  }

  if (emptyReason === 'no_subscription') {
    return {
      title: '未连接',
      subtitle: '购买套餐后即可使用加速',
      buttonLabel: '购买套餐',
      variant: 'default',
      titleTone: 'default',
      connected: false,
      connecting: false,
    }
  }

  const nodeLabel = displayNodeLabel(selectedNode) || '未选择节点'
  const latencyHint = formatLatencyHint(tunnelLatencyMs, entryLatencyMs)
  const connecting =
    connectPending || connectionState === 'connecting' || isSwitching
  const connected = connectionState === 'connected'

  if (connecting) {
    const phaseHint = connectPhaseLabel(connectPhase, { isSwitching })
    let subtitle = phaseHint ?? '正在建立加密隧道…'
    if (selectedNode?.trim() && !phaseHint) {
      subtitle = `${isSwitching ? '正在切换至 ' : '正在连接 '}${nodeLabel}`
    } else if (connectPending && connectionState !== 'connecting' && !phaseHint) {
      subtitle = '正在准备连接…'
    } else if (selectedNode?.trim() && phaseHint) {
      subtitle = `${phaseHint} · ${nodeLabel}`
    }
    if (latencyHint) subtitle += ` · ${latencyHint}`

    return {
      title: isSwitching ? '切换中' : '连接中',
      subtitle,
      // 选节点/点按钮后立刻统一「连接中」，不再短暂显示「准备中」
      buttonLabel: isSwitching ? '正在切换' : '正在连接',
      variant: 'connecting',
      titleTone: 'info',
      connected: false,
      connecting: true,
    }
  }

  if (connected) {
    // 仅真正在自动重连流程中才显示恢复态；探针差不改主标题（避免「软件不稳」观感）
    if (recoveringConnection) {
      return {
        title: '正在重连…',
        subtitle: latencyHint || '请稍候',
        buttonLabel: '正在重连',
        variant: 'connecting',
        titleTone: 'info',
        connected: false,
        connecting: true,
      }
    }
    if (!networkReachable) {
      return {
        title: '网络已断开',
        subtitle: autoReconnectEnabled ? '网络恢复后将自动重连' : '请检查网络后手动重连',
        buttonLabel: '断开连接',
        variant: 'connected',
        titleTone: 'warning',
        connected: true,
        connecting: false,
      }
    }
    // 隧道已建立：统一「已保护」。延迟/探针质量只放副标题，不喊「不稳定」
    return {
      title: '已保护',
      subtitle: latencyHint || '',
      buttonLabel: '断开连接',
      variant: 'connected',
      titleTone: 'success',
      connected: true,
      connecting: false,
    }
  }

  if (connectionState === 'failed') {
    return {
      title: '未连接',
      subtitle: '请稍后重试，或换一个节点',
      buttonLabel: '一键连接',
      variant: 'default',
      titleTone: 'default',
      connected: false,
      connecting: false,
    }
  }

  return {
    title: '未连接',
    subtitle: selectedNode?.trim()
      ? `已选 ${nodeLabel} · 点击下方连接`
      : '未选节点时将按地区自动选路',
    buttonLabel: '一键连接',
    variant: 'default',
    titleTone: 'default',
    connected: false,
    connecting: false,
  }
}
