import type { VpnProbeStatus } from './types'

export interface ProbeResult {
  basicOk: boolean
  overseasOk: boolean
  slow?: boolean
  latencyMs?: number
}

const BASIC_URLS = ['https://www.baidu.com', 'https://www.qq.com']
const OVERSEAS_URLS = [
  'https://www.gstatic.com/generate_204',
  'https://cp.cloudflare.com/generate_204',
]

const DEFAULT_PROBE_TIMEOUT_MS = 20_000

function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function probeUrl(url: string, timeoutMs: number): Promise<boolean> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const res = await fetch(url, {
      method: 'GET',
      signal: controller.signal,
      cache: 'no-store',
    })
    return (res.status >= 200 && res.status < 400) || res.status === 204
  } catch {
    return false
  } finally {
    clearTimeout(timer)
  }
}

async function probeAny(urls: string[], timeoutMs: number): Promise<boolean> {
  for (const url of urls) {
    if (await probeUrl(url, timeoutMs)) return true
  }
  return false
}

/** 顺序探测：国内 → 海外；与 Android ConnectivityProbe 对齐。 */
export async function probeConnectivity(timeoutMs = DEFAULT_PROBE_TIMEOUT_MS): Promise<ProbeResult> {
  const startedAt = Date.now()
  const stageTimeout = Math.max(Math.floor(timeoutMs / 2), 6000)
  const basicOk = await probeAny(BASIC_URLS, stageTimeout)
  if (!basicOk) {
    return { basicOk: false, overseasOk: false, slow: false }
  }
  await delay(300)
  const overseasOk = await probeAny(OVERSEAS_URLS, stageTimeout)
  return {
    basicOk: true,
    overseasOk,
    slow: !overseasOk,
    latencyMs: Date.now() - startedAt,
  }
}

export function normalizeProbeResult(result: ProbeResult): ProbeResult {
  const slow = result.slow ?? (result.basicOk && !result.overseasOk)
  return { ...result, slow }
}

export function probeResultToStatus(result: ProbeResult, keepConnectionOnFailure = true): VpnProbeStatus {
  const normalized = normalizeProbeResult(result)
  if (normalized.basicOk && normalized.overseasOk) return 'ok'
  if (normalized.basicOk && normalized.slow) return 'slow'
  if (normalized.basicOk) return 'limited_overseas'
  return keepConnectionOnFailure ? 'degraded' : 'failed'
}

export function probeHint(status: VpnProbeStatus): string | null {
  switch (status) {
    case 'ok':
      return '已保护，网络畅通'
    case 'slow':
      return '隧道已建立，网络较慢，可尝试自动选路或切换节点'
    case 'limited_overseas':
      return '已连接，但海外可达性受限，请尝试新加坡/香港节点'
    case 'failed':
      return '隧道已建立，网络不可用'
    case 'degraded':
      return '隧道已建立，网络质量较差，正在监测…'
    case 'probing':
      return '正在验证网络…'
    default:
      return null
  }
}
