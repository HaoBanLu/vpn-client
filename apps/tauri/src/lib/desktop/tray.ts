import { invoke } from '@tauri-apps/api/core'
import { listen } from '@tauri-apps/api/event'
import { isDesktopPlatform } from '@/lib/layout'

function isTauriRuntime(): boolean {
  return typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window
}

export async function initDesktopTray(options: {
  onDisconnect: () => void | Promise<void>
  syncHideOnClose: (enabled: boolean) => Promise<void>
  hideOnClose: boolean
}): Promise<() => void> {
  // Windows 浏览器 UA 也会被判为桌面平台，需再确认 Tauri 运行时存在
  if (!isDesktopPlatform() || !isTauriRuntime()) return () => {}

  try {
    await options.syncHideOnClose(options.hideOnClose)
    const unlisten = await listen('tray://disconnect', () => {
      void options.onDisconnect()
    })
    return unlisten
  } catch {
    return () => {}
  }
}

export async function updateTrayTooltip(text: string) {
  if (!isDesktopPlatform() || !isTauriRuntime()) return
  try {
    await invoke('tray_update_tooltip', { text })
  } catch {
    // 浏览器 dev 模式忽略
  }
}

export async function setTrayHideOnClose(enabled: boolean) {
  if (!isDesktopPlatform() || !isTauriRuntime()) return
  try {
    await invoke('tray_set_hide_on_close', { enabled })
  } catch {
    // ignore
  }
}
