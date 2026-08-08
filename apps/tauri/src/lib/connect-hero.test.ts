import { describe, expect, it } from 'vitest'
import { resolveConnectHeroCopy } from '@/lib/connect-hero'

describe('resolveConnectHeroCopy', () => {
  it('connected shows 已保护 without repeating node in subtitle', () => {
    const copy = resolveConnectHeroCopy({
      connectionState: 'connected',
      selectedNode: '武汉',
      tunnelLatencyMs: 120,
      entryLatencyMs: 2,
    })
    expect(copy.title).toBe('已保护')
    expect(copy.subtitle).toContain('入口 2ms')
    expect(copy.subtitle).toContain('隧道 120ms')
    expect(copy.connected).toBe(true)
    expect(copy.subtitle).not.toContain('武汉')
  })

  it('failed shows 连接失败', () => {
    const copy = resolveConnectHeroCopy({
      connectionState: 'failed',
      selectedNode: '武汉',
    })
    expect(copy.title).toBe('连接失败')
    expect(copy.buttonLabel).toBe('一键连接')
  })

  it('connecting shows tunnel subtitle without cancel hint', () => {
    const copy = resolveConnectHeroCopy({
      connectionState: 'connecting',
      selectedNode: '杭州1',
    })
    expect(copy.title).toBe('连接中')
    expect(copy.connecting).toBe(true)
    expect(copy.buttonLabel).toBe('连接中')
    expect(copy.subtitle).toContain('正在连接 杭州1')
    expect(copy.subtitle).not.toContain('再点可取消')
  })

  it('connectPending alone shows 连接中 before connectionState flips', () => {
    const copy = resolveConnectHeroCopy({
      connectionState: 'disconnected',
      connectPending: true,
      selectedNode: '新加坡2',
    })
    expect(copy.title).toBe('连接中')
    expect(copy.buttonLabel).toBe('连接中')
    expect(copy.connecting).toBe(true)
    expect(copy.subtitle).toContain('正在连接 新加坡2')
  })

  it('idle with node prompts clicking the button', () => {
    const copy = resolveConnectHeroCopy({
      connectionState: 'disconnected',
      selectedNode: '新加坡2',
    })
    expect(copy.buttonLabel).toBe('一键连接')
    expect(copy.subtitle).toContain('点击下方连接')
  })
})
