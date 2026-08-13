import { invoke } from '@tauri-apps/api/core'
import { detectClientPlatform } from '@/lib/app-meta'

/** 将 https://t.me/xxx 转为可唤起 Telegram App 的 tg:// 链接；邀请链保持 https。 */
export function telegramAppUrl(raw: string): string | null {
  try {
    const parsed = new URL(raw.trim())
    const host = parsed.hostname.toLowerCase()
    if (host !== 't.me' && host !== 'telegram.me' && host !== 'www.t.me') return null
    const path = parsed.pathname.replace(/^\//, '')
    if (!path) return null
    if (path.startsWith('+') || path.toLowerCase().startsWith('joinchat/')) return null
    const domain = path.split('/')[0]
    if (!domain) return null
    return `tg://resolve?domain=${encodeURIComponent(domain)}`
  } catch {
    return null
  }
}

export function isTelegramChannelType(type?: string) {
  return Boolean(type?.startsWith('telegram'))
}

function isHttpUrl(url: string) {
  return /^https?:\/\//i.test(url)
}

async function openWithAndroidIntent(url: string) {
  await invoke<boolean>('vpn_open_external_url', { options: { url } })
}

export async function openExternalUrl(url: string) {
  const trimmed = url.trim()
  if (!trimmed) throw new Error('链接无效')
  // Android 禁止 window.open：未知 scheme（如 tg://）会把当前 WebView 导航到错误页
  if (detectClientPlatform() === 'android') {
    await openWithAndroidIntent(trimmed)
    return
  }
  try {
    const { open } = await import('@tauri-apps/plugin-shell')
    await open(trimmed)
  } catch {
    if (!isHttpUrl(trimmed)) throw new Error('无法打开链接')
    const opened = window.open(trimmed, '_blank', 'noopener,noreferrer')
    if (!opened) throw new Error('无法打开链接')
  }
}

/** 客服渠道：Telegram 优先唤起 App，失败再打开 https。 */
export async function openSupportChannelUrl(url: string, type?: string) {
  const trimmed = url.trim()
  if (!trimmed) throw new Error('该渠道暂未配置有效链接')
  const appUrl =
    isTelegramChannelType(type) || telegramAppUrl(trimmed) ? telegramAppUrl(trimmed) : null
  if (appUrl) {
    try {
      await openExternalUrl(appUrl)
      return
    } catch {
      // 未安装 Telegram 时走网页链接
    }
  }
  await openExternalUrl(trimmed)
}
