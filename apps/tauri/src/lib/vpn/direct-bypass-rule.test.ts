import { describe, expect, it } from 'vitest'
import { injectDirectBypassRules, validateDirectBypassRule } from './direct-bypass-rule'

describe('direct-bypass-rule', () => {
  it('validates domain suffix', () => {
    expect(validateDirectBypassRule('DOMAIN_SUFFIX', 'example.com')).toBe('example.com')
  })

  it('injects rules before MATCH', () => {
    const yaml = `rules:
- MATCH,PROXY
`
    const patched = injectDirectBypassRules(yaml, [
      { id: '1', type: 'DOMAIN_SUFFIX', value: 'example.com', enabled: true },
    ])
    expect(patched).toContain('DOMAIN-SUFFIX,example.com,DIRECT')
    expect(patched.indexOf('DIRECT')).toBeLessThan(patched.indexOf('MATCH'))
  })
})
