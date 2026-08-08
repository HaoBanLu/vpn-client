import { describe, expect, it } from 'vitest'
import {
  displaySceneTags,
  findFastestNodeId,
  shouldShowRegionLine,
  sortNodesByLatency,
} from '@/lib/vpn/node-list-display'

describe('node-list-display', () => {
  it('hides region line when filter matches node region', () => {
    expect(shouldShowRegionLine('sg', 'sg')).toBe(false)
    expect(shouldShowRegionLine(null, 'sg')).toBe(true)
    expect(shouldShowRegionLine('sg', 'jp')).toBe(true)
  })

  it('hides 适合回国 when filtering cn', () => {
    expect(displaySceneTags(['适合海外视频', '适合回国'], 'cn')).toEqual(['适合海外视频'])
    expect(displaySceneTags(['适合回国'], 'sg')).toEqual(['适合回国'])
  })

  it('sorts by latency and keeps unmeasured at end', () => {
    const nodes = [{ id: 1 }, { id: 2 }, { id: 3 }]
    const sorted = sortNodesByLatency(nodes, { 1: 80, 3: 40 })
    expect(sorted.map((n) => n.id)).toEqual([3, 1, 2])
  })

  it('finds fastest measured node', () => {
    expect(findFastestNodeId([{ id: 1 }, { id: 2 }], { 1: 90, 2: 50 })).toBe(2)
    expect(findFastestNodeId([{ id: 1 }], {})).toBeNull()
  })
})
