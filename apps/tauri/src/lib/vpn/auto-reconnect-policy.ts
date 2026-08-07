/** 桌面端自动重连退避策略，对齐 Android VpnAutoReconnectPolicy */

export const AUTO_RECONNECT_POLICY = {
  maxAttempts: 3,
  backoffMs: [3000, 6000, 10000] as const,
  periodicHealthProbeMs: 120_000,
  degradedHealthProbeMs: 60_000,
  /** 历史字段：桌面端不再因探测降级主动断开 */
  dataplaneDegradedDisconnectMs: 0,
} as const

export function autoReconnectBackoffMs(attemptIndex: number): number {
  const idx = Math.max(0, attemptIndex)
  const delays = AUTO_RECONNECT_POLICY.backoffMs
  return delays[Math.min(idx, delays.length - 1)]
}
