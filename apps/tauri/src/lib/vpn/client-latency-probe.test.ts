import { describe, expect, it } from 'vitest'
import {
  mapPool,
  mergeLatencyResults,
  parseLatencyEndpoint,
  sanitizeLatencyMs,
} from './client-latency-probe'

describe('sanitizeLatencyMs', () => {
  it('drops datacenter/emulator noise like 1–2ms', () => {
    expect(sanitizeLatencyMs(1)).toBeNull()
    expect(sanitizeLatencyMs(2)).toBeNull()
    expect(sanitizeLatencyMs(7)).toBeNull()
  })

  it('keeps plausible user RTT', () => {
    expect(sanitizeLatencyMs(8)).toBe(8)
    expect(sanitizeLatencyMs(120.6)).toBe(121)
  })
})

describe('mergeLatencyResults', () => {
  it('prefers client RTT over server datacenter 1ms', () => {
    expect(mergeLatencyResults(1, 120)).toBe(120)
  })

  it('drops implausible client noise instead of showing 1ms', () => {
    expect(mergeLatencyResults(1, 1)).toBe(-1)
    expect(mergeLatencyResults(-1, 2)).toBe(-1)
  })

  it('uses client when server failed', () => {
    expect(mergeLatencyResults(-1, 80)).toBe(80)
  })

  it('falls back to server when client failed', () => {
    expect(mergeLatencyResults(12, null)).toBe(12)
    expect(mergeLatencyResults(12, -1)).toBe(12)
  })
})

describe('parseLatencyEndpoint', () => {
  it('parses host:port', () => {
    expect(parseLatencyEndpoint('1.2.3.4:443')).toEqual({ host: '1.2.3.4', port: 443 })
  })

  it('rejects empty', () => {
    expect(parseLatencyEndpoint('')).toBeNull()
    expect(parseLatencyEndpoint('no-port')).toBeNull()
  })
})

describe('mapPool', () => {
  it('keeps order with limited concurrency', async () => {
    const seen: number[] = []
    let inflight = 0
    let maxInflight = 0
    const out = await mapPool([1, 2, 3, 4, 5], 2, async (item) => {
      inflight += 1
      maxInflight = Math.max(maxInflight, inflight)
      seen.push(item)
      await Promise.resolve()
      inflight -= 1
      return item * 10
    })
    expect(out).toEqual([10, 20, 30, 40, 50])
    expect(maxInflight).toBeLessThanOrEqual(2)
    expect(seen.sort((a, b) => a - b)).toEqual([1, 2, 3, 4, 5])
  })
})
