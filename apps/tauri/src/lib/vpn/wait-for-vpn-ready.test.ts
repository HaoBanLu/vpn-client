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

  it('returns failed with error message', async () => {
    const outcome = await waitForVpnReady({
      intervalMs: 1,
      timeoutMs: 1000,
      sleep: async () => {},
      getStatus: async () => ({ state: 'failed', error: 'TUN 建立失败' }),
    })
    expect(outcome).toEqual({ kind: 'failed', error: 'TUN 建立失败' })
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
