import { reactive, readonly } from 'vue'
import { listen, type UnlistenFn } from '@tauri-apps/api/event'
import { detectClientPlatform } from '@/lib/app-meta'
import {
  checkAppUpdate,
  getPendingApkUpdate,
  installAppUpdate,
  tryInstallPendingApk,
  type AppUpdateResult,
  type InstallUpdatePhase,
  type PendingApkUpdate,
} from '@/lib/desktop/updater'
import {
  clearUpdateAccepted,
  isLocalVersionUpToDate,
  markUpdateAccepted,
  markUpdateDismissed,
  recordUpdateCheckTime,
  shouldRunPeriodicUpdateCheck,
  shouldShowUpdatePrompt,
} from '@/lib/app-update/dismiss'

export type AppUpdateOverlayPhase =
  | 'idle'
  | 'prompt'
  | 'downloading'
  | 'installing'
  | 'pending_install'
  | 'done'
  | 'error'

export interface AppUpdateState {
  visible: boolean
  phase: AppUpdateOverlayPhase
  progress: number
  statusMessage: string
  updateResult: AppUpdateResult | null
  pendingInstall: PendingApkUpdate | null
  installing: boolean
  checking: boolean
  manualCheckMessage: string | null
  manualCheckError: string | null
}

const state = reactive<AppUpdateState>({
  visible: false,
  phase: 'idle',
  progress: 0,
  statusMessage: '',
  updateResult: null,
  pendingInstall: null,
  installing: false,
  checking: false,
  manualCheckMessage: null,
  manualCheckError: null,
})

let listenersBound = false
let unlistenFns: UnlistenFn[] = []

function setPhase(phase: AppUpdateOverlayPhase, message = '', progress = state.progress) {
  state.phase = phase
  state.statusMessage = message
  state.progress = progress
  state.visible = phase !== 'idle'
}

function hideOverlay() {
  state.visible = false
  state.phase = 'idle'
  state.progress = 0
  state.statusMessage = ''
  state.installing = false
}

async function refreshPendingInstall() {
  state.pendingInstall = await getPendingApkUpdate()
  if (state.pendingInstall && state.phase === 'idle') {
    setPhase('pending_install', `版本 ${state.pendingInstall.versionLabel} 已下载完成`)
  }
}

async function runCheck(options: { showPrompt?: boolean; isManual?: boolean } = {}) {
  const { showPrompt = false, isManual = false } = options
  if (isManual) {
    state.checking = true
    state.manualCheckMessage = null
    state.manualCheckError = null
  }
  try {
    recordUpdateCheckTime()
    const result = await checkAppUpdate()
    if (isManual) {
      state.updateResult = result.hasUpdate ? result : null
      state.manualCheckMessage = result.message
    }
    if (isLocalVersionUpToDate(result)) {
      clearUpdateAccepted()
      state.updateResult = null
      hideOverlay()
      return result
    }
    if ((showPrompt || isManual) && shouldShowUpdatePrompt(result)) {
      state.updateResult = result
      if (showPrompt && !isManual) {
        setPhase('prompt', result.message)
      }
    } else if (!result.hasUpdate && !result.forceUpdate) {
      hideOverlay()
    }
    return result
  } catch (e: unknown) {
    if (isManual) {
      state.manualCheckError = e instanceof Error ? e.message : '检查更新失败'
    }
    throw e
  } finally {
    if (isManual) state.checking = false
  }
}

