import { invoke } from '@tauri-apps/api/core'

export const CLIENT_LATENCY_CONCURRENCY = 8

/**
 * 低于此值多半是同机房探针 / 模拟器噪声（常见 1–2ms）。
 * 仅用于「最快」排序加权；展示仍显示真实测速结果，避免成功测速却变成「—」。
 */
export const MIN_PLAUSIBLE_LATENCY_MS = 8

/** 展示用：任意成功测到的正数 RTT。 */
export function displayLatencyMs(ms: number | null | undefined): number | null {
  if (ms == null || !Number.isFinite(ms) || ms <= 0) return null
  return Math.round(ms)
}

/**
 * 兼容旧名：现与 display 一致（成功测速都应可展示）。
 * 「最快」排序请用 latencySortKey。
 */
export function sanitizeLatencyMs(ms: number | null | undefined): number | null {
  return displayLatencyMs(ms)
}

/** 排序键：可信延迟用原值；&lt;8ms 噪声加惩罚，避免 1ms 抢走「最快」。 */
export function latencySortKey(ms: number | null | undefined): number | null {
  const shown = displayLatencyMs(ms)
  if (shown == null) return null
  if (shown < MIN_PLAUSIBLE_LATENCY_MS) return shown + 10_000
  return shown
}

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
    return displayLatencyMs(latency)
  } catch {
    return null
  }
}

/** 本机失败则重试一次，减少偶发超时导致整行「—」。 */
export async function probeTcpLatencyWithRetry(
  host: string,
  port: number,
  timeoutMs = 5000,
): Promise<number | null> {
  const first = await probeTcpLatency(host, port, timeoutMs)
  if (first != null) return first
  return probeTcpLatency(host, port, timeoutMs)
}

/** 有本机结果就用本机；本机失败才用控制面（机房到节点，不能代表用户 RTT）。 */
export function mergeLatencyResults(serverMs: number, clientMs: number | null): number {
  const client = displayLatencyMs(clientMs)
  const server = displayLatencyMs(serverMs)
  if (client != null) return client
  if (server != null) return server
  return -1
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
