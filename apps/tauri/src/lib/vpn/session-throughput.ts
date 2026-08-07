/** 连接会话时长与吞吐速率（对齐 Android VpnSessionStats） */

/** 连接后前几秒不展示速率，避免内核残余造成虚高。 */
export const RATE_WARMUP_MS = 3_000

/** 采样间隔过短时不算速率。 */
export const MIN_SAMPLE_MS = 400

/** 展示速率上限 200 Mbps（bytes/s）。 */
export const MAX_DISPLAY_BPS = 25_000_000

/** 对齐 Android TrafficRateEma：约 3～4 次 1s 采样达稳态。 */
export const RATE_EMA_ALPHA = 0.35

/** 对齐 Android VpnSessionStatsTracker.formatDuration：`mm:ss` / `hh:mm:ss`，分秒补零 */
export function formatSessionDuration(durationMs: number): string {
  const totalSec = Math.max(0, Math.floor(durationMs / 1000))
  const hours = Math.floor(totalSec / 3600)
  const minutes = Math.floor((totalSec % 3600) / 60)
  const seconds = totalSec % 60
  const mm = String(minutes).padStart(2, '0')
  const ss = String(seconds).padStart(2, '0')
  if (hours > 0) {
    return `${String(hours).padStart(2, '0')}:${mm}:${ss}`
  }
  return `${mm}:${ss}`
}

/**
 * 对齐 Android VpnSessionStatsTracker.formatSpeed：
 * 空闲显示 `0.0 KB/s`，禁止用 `—` 造成「坏了」的观感。
 */
export function formatDisplaySpeed(bytesPerSecond: number): string {
  const bytes = Math.max(0, bytesPerSecond)
  const bitsPerSecond = bytes * 8
  if (bitsPerSecond >= 1_000_000) {
    return `${(bitsPerSecond / 1_000_000).toFixed(1)} Mbps`
  }
  return `${(bytes / 1024).toFixed(1)} KB/s`
}

/** 指数滑动平均，减轻 0 ↔ 跳变。 */
export function smoothTrafficRateEma(
  previousBps: number,
  instantBps: number,
  alpha = RATE_EMA_ALPHA,
): number {
  const safeInstant = Math.max(0, instantBps)
  if (previousBps <= 0) return safeInstant
  return Math.max(0, alpha * safeInstant + (1 - alpha) * previousBps)
}

/** 根据字节增量估算 Mbps（无护栏，供测试与底层换算）。 */
export function estimateMbps(deltaBytes: number, deltaMs: number): number {
  if (deltaMs <= 0 || deltaBytes <= 0) return 0
  return (deltaBytes * 8) / (deltaMs / 1000) / 1_000_000
}

export function bpsToMbps(bps: number): number {
  if (bps <= 0) return 0
  return (bps * 8) / 1_000_000
}

export interface ThroughputSampleInput {
  deltaBytes: number
  deltaMs: number
  /** 自本次 connect/reset 起经过的毫秒数 */
  sessionElapsedMs: number
}

/**
 * 对齐 Android：warmup 内返回 0；间隔过短返回 0；超上限视为脏采样返回 0（保留上次 EMA）。
 * 返回 bytes/s。
 */
export function estimateDisplayBps(input: ThroughputSampleInput): number | null {
  const { deltaBytes, deltaMs, sessionElapsedMs } = input
  if (sessionElapsedMs < RATE_WARMUP_MS) return 0
  if (deltaMs < MIN_SAMPLE_MS) return null
  const safeDelta = Math.max(0, deltaBytes)
  const bps = (safeDelta * 1000) / deltaMs
  if (bps > MAX_DISPLAY_BPS) return null
  return Math.min(bps, MAX_DISPLAY_BPS)
}

/**
 * 对齐 Android：warmup 内返回 0；间隔过短返回 0；超上限视为脏采样返回 0。
 */
export function estimateDisplayMbps(input: ThroughputSampleInput): number {
  const bps = estimateDisplayBps(input)
  if (bps == null || bps <= 0) return 0
  return bpsToMbps(bps)
}
