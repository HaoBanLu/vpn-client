import { describe, expect, it } from 'vitest'
import { connectPhaseLabel } from './connect-phase'

describe('connectPhaseLabel', () => {
  it('returns labels for active phases', () => {
    expect(connectPhaseLabel('config')).toContain('配置')
    expect(connectPhaseLabel('authorize')).toContain('授权')
    expect(connectPhaseLabel('tunnel')).toContain('建立')
    expect(connectPhaseLabel('tunnel', { isSwitching: true })).toContain('切换')
    expect(connectPhaseLabel('verify')).toContain('确认')
  })

  it('idle returns null', () => {
    expect(connectPhaseLabel('idle')).toBeNull()
  })
})
