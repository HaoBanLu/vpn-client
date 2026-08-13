import { describe, expect, it } from 'vitest'
import { resolveSubpageBack } from './subpage-nav'

describe('resolveSubpageBack', () => {
  it('profile child goes back to Profile', () => {
    expect(
      resolveSubpageBack({
        routeName: 'Recharge',
        historyLength: 4,
      }),
    ).toEqual({ name: 'Profile' })
    expect(
      resolveSubpageBack({
        routeName: 'Orders',
        historyLength: 4,
      }),
    ).toEqual({ name: 'Profile' })
  })

  it('respects explicit backTo', () => {
    expect(
      resolveSubpageBack({
        backTo: 'Tickets',
        routeName: 'Recharge',
        historyLength: 4,
      }),
    ).toEqual({ name: 'Tickets' })
  })

  it('falls back to history when not a profile child', () => {
    expect(
      resolveSubpageBack({
        routeName: 'Login',
        historyLength: 3,
      }),
    ).toBe('history-back')
  })
})
