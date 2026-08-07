import { describe, expect, it } from 'vitest'
import { evaluatePrivacyLeakProbe } from './privacy-leak-probe'

describe('privacy-leak-probe', () => {
  it('passes when exit differs from baseline and dns ok', () => {
    const result = evaluatePrivacyLeakProbe({
      exitIp: '203.0.113.1',
      baselineIp: '198.51.100.2',
      ipv6LocalActive: false,
      dnsReachable: true,
    })
    expect(result.passed).toBe(true)
  })

  it('fails when exit matches baseline', () => {
    const result = evaluatePrivacyLeakProbe({
      exitIp: '198.51.100.2',
      baselineIp: '198.51.100.2',
      ipv6LocalActive: false,
      dnsReachable: true,
    })
    expect(result.passed).toBe(false)
  })
})
