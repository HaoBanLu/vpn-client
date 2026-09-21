import { describe, expect, it } from 'vitest'
import { resolveConnectHeroCopy } from '@/lib/connect-hero'

describe('resolveConnectHeroCopy', () => {
  it('connected with network down shows offline title', () => {
    const copy = resolveConnectHeroCopy({
      connectionState: 'connected',
      networkReachable: false,
      autoReconnectEnabled: true,
    })
    expect(copy.title).toBe('网络已断开')
    expect(copy.subtitle).toBe('网络恢复后将自动重连')
    expect(copy.titleTone).toBe('warning')
  })

  it('connected with failed probe still shows 已保护 (no scare title)', () => {
    const copy = resolveConnectHeroCopy({
      connectionState: 'connected',
      probeStatus: 'failed',
      effectivelyProtected: false,
    })
    expect(copy.title).toBe('已保护')
    expect(copy.connected).toBe(true)
    expect(copy.connecting).toBe(false)
  })

  it('connected with degraded probe still shows 已保护', () => {
    const copy = resolveConnectHeroCopy({
      connectionState: 'connected',
      probeStatus: 'degraded',
      tunnelLatencyMs: 800,
    })
    expect(copy.title).toBe('已保护')
    expect(copy.titleTone).toBe('success')
    expect(copy.connected).toBe(true)
    expect(copy.subtitle).toContain('响应 800ms')
  })

  it('recoveringConnection shows soft reconnect title', () => {
    const copy = resolveConnectHeroCopy({
      connectionState: 'connected',
      recoveringConnection: true,
    })
    expect(copy.title).toBe('正在重连…')
    expect(copy.connecting).toBe(true)
  })

  it('connected shows tunnel response only, not entry latency', () => {
    const copy = resolveConnectHeroCopy({
      connectionState: 'connected',
      selectedNode: '武汉',
      tunnelLatencyMs: 120,
      entryLatencyMs: 2,
    })
    expect(copy.title).toBe('已保护')
    expect(copy.subtitle).toContain('响应 120ms')
    expect(copy.subtitle).not.toContain('入口')
    expect(copy.subtitle).not.toContain('隧道')
    expect(copy.subtitle).not.toContain('延迟')
    expect(copy.connected).toBe(true)
    expect(copy.subtitle).not.toContain('武汉')
  })

  it('failed softens to 未连接 instead of 连接失败', () => {
    const copy = resolveConnectHeroCopy({
      connectionState: 'failed',
      selectedNode: '武汉',
    })
    expect(copy.title).toBe('未连接')
    expect(copy.buttonLabel).toBe('一键连接')
    expect(copy.titleTone).toBe('default')
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

  it('connecting with phase shows phase hint', () => {
    const copy = resolveConnectHeroCopy({
      connectionState: 'connecting',
      selectedNode: '杭州1',
      connectPhase: 'authorize',
    })
    expect(copy.subtitle).toContain('授权')
    expect(copy.subtitle).toContain('杭州1')
  })

  it('no subscription prompts purchase instead of one-click connect', () => {
    const copy = resolveConnectHeroCopy({
      connectionState: 'disconnected',
      emptyReason: 'no_subscription',
    })
    expect(copy.buttonLabel).toBe('购买套餐')
    expect(copy.subtitle).toContain('购买套餐')
    expect(copy.subtitle).not.toContain('一键连接')
  })

  it('load error prompts retry instead of no-subscription copy', () => {
    const copy = resolveConnectHeroCopy({
      connectionState: 'disconnected',
      emptyReason: 'load_error',
    })
    expect(copy.title).toBe('加载失败')
    expect(copy.buttonLabel).toBe('重试')
    expect(copy.subtitle).not.toContain('暂无')
  })
})