async function acceptUpdate() {
  const result = state.updateResult
  if (!result) return
  markUpdateAccepted(result)
  state.installing = true
  setPhase('downloading', '正在准备更新', 0)
  try {
    const installResult = await installAppUpdate(
      {
        downloadUrl: result.downloadUrl,
        versionLabel: result.latestVersionName,
        versionCode: result.latestVersionCode,
      },
      ({ phase, percent, message }) => {
        const overlayPhase = phase as AppUpdateOverlayPhase
        setPhase(overlayPhase === 'idle' ? 'downloading' : overlayPhase, message ?? state.statusMessage, percent)
      },
    )
    if (!installResult.ok) {
      setPhase('error', state.statusMessage || '更新失败，请稍后重试')
      return
    }
    if (installResult.usedExternalBrowser) {
      setPhase('done', '已在浏览器打开下载页')
      state.updateResult = null
      return
    }
    if (detectClientPlatform() === 'android') {
      await refreshPendingInstall()
      if (state.pendingInstall) {
        setPhase('pending_install', `版本 ${state.pendingInstall.versionLabel} 已下载完成，请点击立即安装`)
      } else {
        setPhase('downloading', '正在下载，完成后将提示安装', 30)
      }
      state.updateResult = null
      return
    }
    setPhase('done', '更新完成，正在重启', 100)
  } catch (e: unknown) {
    setPhase('error', e instanceof Error ? e.message : '更新失败')
  } finally {
    state.installing = false
  }
}

function dismissPrompt() {
  if (state.updateResult && !state.updateResult.forceUpdate) {
    markUpdateDismissed(state.updateResult)
  }
  state.updateResult = null
  hideOverlay()
}

async function installPendingApk() {
  state.installing = true
  try {
    const result = await tryInstallPendingApk()
    if (result === 'launched') {
      setPhase('installing', '请按系统提示完成安装', 100)
      state.updateResult = null
    } else if (result === 'need_permission') {
      await refreshPendingInstall()
      setPhase('pending_install', '请先允许安装未知应用，再点击立即安装')
    } else if (result === 'no_pending') {
      hideOverlay()
      state.pendingInstall = null
    } else {
      setPhase('error', '无法打开安装程序，请稍后重试')
    }
  } finally {
    state.installing = false
  }
}

async function reconcileAfterResume() {
  await refreshPendingInstall()
  if (state.updateResult && isLocalVersionUpToDate(state.updateResult)) {
    clearUpdateAccepted()
    state.updateResult = null
    hideOverlay()
    return
  }
  if (state.pendingInstall) {
    const attempt = await tryInstallPendingApk()
    if (attempt === 'launched') {
      setPhase('installing', '请按系统提示完成安装', 100)
      return
    }
    if (attempt === 'need_permission') {
      setPhase('pending_install', '请先允许安装未知应用，再点击立即安装')
      return
    }
    if (attempt === 'no_pending') {
      state.pendingInstall = null
      hideOverlay()
      return
    }
  }
  if (shouldRunPeriodicUpdateCheck()) {
    await runCheck({ showPrompt: true })
  }
}

async function bindAndroidEvents() {
  if (listenersBound || detectClientPlatform() !== 'android') return
  listenersBound = true
  const events = [
    'app-update://download-started',
    'app-update://download-complete',
    'app-update://download-failed',
    'app-update://install-launched',
    'app-update://resume',
  ]
  for (const event of events) {
    const unlisten = await listen(event, async (payload) => {
      if (event === 'app-update://download-started') {
        setPhase('downloading', '正在下载更新', 10)
      } else if (event === 'app-update://download-complete') {
        const data = payload.payload as PendingApkUpdate
        state.pendingInstall = data
        setPhase('pending_install', `版本 ${data.versionLabel} 已下载完成，请点击立即安装`, 100)
        state.updateResult = null
      } else if (event === 'app-update://download-failed') {
        const data = payload.payload as { message?: string }
        setPhase('error', data.message || '下载失败，请稍后重试')
      } else if (event === 'app-update://install-launched') {
        setPhase('installing', '请按系统提示完成安装', 100)
        state.updateResult = null
      } else if (event === 'app-update://resume') {
        await reconcileAfterResume()
      }
    })
    unlistenFns.push(unlisten)
  }
}

function disposeUpdateListeners() {
  for (const fn of unlistenFns) fn()
  unlistenFns = []
  listenersBound = false
}

export function useAppUpdate() {
  return {
    state: readonly(state),
    checkSilently: () => runCheck({ showPrompt: true }),
    checkManual: () => runCheck({ isManual: true }),
    acceptUpdate,
    dismissPrompt,
    installPendingApk,
    reconcileAfterResume,
    refreshPendingInstall,
    bindAndroidEvents,
    disposeUpdateListeners,
    hideOverlay,
  }
}

export type { AppUpdateResult, InstallUpdatePhase }
