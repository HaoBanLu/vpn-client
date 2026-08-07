import { invoke } from '@tauri-apps/api/core'

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

export function mergeLatencyResults(serverMs: number, clientMs: number | null): number {
  const server = serverMs > 0 ? serverMs : -1
  const client = clientMs != null && clientMs > 0 ? clientMs : -1
  if (server > 0 && client > 0) return Math.min(server, client)
  if (client > 0) return client
  return server
}
