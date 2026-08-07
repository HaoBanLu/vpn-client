import type { NodeItem } from '@/api/client'

const NATIVE_ONLY_PROTOCOLS = new Set(['openvpn', 'wireguard'])
const SING_BOX_PROTOCOLS = new Set([
  'vless',
  'vmess',
  'trojan',
  'shadowsocks',
  'ss',
  'hysteria2',
  'hy2',
])

export function normalizeProtocol(protocol?: string | null): string {
  const raw = protocol?.trim().toLowerCase() ?? ''
  if (!raw || raw === 'null') return 'vmess'
  if (raw === 'hy2') return 'hysteria2'
  if (raw === 'ss') return 'shadowsocks'
  return raw
}

export function protocolLabel(protocol?: string | null): string {
  switch (normalizeProtocol(protocol)) {
    case 'openvpn':
      return 'OpenVPN'
    case 'wireguard':
      return 'WireGuard'
    case 'shadowsocks':
      return 'Shadowsocks'
    case 'hysteria2':
      return 'Hysteria2'
    case 'vless':
      return 'VLESS'
    case 'vmess':
      return 'VMess'
    case 'trojan':
      return 'Trojan'
    default:
      return protocol ?? '未知'
  }
}

export function usesRelay(node: NodeItem): boolean {
  return node.access_mode?.toLowerCase() === 'relay'
}

export function isRelayCompatible(node: NodeItem): boolean {
  if (!usesRelay(node)) return true
  return SING_BOX_PROTOCOLS.has(normalizeProtocol(node.protocol))
}

export function isAppConnectable(node: NodeItem): boolean {
  const protocol = normalizeProtocol(node.protocol)
  if (NATIVE_ONLY_PROTOCOLS.has(protocol)) return false
  if (!SING_BOX_PROTOCOLS.has(protocol)) return false
  return isRelayCompatible(node)
}

export function unsupportedReason(node: NodeItem): string | null {
  if (isAppConnectable(node)) return null
  const protocol = normalizeProtocol(node.protocol)
  if (NATIVE_ONLY_PROTOCOLS.has(protocol)) {
    return `需使用 ${protocolLabel(protocol)} 官方客户端，自研 App 不支持`
  }
  if (!isRelayCompatible(node)) {
    return '该节点经中转，自研 App 仅支持 sing-box 族协议（不含 OpenVPN/WireGuard）'
  }
  return '自研 App 暂不支持此协议'
}
