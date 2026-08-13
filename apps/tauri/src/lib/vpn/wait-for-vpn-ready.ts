/** 轮询原生 VPN 状态，直到 settled（connected/failed）或超时。 */
export type VpnReadyStatus = {
  state: 'connected' | 'connecting' | 'disconnected' | 'failed' | string
  error?: string | null
}

export type VpnReadyOutcome =
  | { kind: 'connected' }
  | { kind: 'failed'; error: string }
  | { kind: 'timeout' }
  | { kind: 'cancelled' }

export async function waitForVpnReady(options: {
  getStatus: () => Promise<VpnReadyStatus>
  isCurrent?: () => boolean
  timeoutMs?: number
  intervalMs?: number
  sleep?: (ms: number) => Promise<void>
}): Promise<VpnReadyOutcome> {
  const timeoutMs = options.timeoutMs ?? 25_000
  const intervalMs = options.intervalMs ?? 400
  const sleep = options.sleep ?? ((ms: number) => new Promise((r) => setTimeout(r, ms)))
  const deadline = Date.now() + timeoutMs
  let seenConnecting = false

  while (Date.now() < deadline) {
    if (options.isCurrent && !options.isCurrent()) {
      return { kind: 'cancelled' }
    }
    const status = await options.getStatus()
    if (options.isCurrent && !options.isCurrent()) {
      return { kind: 'cancelled' }
    }
    if (status.state === 'connected') {
      return { kind: 'connected' }
    }
    if (status.state === 'connecting') {
      seenConnecting = true
    }
    // 上一轮残留的 failed 会在服务尚未切到 connecting 时被读到，必须忽略
    if (status.state === 'failed' && seenConnecting) {
      return { kind: 'failed', error: status.error?.trim() || '连接失败' }
    }
    await sleep(intervalMs)
  }
  return { kind: 'timeout' }
}
