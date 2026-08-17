import { invoke } from '@tauri-apps/api/core'

export const CLIENT_LATENCY_CONCURRENCY = 8

export function parseLatencyEndpoint(endpoint?: string | null): { host: string; port: number } | null {
  const raw = endpoint?.trim() ?? ''
  if (!raw) return null
  const idx = raw.lastIndexOf(':')
  if (idx <= 0 || idx >= raw.length - 1) return null
  const host = raw.slice(0, idx).trim()
  const port = Number.parseInt(raw.slice(idx + 1).trim(), 10)
  if (!host || !Number.isFinite(port) || port <= 0) return null
  return { host, port }
}

export async function probeTcpLatency(
  host: string,
  port: number,
  timeoutMs = 5000,
): Promise<number | null> {
  try {
    const latency = await invoke<number | null>('tcp_connect_latency', {
      host,
      port,
      timeoutMs,
    })
    return latency != null && latency >= 0 ? latency : null
  } catch {
    return null
  }
}

/** 有本机结果就用本机；本机失败才用控制面（机房到节点，不能代表用户 RTT）。 */
export function mergeLatencyResults(serverMs: number, clientMs: number | null): number {
  const server = serverMs > 0 ? serverMs : -1
  const client = clientMs != null && clientMs > 0 ? clientMs : -1
  if (client > 0) return client
  return server
}

export async function mapPool<T, R>(
  items: T[],
  concurrency: number,
  mapper: (item: T, index: number) => Promise<R>,
): Promise<R[]> {
  if (items.length === 0) return []
  const results: R[] = new Array(items.length)
  let next = 0
  const limit = Math.min(Math.max(1, concurrency), items.length)
  async function worker() {
    while (next < items.length) {
      const index = next
      next += 1
      results[index] = await mapper(items[index], index)
    }
  }
  await Promise.all(Array.from({ length: limit }, () => worker()))
  return results
}
