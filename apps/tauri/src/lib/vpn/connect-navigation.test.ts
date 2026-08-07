import { describe, expect, it } from 'vitest'
import {
  shouldConnectAfterNodeSelect,
  shouldNavigateToConnectAfterNodeSelect,
  shouldNavigateToNodes,
  shouldNavigateToPackages,
} from './connect-navigation'

describe('connect-navigation', () => {
  it('navigates when node missing or smart-route tag', () => {
    expect(shouldNavigateToNodes(null)).toBe(true)
    expect(shouldNavigateToNodes('')).toBe(true)
    expect(shouldNavigateToNodes('智能选路')).toBe(true)
    expect(shouldNavigateToNodes('新加坡1')).toBe(false)
  })

  it('navigates to packages without subscription', () => {
    expect(shouldNavigateToPackages(false)).toBe(true)
    expect(shouldNavigateToPackages(true)).toBe(false)
  })

  it('connects immediately after node select when not already connected', () => {
    expect(shouldConnectAfterNodeSelect(false)).toBe(true)
    expect(shouldConnectAfterNodeSelect(true)).toBe(false)
  })

  it('always navigates to connect tab after node select', () => {
    expect(shouldNavigateToConnectAfterNodeSelect()).toBe(true)
  })
})
