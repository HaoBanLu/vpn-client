import { describe, expect, it, beforeEach } from 'vitest'
import {
  quietNetworkErrorToasts,
  resetNetworkErrorToastQuietForTests,
  shouldQuietNetworkErrorToast,
} from './network-error-toast'

describe('network-error-toast', () => {
  beforeEach(() => {
    resetNetworkErrorToastQuietForTests()
  })

  it('quiets toasts for a window after arming', () => {
    const t0 = 1_000_000
    quietNetworkErrorToasts(5_000, t0)
    expect(shouldQuietNetworkErrorToast(t0)).toBe(true)
    expect(shouldQuietNetworkErrorToast(t0 + 4_999)).toBe(true)
    expect(shouldQuietNetworkErrorToast(t0 + 5_000)).toBe(false)
  })
})
