import { describe, expect, it } from 'vitest'
import { mapPool, mergeLatencyResults, parseLatencyEndpoint } from './client-latency-probe'

describe('mergeLatencyResults', () => {
  it('prefers client RTT over server datacenter 1ms', () => {
    expect(mergeLatencyResults(1, 120)).toBe(120)
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
