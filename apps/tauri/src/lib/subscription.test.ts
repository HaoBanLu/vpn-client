import { describe, expect, it } from 'vitest'
import { daysUntilExpiry, latencyColor, subscriptionStatusLabel } from '@/lib/subscription'

describe('subscription helpers', () => {
  it('daysUntilExpiry returns null when expires_at is missing', () => {
    expect(daysUntilExpiry(undefined)).toBeNull()
    expect(daysUntilExpiry(null)).toBeNull()
    expect(daysUntilExpiry('')).toBeNull()
  })

  it('subscriptionStatusLabel does not throw when expires_at is missing', () => {
    expect(
      subscriptionStatusLabel(
        {
          id: 1,
          package_id: 1,
          expires_at: undefined as unknown as string,
          traffic_total_gb: 100,
          traffic_used_gb: 10,
        },
        { total: 100, used: 10, remaining: 90 },
      ),
    ).toBe('使用中')
  })

  it('latencyColor uses relaxed vpn thresholds', () => {
    expect(latencyColor(487)).toBe('#4CAF50')
    expect(latencyColor(900)).toBe('#FFC107')
    expect(latencyColor(1500)).toBe('#FF6B6B')
  })
})
