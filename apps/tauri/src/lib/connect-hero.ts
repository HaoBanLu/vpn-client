import type { VpnConnectionState } from '@/lib/vpn/types'

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

function formatLatencyHint(tunnelLatencyMs?: number | null, entryLatencyMs?: number | null): string {
  const parts: string[] = []
  if (entryLatencyMs && entryLatencyMs > 0) parts.push(`入口 ${entryLatencyMs}ms`)
  if (tunnelLatencyMs && tunnelLatencyMs > 0) parts.push(`隧道 ${tunnelLatencyMs}ms`)
  return parts.join(' · ')
}

/**
 * 对齐 Android ConnectHero.resolveConnectHeroCopy：
 * CONNECTED 一律「已保护」，不因 probeStatus 改主标题（主区不展示探测降级）。
 */
export function resolveConnectHeroCopy(input: {
  connectionState: VpnConnectionState
  connectPending?: boolean
  isSwitching?: boolean
  selectedNode?: string | null
  tunnelLatencyMs?: number | null
  entryLatencyMs?: number | null
}): ConnectHeroCopy {
  const {
    connectionState,
    connectPending = false,
    isSwitching = false,
    selectedNode,
    tunnelLatencyMs,
    entryLatencyMs,
  } = input

  const nodeLabel = displayNodeLabel(selectedNode) || '未选择节点'
  const latencyHint = formatLatencyHint(tunnelLatencyMs, entryLatencyMs)
  const connecting =
    connectPending || connectionState === 'connecting' || isSwitching
  const connected = connectionState === 'connected'

  if (connecting) {
    let subtitle = '正在建立加密隧道…'
    if (selectedNode?.trim()) {
      subtitle = `${isSwitching ? '正在切换至 ' : '正在连接 '}${nodeLabel}`
    } else if (connectPending && connectionState !== 'connecting') {
      subtitle = '正在准备连接…'
    }
    if (latencyHint) subtitle += ` · ${latencyHint}`

    return {
      title: isSwitching ? '切换中' : '连接中',
      subtitle,
      // 选节点/点按钮后立刻统一「连接中」，不再短暂显示「准备中」
      buttonLabel: isSwitching ? '切换中' : '连接中',
      variant: 'connecting',
      titleTone: 'info',
      connected: false,
      connecting: true,
    }
  }

  if (connected) {
    return {
      title: '已保护',
      subtitle: nodeLabel,
      buttonLabel: '断开',
      variant: 'connected',
      titleTone: 'success',
      connected: true,
      connecting: false,
    }
  }

  if (connectionState === 'failed') {
    return {
      title: '连接失败',
      subtitle: '请检查网络或切换节点',
      buttonLabel: '一键连接',
      variant: 'default',
      titleTone: 'error',
      connected: false,
      connecting: false,
    }
  }

  return {
    title: '未连接',
    subtitle: selectedNode?.trim()
      ? `已选 ${nodeLabel} · 点击下方连接`
      : '点击「一键连接」前往选择节点',
    buttonLabel: '一键连接',
    variant: 'default',
    titleTone: 'default',
    connected: false,
    connecting: false,
  }
}
