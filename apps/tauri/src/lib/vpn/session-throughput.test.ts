import { describe, expect, it } from 'vitest'
import {
  RATE_CACHE_MS,
  RATE_WARMUP_MS,
  advanceDisplayRates,
  emptyRateSmoother,
  estimateDisplayBps,
  estimateDisplayMbps,
  estimateMbps,
  formatDisplaySpeed,
  formatSessionBytes,
  formatSessionDuration,
  nextSmoothedDisplayBps,
  smoothTrafficRateEma,
} from './session-throughput'

describe('session-throughput', () => {
  it('formats duration under one hour as mm:ss (Android padded)', () => {
    expect(formatSessionDuration(65_000)).toBe('01:05')
    expect(formatSessionDuration(125_000)).toBe('02:05')
  })

  it('estimates mbps from byte delta', () => {
    expect(estimateMbps(1_000_000, 1000)).toBeCloseTo(8, 1)
  })

  it('formatDisplaySpeed never uses em dash for idle', () => {
    expect(formatDisplaySpeed(0)).toBe('0.0 KB/s')
    expect(formatDisplaySpeed(5 * 1024)).toBe('5.0 KB/s')
    expect(formatDisplaySpeed(2 * 1024 * 1024)).toBe('16.8 Mbps')
  })

  it('smooths rates toward zero instead of hard reset', () => {
    const next = smoothTrafficRateEma(1000, 0, 0.35)
    expect(next).toBeGreaterThan(0)
    expect(next).toBeLessThan(1000)
  })

  it('returns 0 during warmup', () => {
    expect(
      estimateDisplayMbps({
        deltaBytes: 5 * 1024 * 1024,
        deltaMs: 1000,
        sessionElapsedMs: RATE_WARMUP_MS - 1,
      }),
    ).toBe(0)
    expect(
      estimateDisplayBps({
        deltaBytes: 5 * 1024 * 1024,
        deltaMs: 1000,
        sessionElapsedMs: RATE_WARMUP_MS - 1,
      }),
    ).toBe(0)
  })

  it('returns 0 when sample interval is too short', () => {
    expect(
      estimateDisplayMbps({
        deltaBytes: 200 * 1024,
        deltaMs: 100,
        sessionElapsedMs: RATE_WARMUP_MS + 500,
      }),
    ).toBe(0)
    expect(
      estimateDisplayBps({
        deltaBytes: 200 * 1024,
        deltaMs: 100,
        sessionElapsedMs: RATE_WARMUP_MS + 500,
      }),
    ).toBeNull()
  })

  it('caps / rejects absurd spikes above 200 Mbps', () => {
    // ~800 Mbps raw → dirty sample → 0
    expect(
      estimateDisplayMbps({
        deltaBytes: 100 * 1024 * 1024,
        deltaMs: 1000,
        sessionElapsedMs: RATE_WARMUP_MS + 500,
      }),
    ).toBe(0)
    expect(
      estimateDisplayBps({
        deltaBytes: 100 * 1024 * 1024,
        deltaMs: 1000,
        sessionElapsedMs: RATE_WARMUP_MS + 500,
      }),
    ).toBeNull()
  })

  it('returns plausible rate after warmup', () => {
    // 2.5 MB in 1s ≈ 20 Mbps
    const mbps = estimateDisplayMbps({
      deltaBytes: 2.5 * 1024 * 1024,
      deltaMs: 1000,
      sessionElapsedMs: RATE_WARMUP_MS + 500,
    })
    expect(mbps).toBeGreaterThan(19)
    expect(mbps).toBeLessThan(21)
  })

  it('formatDisplaySpeed uses decimal Mbps at 1e6 bit/s', () => {
    expect(formatDisplaySpeed(124_999)).toMatch(/KB\/s$/)
    expect(formatDisplaySpeed(125_000)).toBe('1.0 Mbps')
  })

  it('formatSessionBytes matches Android thresholds', () => {
    expect(formatSessionBytes(800)).toBe('800 B')
    expect(formatSessionBytes(2048)).toBe('2.0 KB')
    expect(formatSessionBytes(1.5 * 1024 * 1024)).toBe('1.5 MB')
    expect(formatSessionBytes(2 * 1024 * 1024 * 1024)).toBe('2.00 GB')
  })

  it('nextSmoothedDisplayBps stays 0 during warmup and keeps last on dirty sample', () => {
    expect(
      nextSmoothedDisplayBps({
        previousBps: 1000,
        deltaBytes: 5 * 1024 * 1024,
        deltaMs: 1000,
        sessionElapsedMs: RATE_WARMUP_MS - 1,
      }),
    ).toBe(0)
    expect(
      nextSmoothedDisplayBps({
        previousBps: 12_000,
        deltaBytes: 100 * 1024 * 1024,
        deltaMs: 1000,
        sessionElapsedMs: RATE_WARMUP_MS + 500,
      }),
    ).toBe(12_000)
  })

  it('advanceDisplayRates zeros during warmup and does not steal a 200ms second sample', () => {
    let state = emptyRateSmoother()
    state = advanceDisplayRates(state, {
      uploadBytes: 50_000,
      downloadBytes: 80_000,
      nowMs: 1_000,
      sessionElapsedMs: 1_000,
    })
    expect(state.uploadBps).toBe(0)
    expect(state.downloadBps).toBe(0)

    state = advanceDisplayRates(state, {
      uploadBytes: 50_000,
      downloadBytes: 80_000,
      nowMs: 4_000,
      sessionElapsedMs: 4_000,
    })
    state = advanceDisplayRates(state, {
      uploadBytes: 50_000 + 100_000,
      downloadBytes: 80_000 + 200_000,
      nowMs: 5_000,
      sessionElapsedMs: 5_000,
    })
    expect(state.downloadBps).toBeGreaterThan(0)
    expect(state.uploadBps).toBeGreaterThan(0)
    const afterTick = { ...state }

    state = advanceDisplayRates(state, {
      uploadBytes: afterTick.prevUploadBytes + 90_000,
      downloadBytes: afterTick.prevDownloadBytes + 180_000,
      nowMs: afterTick.prevSampleMs + 200,
      sessionElapsedMs: 5_200,
    })
    expect(state).toEqual(afterTick)
    expect(200).toBeLessThan(RATE_CACHE_MS)
  })
})
