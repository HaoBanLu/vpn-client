import { describe, expect, it } from 'vitest'
import {
  RATE_WARMUP_MS,
  estimateDisplayBps,
  estimateDisplayMbps,
  estimateMbps,
  formatDisplaySpeed,
  formatSessionDuration,
  smoothTrafficRateEma,
} from './session-throughput'

describe('session-throughput', () => {
  it('formats duration under one hour as m:ss', () => {
    expect(formatSessionDuration(65_000)).toBe('1:05')
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
})
