/** 连接会话时长与吞吐速率（对齐 Android VpnSessionStats） */

/** 连接后前几秒不展示速率，避免内核残余造成虚高。 */
export const RATE_WARMUP_MS = 3_000

/** 采样间隔过短时不算速率。 */
export const MIN_SAMPLE_MS = 400

/** 300ms 内重复采样返回缓存，避免双调用偷间隔。 */
export const RATE_CACHE_MS = 300

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

/** 桌面端：用累计字节差平滑出展示速率（Android 走原生 tracker，不走这里）。 */
export function nextSmoothedDisplayBps(input: {
  previousBps: number
  deltaBytes: number
  deltaMs: number
  sessionElapsedMs: number
}): number {
  if (input.sessionElapsedMs < RATE_WARMUP_MS) return 0
  if (input.deltaMs <= 0) return Math.max(0, input.previousBps)
  const instant = estimateDisplayBps({
    deltaBytes: input.deltaBytes,
    deltaMs: input.deltaMs,
    sessionElapsedMs: input.sessionElapsedMs,
  })
  if (instant == null) return Math.max(0, input.previousBps)
  return smoothTrafficRateEma(input.previousBps, instant)
}

export interface RateSmootherState {
  prevUploadBytes: number
  prevDownloadBytes: number
  prevSampleMs: number
  uploadBps: number
  downloadBps: number
}

export function emptyRateSmoother(): RateSmootherState {
  return {
    prevUploadBytes: 0,
    prevDownloadBytes: 0,
    prevSampleMs: 0,
    uploadBps: 0,
    downloadBps: 0,
  }
}

/**
 * 对齐 Android VpnSessionStatsTracker.sampleRates：warmup / 缓存 / EMA / cap。
 * 桌面连接页用累计字节差走这里；Android 直接用原生 bps。
 */
export function advanceDisplayRates(
  state: RateSmootherState,
  sample: {
    uploadBytes: number
    downloadBytes: number
    nowMs: number
    sessionElapsedMs: number
  },
): RateSmootherState {
  if (sample.sessionElapsedMs < RATE_WARMUP_MS) {
    return {
      prevUploadBytes: sample.uploadBytes,
      prevDownloadBytes: sample.downloadBytes,
      prevSampleMs: sample.nowMs,
      uploadBps: 0,
      downloadBps: 0,
    }
  }
  if (state.prevSampleMs > 0 && sample.nowMs - state.prevSampleMs < RATE_CACHE_MS) {
    return state
  }
  if (state.prevSampleMs > 0) {
    const deltaMs = sample.nowMs - state.prevSampleMs
    if (deltaMs < MIN_SAMPLE_MS) {
      return state
    }
    return {
      prevUploadBytes: sample.uploadBytes,
      prevDownloadBytes: sample.downloadBytes,
      prevSampleMs: sample.nowMs,
      uploadBps: nextSmoothedDisplayBps({
        previousBps: state.uploadBps,
        deltaBytes: sample.uploadBytes - state.prevUploadBytes,
        deltaMs,
        sessionElapsedMs: sample.sessionElapsedMs,
      }),
      downloadBps: nextSmoothedDisplayBps({
        previousBps: state.downloadBps,
        deltaBytes: sample.downloadBytes - state.prevDownloadBytes,
        deltaMs,
        sessionElapsedMs: sample.sessionElapsedMs,
      }),
    }
  }
  return {
    prevUploadBytes: sample.uploadBytes,
    prevDownloadBytes: sample.downloadBytes,
    prevSampleMs: sample.nowMs,
    uploadBps: state.uploadBps,
    downloadBps: state.downloadBps,
  }
}

/** 对齐 Android VpnSessionStatsTracker.formatBytes */
export function formatSessionBytes(bytes: number): string {
  const n = Math.max(0, bytes)
  if (n < 1024) return `${Math.round(n)} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`
}
