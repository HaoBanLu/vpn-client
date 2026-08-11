/**
 * 对齐 Compose AppDebugLogger：
 * - app_debug_enabled 时自动上报
 * - warn/error 立即 flush；info 防抖 30s
 * - 本地环形缓冲供「诊断日志」页查看
 */
import { clientApi } from '@/api/client'
import { APP_VERSION_CODE, APP_VERSION_NAME, detectClientPlatform } from '@/lib/app-meta'

export interface AppDebugLogEntry {
  ts: string
  level: 'info' | 'warn' | 'error'
  tag: string
  message: string
  context?: Record<string, string>
}

const STORAGE_KEY = 'tauri_app_debug_logs'
const MAX_ENTRIES = 200
const INFO_FLUSH_MS = 30_000
const TOKEN_KEY = 'tauri_token'

let enabled = false
let pendingUpload: AppDebugLogEntry[] = []
let debounceTimer: ReturnType<typeof setTimeout> | null = null
let flushInFlight = false
let reflushNeeded = false

function hasAuthToken(): boolean {
  try {
    return Boolean(localStorage.getItem(TOKEN_KEY))
  } catch {
    return false
  }
}

function loadEntries(): AppDebugLogEntry[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as AppDebugLogEntry[]
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function persist(entries: AppDebugLogEntry[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(entries.slice(-MAX_ENTRIES)))
}

export function sanitizeDebugText(raw: string): string {
  let text = raw.trim()
  if (text.length > 2000) text = `${text.slice(0, 2000)}…`
  const patterns: Array<[RegExp, string]> = [
    [/(bearer\s+)[a-z0-9._\-]+/gi, '$1[redacted]'],
    [/(token[=:]\s*)[a-z0-9._\-]+/gi, '$1[redacted]'],
    [/(password[=:]\s*)\S+/gi, '$1[redacted]'],
    [/eyJ[a-zA-Z0-9_\-]+\.[a-zA-Z0-9_\-]+\.[a-zA-Z0-9_\-]+/g, '[redacted-jwt]'],
  ]
  for (const [re, repl] of patterns) {
    text = text.replace(re, repl)
  }
  return text
}

function sanitizeContext(ctx?: Record<string, string>): Record<string, string> | undefined {
  if (!ctx) return undefined
  const out: Record<string, string> = {}
  for (const [k, v] of Object.entries(ctx)) {
    if (v == null || v === '') continue
    out[k] = sanitizeDebugText(String(v))
  }
  return Object.keys(out).length ? out : undefined
}

function deviceMeta(): Record<string, unknown> {
  const platform = detectClientPlatform()
  return {
    platform,
    app_channel: platform === 'android' || platform === 'ios' ? `tauri-${platform}` : 'tauri-desktop',
    app_version: APP_VERSION_NAME,
    version_code: APP_VERSION_CODE,
  }
}

function deviceId(): string {
  return `tauri-${detectClientPlatform()}`
}

/** 登录/拉用户后调用；关闭时清空待上报队列。 */
export function configureAppDebug(nextEnabled: boolean) {
  enabled = Boolean(nextEnabled)
  if (!enabled) {
    pendingUpload = []
    if (debounceTimer) {
      clearTimeout(debounceTimer)
      debounceTimer = null
    }
  }
}

export function isAppDebugEnabled(): boolean {
  return enabled
}

export function appendDebugLog(
  tag: string,
  message: string,
  level: AppDebugLogEntry['level'] = 'info',
  context?: Record<string, string>,
) {
  const sanitized = sanitizeDebugText(message)
  if (!sanitized) return

  const entry: AppDebugLogEntry = {
    ts: new Date().toISOString(),
    level,
    tag,
    message: sanitized,
    context: sanitizeContext(context),
  }

  // 本地始终保留，便于未开 flag 时手动页也能看到近期事件（上传仍受 flag 约束）
  const entries = loadEntries()
  entries.push(entry)
  persist(entries)

  if (!enabled) return
  queueUpload(entry, level === 'warn' || level === 'error')
}

function queueUpload(entry: AppDebugLogEntry, immediate: boolean) {
  pendingUpload.push(entry)
  if (immediate) {
    if (debounceTimer) {
      clearTimeout(debounceTimer)
      debounceTimer = null
    }
    void flushPending()
    return
  }
  if (debounceTimer) return
  debounceTimer = setTimeout(() => {
    debounceTimer = null
    void flushPending()
  }, INFO_FLUSH_MS)
}

/** 立即上报待发送队列（连接成功/失败/断开时调用）。 */
export async function flushDebugLogs(): Promise<number> {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
    debounceTimer = null
  }
  return flushPending()
}

async function flushPending(): Promise<number> {
  if (!enabled || !hasAuthToken()) return 0
  if (flushInFlight) {
    reflushNeeded = true
    return 0
  }
  if (pendingUpload.length === 0) return 0

  const batch = pendingUpload.splice(0, pendingUpload.length)
  flushInFlight = true
  try {
    const accepted = await postEntries(batch)
    return accepted
  } catch {
    // 失败放回队首，下次再试（避免丢诊断）
    pendingUpload = [...batch, ...pendingUpload].slice(-MAX_ENTRIES)
    return 0
  } finally {
    flushInFlight = false
    if (reflushNeeded) {
      reflushNeeded = false
      if (pendingUpload.length > 0) void flushPending()
    }
  }
}

async function postEntries(batch: AppDebugLogEntry[]): Promise<number> {
  if (batch.length === 0) return 0
  try {
    const res = await clientApi.uploadAppDebugLogs({
      entries: batch.map((e) => ({
        level: e.level,
        category: e.tag,
        message: e.message,
        context: e.context,
        client_at: e.ts,
      })),
      device_meta: deviceMeta(),
      device_id: deviceId(),
    })
    return res.data.accepted ?? batch.length
  } catch (e: unknown) {
    const status = (e as { response?: { status?: number } })?.response?.status
    if (status === 403) {
      // 服务端关闭 debug：停止自动上报
      configureAppDebug(false)
      return 0
    }
    throw e
  }
}

export function listDebugLogs(): AppDebugLogEntry[] {
  return loadEntries().slice().reverse()
}

export function clearDebugLogs() {
  localStorage.removeItem(STORAGE_KEY)
  pendingUpload = []
  if (debounceTimer) {
    clearTimeout(debounceTimer)
    debounceTimer = null
  }
}

/** 手动上传：发送本地全部缓冲（诊断日志页按钮）。 */
export async function uploadDebugLogs(): Promise<number> {
  const entries = loadEntries()
  if (entries.length === 0) return 0
  const accepted = await postEntries(entries)
  pendingUpload = []
  if (debounceTimer) {
    clearTimeout(debounceTimer)
    debounceTimer = null
  }
  return accepted
}

/** 启动时根据本地缓存用户恢复开关（登录后仍会被 configure 覆盖）。 */
export function bootstrapAppDebugFromStorage() {
  try {
    const raw = localStorage.getItem('tauri_user')
    if (!raw) {
      configureAppDebug(false)
      return
    }
    const user = JSON.parse(raw) as { app_debug_enabled?: boolean }
    configureAppDebug(Boolean(user?.app_debug_enabled))
  } catch {
    configureAppDebug(false)
  }
}

/** 测试用：清空运行时状态 */
export function __resetAppDebugForTests() {
  enabled = false
  pendingUpload = []
  flushInFlight = false
  reflushNeeded = false
  if (debounceTimer) {
    clearTimeout(debounceTimer)
    debounceTimer = null
  }
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    /* ignore */
  }
}

export function __pendingCountForTests(): number {
  return pendingUpload.length
}
