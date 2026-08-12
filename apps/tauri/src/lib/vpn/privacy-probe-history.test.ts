import { describe, expect, it, beforeEach } from 'vitest'
import {
  appendPrivacyProbeHistory,
  clearPrivacyProbeHistory,
  loadPrivacyProbeHistory,
} from './privacy-probe-history'

describe('privacy-probe-history', () => {
  beforeEach(() => {
    clearPrivacyProbeHistory()
  })

  it('appends and caps at 10 entries', () => {
    for (let i = 0; i < 12; i += 1) {
      appendPrivacyProbeHistory({
        exitIp: `1.2.3.${i}`,
        exitIpLooksProtected: true,
        ipv6LocalActive: false,
        dnsReachable: true,
        passed: true,
      })
    }
    const list = loadPrivacyProbeHistory()
    expect(list).toHaveLength(10)
    expect(list[0]?.exitIp).toBe('1.2.3.11')
  })

  it('stores member-facing failed probe summary', () => {
    appendPrivacyProbeHistory({
      exitIp: null,
      exitIpLooksProtected: false,
      ipv6LocalActive: true,
      dnsReachable: false,
      passed: false,
    })
    const [entry] = loadPrivacyProbeHistory()
    expect(entry?.passed).toBe(false)
    expect(entry?.summary).toBe('基础检测未通过 · 可能泄露真实网络信息')
  })

  it('stores member-facing passed probe summary', () => {
    appendPrivacyProbeHistory({
      exitIp: '1.2.3.4',
      exitIpLooksProtected: true,
      ipv6LocalActive: false,
      dnsReachable: true,
      passed: true,
    })
    const [entry] = loadPrivacyProbeHistory()
    expect(entry?.summary).toBe('基础检测已通过')
  })
})
