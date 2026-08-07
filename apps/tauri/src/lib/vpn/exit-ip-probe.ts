export interface ExitIpInfo {
  ip: string
  country?: string
  region?: string
  city?: string
}

const GEO_URL = 'https://ip-api.com/json/?fields=status,query,country,regionName,city'
const DEFAULT_TIMEOUT_MS = 8_000

/** VPN 连通后经默认路由（隧道）探测公网出口 IP 与归属地。 */
export async function probeExitIp(timeoutMs = DEFAULT_TIMEOUT_MS): Promise<ExitIpInfo | null> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const res = await fetch(GEO_URL, {
      method: 'GET',
      signal: controller.signal,
      cache: 'no-store',
    })
    if (!res.ok) return null
    const json = (await res.json()) as {
      status?: string
      query?: string
      country?: string
      regionName?: string
      city?: string
    }
    if (json.status !== 'success') return null
    const ip = json.query?.trim()
    if (!ip) return null
    return {
      ip,
      country: json.country?.trim() || undefined,
      region: json.regionName?.trim() || undefined,
      city: json.city?.trim() || undefined,
    }
  } catch {
    return null
  } finally {
    clearTimeout(timer)
  }
}
