import { describe, expect, it } from 'vitest'
import { displaySceneTags, shouldShowRegionLine } from '@/lib/vpn/node-list-display'

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
})
