import { describe, expect, it } from 'vitest'
import {
  CONNECTION_SCENARIO,
  inferDomesticReturnFromNode,
  normalizeConnectionScenario,
  resolveConnectionConfig,
} from './connection-scenario'

describe('connection-scenario', () => {
  it('auto + cn region resolves domestic return', () => {
    const got = resolveConnectionConfig(CONNECTION_SCENARIO.AUTO, 'cn', null)
    expect(got.profile).toBe('domestic_return')
  })

  it('overseas scenario resolves overseas profile', () => {
    const got = resolveConnectionConfig(CONNECTION_SCENARIO.OVERSEAS)
    expect(got.profile).toBe('overseas_weak')
  })

  it('normalizes return-home aliases', () => {
    expect(normalizeConnectionScenario('return-home')).toBe(CONNECTION_SCENARIO.RETURN_HOME)
  })

  it('relay access mode infers domestic return', () => {
    expect(inferDomesticReturnFromNode('sg', 'relay')).toBe(true)
  })

  it('direct access mode wins over return-home scenario', () => {
    const got = resolveConnectionConfig(CONNECTION_SCENARIO.RETURN_HOME, 'sg', 'direct')
    expect(got.profile).toBe('overseas_weak')
  })

  it('relay access mode wins over overseas scenario', () => {
    const got = resolveConnectionConfig(CONNECTION_SCENARIO.OVERSEAS, 'cn', 'relay')
    expect(got.profile).toBe('domestic_return')
  })
})
