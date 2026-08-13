import { describe, expect, it, vi } from 'vitest'
import { waitForVpnReady } from './wait-for-vpn-ready'

describe('waitForVpnReady', () => {
  it('returns connected when status becomes connected', async () => {
    let n = 0
    const outcome = await waitForVpnReady({
      intervalMs: 1,
      timeoutMs: 1000,
      sleep: async () => {},
      getStatus: async () => {
        n += 1
        return n < 3 ? { state: 'connecting' } : { state: 'connected' }
      },
    })
    expect(outcome).toEqual({ kind: 'connected' })
    expect(n).toBe(3)
  })

  it('returns failed after connecting then failing', async () => {
    let n = 0
    const outcome = await waitForVpnReady({
      intervalMs: 1,
      timeoutMs: 1000,
      sleep: async () => {},
      getStatus: async () => {
        n += 1
        return n < 2
          ? { state: 'connecting' }
          : { state: 'failed', error: 'TUN 建立失败' }
      },
    })
    expect(outcome).toEqual({ kind: 'failed', error: 'TUN 建立失败' })
  })

  it('ignores leftover failed until connecting is observed', async () => {
    let n = 0
    const outcome = await waitForVpnReady({
      intervalMs: 1,
      timeoutMs: 1000,
      sleep: async () => {},
      getStatus: async () => {
        n += 1
        if (n === 1) return { state: 'failed', error: '上一轮失败' }
        if (n === 2) return { state: 'connecting' }
        return { state: 'connected' }
      },
    })
    expect(outcome).toEqual({ kind: 'connected' })
  })

  it('returns timeout when never settles', async () => {
    vi.useFakeTimers()
    const promise = waitForVpnReady({
      intervalMs: 100,
      timeoutMs: 250,
      getStatus: async () => ({ state: 'connecting' }),
    })
    await vi.advanceTimersByTimeAsync(400)
    await expect(promise).resolves.toEqual({ kind: 'timeout' })
    vi.useRealTimers()
  })

  it('returns cancelled when generation becomes stale', async () => {
    let current = true
    const outcome = await waitForVpnReady({
      intervalMs: 1,
      timeoutMs: 1000,
      sleep: async () => {
        current = false
      },
      isCurrent: () => current,
      getStatus: async () => ({ state: 'connecting' }),
    })
    expect(outcome).toEqual({ kind: 'cancelled' })
  })
})
