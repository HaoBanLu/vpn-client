import { describe, expect, it } from 'vitest'
import { resolveAccountViewState, shareInflight, withTimeout } from './account-view-state'

describe('resolveAccountViewState', () => {
  it('ready when subscription present even if still loading', () => {
    expect(
      resolveAccountViewState({
        loading: true,
        fetched: false,
        loadError: null,
        hasSubscription: true,
      }),
    ).toBe('ready')
  })

  it('empty after successful fetch without subscription', () => {
    expect(
      resolveAccountViewState({
        loading: false,
        fetched: true,
        loadError: null,
        hasSubscription: false,
      }),
    ).toBe('empty')
  })

  it('error when load failed and not loading', () => {
    expect(
      resolveAccountViewState({
        loading: false,
        fetched: false,
        loadError: '网络异常',
        hasSubscription: false,
      }),
    ).toBe('error')
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

  it('stays loading while retrying after a network error', () => {
    expect(
      resolveAccountViewState({
        loading: true,
        fetched: false,
        loadError: '网络异常',
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
    ).toBe('error')
  })
})

describe('shareInflight', () => {
  it('reuses the same promise until settled', async () => {
    const holder: { current: Promise<number> | null } = { current: null }
    let runs = 0
    const run = () => {
      runs += 1
      return Promise.resolve(runs)
    }
    const [a, b] = await Promise.all([shareInflight(holder, run), shareInflight(holder, run)])
    expect(a).toBe(1)
    expect(b).toBe(1)
    const c = await shareInflight(holder, run)
    expect(c).toBe(2)
  })
})

describe('withTimeout', () => {
  it('resolves when promise finishes in time', async () => {
    await expect(withTimeout(Promise.resolve(7), 50)).resolves.toBe(7)
  })

  it('rejects when promise exceeds deadline', async () => {
    await expect(withTimeout(new Promise(() => {}), 20, '加载超时')).rejects.toThrow('加载超时')
  })
})
