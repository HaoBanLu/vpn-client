import { describe, expect, it } from 'vitest'
import {
  isAppConnectable,
  isRelayCompatible,
  normalizeProtocol,
  unsupportedReason,
} from './app-protocol-support'
import type { NodeItem } from '@/api/client'

function node(
  protocol: string,
  accessMode: string | undefined = 'direct',
  tlsMode?: string,
): NodeItem {
  return {
    id: 1,
    name: 'test',
    region: 'cn',
    status: 'online',
    protocol,
    access_mode: accessMode,
    tls_mode: tlsMode,
  }
}

describe('app-protocol-support relay', () => {
  it('allows all sing-box protocols via relay (not only VLESS+Reality)', () => {
    for (const protocol of ['vless', 'vmess', 'trojan', 'shadowsocks', 'ss', 'hysteria2', 'hy2']) {
      const n = node(protocol, 'relay', 'tls')
      expect(isAppConnectable(n), protocol).toBe(true)
      expect(isRelayCompatible(n), protocol).toBe(true)
      expect(unsupportedReason(n), protocol).toBeNull()
    }
  })

  it('allows relay VLESS without Reality', () => {
    expect(isAppConnectable(node('vless', 'relay', 'tls'))).toBe(true)
  })

  it('rejects OpenVPN/WireGuard on relay', () => {
    for (const protocol of ['openvpn', 'wireguard']) {
      const n = node(protocol, 'relay')
      expect(isAppConnectable(n)).toBe(false)
      expect(isRelayCompatible(n)).toBe(false)
      // 原生栈优先提示官方客户端（与直连一致）
      expect(unsupportedReason(n) ?? '').toMatch(/官方客户端|OpenVPN|WireGuard/)
    }
  })

  it('rejects direct OpenVPN in App', () => {
    const n = node('openvpn', 'direct')
    expect(isAppConnectable(n)).toBe(false)
    expect(unsupportedReason(n)).toContain('OpenVPN')
  })

  it('normalizes protocol aliases', () => {
    expect(normalizeProtocol('hy2')).toBe('hysteria2')
    expect(normalizeProtocol('ss')).toBe('shadowsocks')
    expect(normalizeProtocol('')).toBe('vmess')
  })
})
