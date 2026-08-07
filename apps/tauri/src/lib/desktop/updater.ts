import { clientApi } from '@/api/client'
import { APP_VERSION_CODE, APP_VERSION_NAME, detectClientPlatform } from '@/lib/app-meta'
import { openExternalUrl } from '@/lib/open-url'
import { isDesktopPlatform } from '@/lib/layout'

export interface AppUpdateResult {
  source: 'updater' | 'api' | 'none'
  hasUpdate: boolean
  message: string
  downloadUrl?: string
  forceUpdate?: boolean
  latestVersionCode?: number
  releaseNotes?: string
}

/** 优先尝试 Tauri 内置 updater，失败则回退 API 检查 + 外链下载。 */
export async function checkAppUpdate(): Promise<AppUpdateResult> {
  if (isDesktopPlatform()) {
    try {
      const { check } = await import('@tauri-apps/plugin-updater')
      const update = await check()
      if (update) {
        return {
          source: 'updater',
          hasUpdate: true,
          message: `发现新版本 ${update.version}`,
          downloadUrl: undefined,
          forceUpdate: false,
        }
      }
      return { source: 'updater', hasUpdate: false, message: '当前已是最新版本' }
    } catch {
      // updater 未配置或不可用，回退 API
    }
  }

  const res = await clientApi.getClientVersion(
    detectClientPlatform(),
    APP_VERSION_CODE,
    APP_VERSION_NAME,
  )
  const data = res.data
  const versionLabel = data.latest_version_name || String(data.latest_version_code ?? '')
  return {
    source: 'api',
    hasUpdate: !!data.has_update,
    message: data.has_update
      ? `发现新版本 ${versionLabel}${data.release_notes ? `\n\n${data.release_notes}` : ''}`
      : '当前已是最新版本',
    downloadUrl: data.download_url,
    forceUpdate: data.force_update,
    latestVersionCode: data.latest_version_code,
    releaseNotes: data.release_notes,
  }
}

export async function installAppUpdate(downloadUrl?: string): Promise<boolean> {
  if (isDesktopPlatform()) {
    try {
      const { check } = await import('@tauri-apps/plugin-updater')
      const update = await check()
      if (update) {
        await update.downloadAndInstall()
        return true
      }
    } catch {
      // fallback below
    }
  }
  if (downloadUrl) {
    await openExternalUrl(downloadUrl)
    return true
  }
  return false
}
