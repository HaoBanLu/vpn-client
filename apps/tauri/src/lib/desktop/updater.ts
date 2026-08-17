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

export type InstallUpdatePhase = 'idle' | 'downloading' | 'installing' | 'done' | 'error'

export interface InstallUpdateProgress {
  phase: InstallUpdatePhase
  percent: number
  message?: string
}

export interface InstallAppUpdateResult {
  ok: boolean
  phase: InstallUpdatePhase
  usedExternalBrowser?: boolean
}

type UpdaterProgressEvent =
  | { event: 'Started'; data: { contentLength?: number } }
  | { event: 'Progress'; data: { chunkLength: number } }
  | { event: 'Finished' }

async function fetchApiUpdate(): Promise<AppUpdateResult> {
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

function mergeUpdaterWithApi(updater: AppUpdateResult, api: AppUpdateResult): AppUpdateResult {
  const versionLabel = updater.latestVersionName || api.latestVersionName || ''
  const message =
    api.hasUpdate && api.releaseNotes
      ? `发现新版本 ${versionLabel}\n\n${api.releaseNotes}`
      : updater.message
  return {
    ...updater,
    forceUpdate: api.forceUpdate || updater.forceUpdate,
    latestVersionCode: api.latestVersionCode ?? updater.latestVersionCode,
    latestVersionName: updater.latestVersionName || api.latestVersionName,
    downloadUrl: api.downloadUrl ?? updater.downloadUrl,
    message,
    releaseNotes: api.releaseNotes ?? updater.releaseNotes,
  }
}

/** 优先 Tauri updater；命中后仍查 API 合并强制更新与 download_url。 */
export async function checkAppUpdate(): Promise<AppUpdateResult> {
  let updaterResult: AppUpdateResult | null = null

  if (isDesktopPlatform()) {
    try {
      const { check } = await import('@tauri-apps/plugin-updater')
      const update = await check()
      if (update) {
        updaterResult = {
          source: 'updater',
          hasUpdate: true,
          message: `发现新版本 ${update.version}`,
          downloadUrl: undefined,
          forceUpdate: false,
          latestVersionName: update.version,
        }
      }
    } catch {
      // updater 未配置或不可用，回退 API
    }
  }

  const apiResult = await fetchApiUpdate()
  if (updaterResult) {
    return mergeUpdaterWithApi(updaterResult, apiResult)
  }
  return apiResult
}

async function relaunchDesktopApp() {
  if (!isDesktopPlatform()) return
  try {
    const { relaunch } = await import('@tauri-apps/plugin-process')
    await relaunch()
  } catch {
    // dev 模式或 plugin 未就绪时忽略
  }
}

/**
 * 安装更新：
 * - 桌面：Tauri updater（带进度）+ 自动 relaunch
 * - Android：应用内 DownloadManager
 * - 其它：外链
 */
export async function installAppUpdate(
  options?: InstallAppUpdateOptions | string,
  onProgress?: (progress: InstallUpdateProgress) => void,
): Promise<InstallAppUpdateResult> {
  const opts: InstallAppUpdateOptions =
    typeof options === 'string' ? { downloadUrl: options } : options ?? {}

  const report = (phase: InstallUpdatePhase, percent: number, message?: string) => {
    onProgress?.({ phase, percent, message })
  }

  if (isDesktopPlatform()) {
    try {
      const { check } = await import('@tauri-apps/plugin-updater')
      const update = await check()
      if (update) {
        report('downloading', 0, '正在下载更新…')
        let downloaded = 0
        let total = 0
        await update.downloadAndInstall((event: UpdaterProgressEvent) => {
          if (event.event === 'Started') {
            total = event.data.contentLength ?? 0
            report('downloading', 0, '正在下载更新…')
          } else if (event.event === 'Progress') {
            downloaded += event.data.chunkLength
            const percent = total > 0 ? Math.min(99, Math.round((downloaded / total) * 100)) : 50
            report('downloading', percent, '正在下载更新…')
          } else if (event.event === 'Finished') {
            report('installing', 100, '正在安装…')
          }
        })
        report('done', 100, '更新完成，正在重启…')
        await relaunchDesktopApp()
        return { ok: true, phase: 'done' }
      }
    } catch (err) {
      report('error', 0, err instanceof Error ? err.message : '更新失败')
      // fallback below
    }
  }

  if (opts.downloadUrl && detectClientPlatform() === 'android') {
    try {
      report('downloading', 0, '正在下载更新…')
      await invoke('vpn_install_apk_update', {
        options: {
          url: opts.downloadUrl,
          versionLabel: opts.versionLabel || APP_VERSION_NAME,
          versionCode: opts.versionCode ?? 0,
        },
      })
      report('downloading', 30, '已开始下载，完成后将提示安装')
      return { ok: true, phase: 'downloading' }
    } catch {
      // 插件未就绪时回退浏览器下载
    }
  }

  if (opts.downloadUrl) {
    report('downloading', 0, '正在打开下载页…')
    await openExternalUrl(opts.downloadUrl)
    report('done', 100, '已在浏览器打开下载页')
    return { ok: true, phase: 'done', usedExternalBrowser: true }
  }

  report('error', 0, '无法启动更新')
  return { ok: false, phase: 'error' }
}

export interface PendingApkUpdate {
  versionLabel: string
  versionCode: number
  needsInstallPermission?: boolean
}

export async function getPendingApkUpdate(): Promise<PendingApkUpdate | null> {
  if (detectClientPlatform() !== 'android') return null
  try {
    const res = await invoke<{ pending?: PendingApkUpdate | null }>('vpn_get_pending_apk_update')
    const pending = res?.pending
    if (!pending) return null
    return {
      versionLabel: pending.versionLabel,
      versionCode: pending.versionCode,
      needsInstallPermission: pending.needsInstallPermission,
    }
  } catch {
    return null
  }
}

export async function tryInstallPendingApk(): Promise<string | null> {
  if (detectClientPlatform() !== 'android') return null
  try {
    const res = await invoke<{ result: string }>('vpn_try_install_pending_apk')
    return res.result
  } catch {
    return null
  }
}
