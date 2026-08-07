import { invoke } from '@tauri-apps/api/core'
import { isDesktopPlatform } from '@/lib/layout'

/** 桌面端：检测本机活跃 IPv6 地址（Rust if-addrs）。 */
export async function detectLocalIpv6Desktop(): Promise<boolean> {
  if (!isDesktopPlatform()) return false
  try {
    return await invoke<boolean>('privacy_detect_local_ipv6')
  } catch {
    return false
  }
}
