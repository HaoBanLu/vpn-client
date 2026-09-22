import { describe, expect, it } from 'vitest'
import {
  displayLatencyMs,
  latencySortKey,
  mapPool,
  mergeLatencyResults,
  parseLatencyEndpoint,
  sanitizeLatencyMs,
} from './client-latency-probe'

describe('displayLatencyMs', () => {
  it('keeps any successful positive RTT for UI', () => {
    expect(displayLatencyMs(1)).toBe(1)
    expect(displayLatencyMs(7)).toBe(7)
    expect(displayLatencyMs(120.6)).toBe(121)
  })

  it('drops invalid values', () => {
    expect(displayLatencyMs(0)).toBeNull()
    expect(displayLatencyMs(-1)).toBeNull()
    expect(displayLatencyMs(undefined)).toBeNull()
  })
})

describe('sanitizeLatencyMs', () => {
  it('matches displayLatencyMs (success should be visible)', () => {
    expect(sanitizeLatencyMs(1)).toBe(1)
    expect(sanitizeLatencyMs(8)).toBe(8)
  })
})

describe('latencySortKey', () => {
  it('deprioritizes sub-8ms noise for fastest ranking', () => {
    expect(latencySortKey(2)!).toBeGreaterThan(latencySortKey(40)!)
    expect(latencySortKey(11)!).toBeLessThan(latencySortKey(36)!)
  })
})

describe('mergeLatencyResults', () => {
  it('prefers client RTT over server datacenter 1ms', () => {
    expect(mergeLatencyResults(1, 120)).toBe(120)
  })

  it('keeps low client RTT for display instead of hiding as dash', () => {
    expect(mergeLatencyResults(1, 1)).toBe(1)
    expect(mergeLatencyResults(-1, 2)).toBe(2)
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
