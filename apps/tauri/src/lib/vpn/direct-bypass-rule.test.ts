import { describe, expect, it } from 'vitest'
import {
  controlPlaneBypassRules,
  injectDirectBypassRules,
  validateDirectBypassRule,
} from './direct-bypass-rule'

describe('direct-bypass-rule', () => {
  it('validates domain suffix', () => {
    expect(validateDirectBypassRule('DOMAIN_SUFFIX', 'example.com')).toBe('example.com')
  })

  it('builds control-plane DIRECT rule from API base', () => {
    const rules = controlPlaneBypassRules('http://192.229.87.112:44080/api')
    expect(rules).toEqual([
      { id: 'control-plane-api', type: 'IP_CIDR', value: '192.229.87.112/32', enabled: true },
    ])
  })

  it('injects control-plane + user rules before MATCH', () => {
    const yaml = `rules:
- MATCH,PROXY
`
    const patched = injectDirectBypassRules(yaml, [
      { id: '1', type: 'DOMAIN_SUFFIX', value: 'example.com', enabled: true },
    ])
    expect(patched).toContain('IP-CIDR,192.229.87.112/32,DIRECT,no-resolve')
    expect(patched).toContain('DOMAIN-SUFFIX,example.com,DIRECT')
    expect(patched.indexOf('DIRECT')).toBeLessThan(patched.indexOf('MATCH'))
  })
})
