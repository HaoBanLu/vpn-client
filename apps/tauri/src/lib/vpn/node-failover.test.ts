import { describe, expect, it } from 'vitest'
import {
  AUTO_FAILOVER_ENABLED,
  pickBackupNode,
  recordProbeFailure,
  resetFailoverMonitor,
  shouldNodeFailover,
} from './node-failover'
import type { NodeItem } from '@/api/client'

const nodes: NodeItem[] = [
  { id: 1, name: '新加坡-A', region: 'sg', status: 'online', protocol: 'vless', latency_ms: 200 },
  { id: 2, name: '新加坡-B', region: 'sg', status: 'online', protocol: 'vless', latency_ms: 80 },
]

describe('node-failover', () => {
  it('picks lower latency backup in same region', () => {
    const backup = pickBackupNode('新加坡-A', 'sg', nodes)
    expect(backup?.name).toBe('新加坡-B')
  })

  it('does not auto-failover even after three failures (aligned with Android)', () => {
    expect(AUTO_FAILOVER_ENABLED).toBe(false)
    resetFailoverMonitor()
    recordProbeFailure()
    recordProbeFailure()
    recordProbeFailure()
    expect(shouldNodeFailover()).toBe(false)
  })
})
