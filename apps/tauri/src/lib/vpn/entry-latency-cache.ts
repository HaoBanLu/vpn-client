/** 节点页测速结果缓存，供连接页 Hero 展示「入口 xxms」。 */
const STORAGE_KEY = 'ky_entry_latency_by_node_id'

type LatencyMap = Record<string, number>

function readMap(): LatencyMap {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw) as LatencyMap
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

export function saveEntryLatenciesByNodeId(entries: Array<{ id: number; latencyMs: number }>) {
  if (typeof sessionStorage === 'undefined') return
  const map = readMap()
  for (const item of entries) {
    if (!(item.id > 0) || !(item.latencyMs > 0)) continue
    map[String(item.id)] = item.latencyMs
  }
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(map))
}

export function getEntryLatencyMs(nodeId: number | null | undefined): number | null {
  if (!(nodeId && nodeId > 0) || typeof sessionStorage === 'undefined') return null
  const value = readMap()[String(nodeId)]
  return typeof value === 'number' && value > 0 ? value : null
}
