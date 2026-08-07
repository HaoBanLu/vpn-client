import { probeExitIp } from './exit-ip-probe'
import { detectLocalIpv6Desktop } from './privacy-bridge'

export interface PrivacyLeakProbeResult {
  exitIp: string | null
  exitIpLooksProtected: boolean
  ipv6LocalActive: boolean
  dnsReachable: boolean
  passed: boolean
}

const BASELINE_IP_KEY = 'tauri_privacy_baseline_ip'
const DNS_PROBE_URL = 'https://cloudflare-dns.com/dns-query?name=example.com&type=A'

export function savePrivacyBaselineIp(ip: string | null) {
  if (ip) localStorage.setItem(BASELINE_IP_KEY, ip)
}

export function loadPrivacyBaselineIp(): string | null {
  return localStorage.getItem(BASELINE_IP_KEY)
}

/** 纯逻辑评估，便于单测。 */
export function evaluatePrivacyLeakProbe(input: {
  exitIp: string | null
  baselineIp?: string | null
  ipv6LocalActive: boolean
  dnsReachable: boolean
  ipv6ProtectionEnabled?: boolean
}): PrivacyLeakProbeResult {
  const ipv6ProtectionEnabled = input.ipv6ProtectionEnabled ?? true
  const ipv6Risk = ipv6ProtectionEnabled && input.ipv6LocalActive
  const protectedIp =
    !!input.exitIp &&
    (!input.baselineIp || input.exitIp.toLowerCase() !== input.baselineIp.toLowerCase())
  const passed = protectedIp && !ipv6Risk && input.dnsReachable
  return {
    exitIp: input.exitIp,
    exitIpLooksProtected: protectedIp,
    ipv6LocalActive: input.ipv6LocalActive,
    dnsReachable: input.dnsReachable,
    passed,
  }
}

async function probeDns(): Promise<boolean> {
  try {
    const res = await fetch(DNS_PROBE_URL, {
      method: 'GET',
      headers: { Accept: 'application/dns-json' },
      cache: 'no-store',
    })
    return res.status >= 200 && res.status < 300
  } catch {
    return false
  }
}

/** 桌面端轻量隐私泄露自检（出口 IP / DNS / IPv6）。 */
export async function runPrivacyLeakProbe(options?: {
  baselineIp?: string | null
  ipv6ProtectionEnabled?: boolean
}): Promise<PrivacyLeakProbeResult> {
  const baseline = options?.baselineIp ?? loadPrivacyBaselineIp()
  const exit = await probeExitIp().catch(() => null)
  const dnsOk = await probeDns()
  const ipv6Local = await detectLocalIpv6Desktop()
  return evaluatePrivacyLeakProbe({
    exitIp: exit?.ip ?? null,
    baselineIp: baseline,
    ipv6LocalActive: ipv6Local,
    dnsReachable: dnsOk,
    ipv6ProtectionEnabled: options?.ipv6ProtectionEnabled ?? true,
  })
}

export function formatPrivacyProbeMessage(result: PrivacyLeakProbeResult): string {
  if (result.passed) {
    return `自检通过：出口 IP ${result.exitIp ?? '-'}`
  }
  const parts = ['自检未完全通过']
  if (!result.exitIpLooksProtected) parts.push('出口 IP 异常')
  if (result.ipv6LocalActive) parts.push('IPv6 风险')
  if (!result.dnsReachable) parts.push('DNS 异常')
  return parts.join(' · ')
}
