import { describe, expect, it } from 'vitest'
import { poolLabel, scenarioMismatchHint } from './node-access-hint'
import { CONNECTION_SCENARIO } from './connection-scenario'

describe('node-access-hint', () => {
  it('labels relay/direct pools', () => {
    expect(poolLabel('relay')).toBe('回国专线')
    expect(poolLabel('direct')).toBe('海外直连')
    expect(poolLabel('other')).toBeNull()
  })

  it('warns when overseas scenario picks relay node', () => {
    const hint = scenarioMismatchHint(CONNECTION_SCENARIO.OVERSEAS, 'relay')
    expect(hint).toContain('回国专线')
    expect(hint).toContain('海外直连')
  })

  it('warns when return-home scenario picks direct node', () => {
    const hint = scenarioMismatchHint(CONNECTION_SCENARIO.RETURN_HOME, 'direct')
    expect(hint).toContain('回国加速')
    expect(hint).toContain('武汉')
  })

  it('no tip when auto or matched', () => {
    expect(scenarioMismatchHint(CONNECTION_SCENARIO.AUTO, 'direct')).toBeNull()
    expect(scenarioMismatchHint(CONNECTION_SCENARIO.OVERSEAS, 'direct')).toBeNull()
    expect(scenarioMismatchHint(CONNECTION_SCENARIO.RETURN_HOME, 'relay')).toBeNull()
  })
})
