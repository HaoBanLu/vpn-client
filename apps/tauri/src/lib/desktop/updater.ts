import { invoke } from '@tauri-apps/api/core'
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
  latestVersionName?: string
  releaseNotes?: string
}

export interface InstallAppUpdateOptions {
  downloadUrl?: string
  versionLabel?: string
  versionCode?: number
}

/** 优先尝试 Tauri 内置 updater；无更新或失败时再查 API（含强制更新）。 */
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
          latestVersionName: update.version,
        }
      }
      // 内置 updater 无新包时仍查 API，避免漏掉强制更新
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
    latestVersionName: data.latest_version_name,
    releaseNotes: data.release_notes,
  }
}

/**
 * 安装更新：
 * - 桌面：Tauri updater，否则外链
 * - Android：应用内 DownloadManager 下载并调起安装，失败再外链
 */
export async function installAppUpdate(options?: InstallAppUpdateOptions | string): Promise<boolean> {
  const opts: InstallAppUpdateOptions =
    typeof options === 'string' ? { downloadUrl: options } : options ?? {}

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

  if (opts.downloadUrl && detectClientPlatform() === 'android') {
    try {
      await invoke('vpn_install_apk_update', {
        options: {
          url: opts.downloadUrl,
          versionLabel: opts.versionLabel || APP_VERSION_NAME,
          versionCode: opts.versionCode ?? 0,
        },
      })
      return true
    } catch {
      // 插件未就绪时回退浏览器下载
    }
  }

  if (opts.downloadUrl) {
    await openExternalUrl(opts.downloadUrl)
    return true
  }
  return false
}
