import type { NodeItem } from '@/api/client'
import { isAppConnectable } from './app-protocol-support'

const FAIL_THRESHOLD = 3

/**
 * 对齐 Android NodeFailoverMonitor.AUTO_FAILOVER_ENABLED=false：
 * 仍记录探针成败，但不自动同区切节点（弱网误切体验差）。
 */
export const AUTO_FAILOVER_ENABLED = false

let consecutiveFails = 0

export function recordProbeFailure() {
  consecutiveFails += 1
}

export function recordProbeSuccess() {
  consecutiveFails = 0
}

export function shouldNodeFailover(): boolean {
  return AUTO_FAILOVER_ENABLED && consecutiveFails >= FAIL_THRESHOLD
}

export function resetFailoverMonitor() {
  consecutiveFails = 0
}

function isOnline(node: NodeItem): boolean {
  return node.status?.toLowerCase() === 'online'
}

/** 健康探测连续失败后，在同区在线节点中选取备用节点。 */
export function pickBackupNode(
  currentNodeName: string,
  currentRegion: string | null | undefined,
  nodes: NodeItem[],
  currentNodeId?: number | null,
): NodeItem | null {
  const current = currentNodeId != null
    ? nodes.find((n) => n.id === currentNodeId) ?? nodes.find((n) => n.name === currentNodeName)
    : nodes.find((n) => n.name === currentNodeName)
  if (!current) return null

  const region =
    currentRegion?.trim() ||
    current.region?.trim() ||
    ''

  const connectable = nodes.filter(
    (node) =>
      node.id !== current.id &&
      node.name !== currentNodeName &&
      isOnline(node) &&
      isAppConnectable(node),
  )

  let candidates = region
    ? connectable.filter((n) => n.region?.toLowerCase() === region.toLowerCase())
    : connectable

  if (candidates.length === 0) {
    const country = current.country?.trim()
    candidates = country
      ? connectable.filter((n) => n.country?.toLowerCase() === country.toLowerCase())
      : connectable
  }

  if (candidates.length === 0) return null

  return candidates.reduce((best, node) => {
    const bestLatency = best.latency_ms ?? Number.MAX_SAFE_INTEGER
    const nodeLatency = node.latency_ms ?? Number.MAX_SAFE_INTEGER
    if (nodeLatency < bestLatency) return node
    if (nodeLatency > bestLatency) return best
    return node.name < best.name ? node : best
  })
}
