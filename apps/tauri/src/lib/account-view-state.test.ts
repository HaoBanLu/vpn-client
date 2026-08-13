import { describe, expect, it, vi } from 'vitest'
import { resolveAccountViewState, shareInflight } from './account-view-state'

describe('resolveAccountViewState', () => {
  it('ready when subscription exists even if later refresh fails', () => {
    expect(
      resolveAccountViewState({
        loading: false,
        fetched: true,
        loadError: '连接超时',
        hasSubscription: true,
      }),
    ).toBe('ready')
  })

  it('error when fetch failed and no subscription', () => {
    expect(
      resolveAccountViewState({
        loading: false,
        fetched: false,
        loadError: '连接超时，请检查网络后重试',
        hasSubscription: false,
      }),
    ).toBe('error')
  })

  it('empty only after successful fetch with no subscription', () => {
    expect(
      resolveAccountViewState({
        loading: false,
        fetched: true,
        loadError: null,
        hasSubscription: false,
      }),
    ).toBe('empty')
  })

  it('loading before first successful fetch', () => {
    expect(
      resolveAccountViewState({
        loading: true,
        fetched: false,
        loadError: null,
        hasSubscription: false,
      }),
    ).toBe('loading')
  })

  it('timeout is never treated as no-subscription empty', () => {
    expect(
      resolveAccountViewState({
        loading: false,
        fetched: false,
        loadError: '连接超时，请检查网络后重试',
        hasSubscription: false,
      }),
    ).not.toBe('empty')
  })
})

describe('shareInflight', () => {
  it('reuses the same promise for concurrent callers', async () => {
    const holder: { current: Promise<number> | null } = { current: null }
    const run = vi.fn(async () => {
      await Promise.resolve()
      return 1
    })
    const [a, b] = await Promise.all([shareInflight(holder, run), shareInflight(holder, run)])
    expect(a).toBe(1)
    expect(b).toBe(1)
    expect(run).toHaveBeenCalledTimes(1)
    const c = await shareInflight(holder, run)
    expect(c).toBe(1)
    expect(run).toHaveBeenCalledTimes(2)
  })
})
