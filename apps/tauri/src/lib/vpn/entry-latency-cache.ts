/** 节点页测速结果缓存，供连接页 Hero 展示「入口 xxms」。 */
const STORAGE_KEY = 'ky_entry_latency_by_node'

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

export function saveEntryLatenciesByNodeName(entries: Array<{ name: string; latencyMs: number }>) {
  if (typeof sessionStorage === 'undefined') return
  const map = readMap()
  for (const item of entries) {
    const name = item.name?.trim()
    if (!name || !(item.latencyMs > 0)) continue
    map[name] = item.latencyMs
  }
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(map))
}

export function getEntryLatencyMs(nodeName: string | null | undefined): number | null {
  if (!nodeName?.trim() || typeof sessionStorage === 'undefined') return null
  const value = readMap()[nodeName.trim()]
  return typeof value === 'number' && value > 0 ? value : null
}
