import { describe, expect, it } from 'vitest'
import { resolveProtectionStatus } from './protection-status'

describe('resolveProtectionStatus', () => {
  it('disconnected when not connected', () => {
    const view = resolveProtectionStatus({
      connected: false,
      appDirectCount: 2,
      ruleCount: 0,
    })
    expect(view.level).toBe('disconnected')
    expect(view.title).toBe('未连接')
  })

  it('degraded when bypass is enabled', () => {
    const view = resolveProtectionStatus({
      connected: true,
      appDirectCount: 3,
      ruleCount: 1,
    })
    expect(view.level).toBe('degraded')
    expect(view.summary).toContain('应用直连 3 个')
    expect(view.summary).toContain('规则直连 1 条')
  })

  it('protected when connected without bypass', () => {
    const view = resolveProtectionStatus({
      connected: true,
      appDirectCount: 0,
      ruleCount: 0,
    })
    expect(view.level).toBe('protected')
    expect(view.title).toBe('已保护')
  })
})
