import { clientApi } from '@/api/client'
import { detectClientPlatform } from '@/lib/app-meta'

export interface AppDebugLogEntry {
  ts: string
  level: 'info' | 'warn' | 'error'
  tag: string
  message: string
}

const STORAGE_KEY = 'tauri_app_debug_logs'
const MAX_ENTRIES = 200

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

export function appendDebugLog(
  tag: string,
  message: string,
  level: AppDebugLogEntry['level'] = 'info',
) {
  const entries = loadEntries()
  entries.push({
    ts: new Date().toISOString(),
    level,
    tag,
    message,
  })
  persist(entries)
}

export function listDebugLogs(): AppDebugLogEntry[] {
  return loadEntries().slice().reverse()
}

export function clearDebugLogs() {
  localStorage.removeItem(STORAGE_KEY)
}

export async function uploadDebugLogs(): Promise<number> {
  const entries = loadEntries()
  if (entries.length === 0) return 0
  const res = await clientApi.uploadAppDebugLogs({
    entries: entries.map((e) => ({
      level: e.level,
      category: e.tag,
      message: e.message,
    })),
    device_meta: {
      platform: detectClientPlatform(),
      app_channel: 'tauri-desktop',
    },
    device_id: `tauri-${detectClientPlatform()}`,
  })
  return res.data.accepted ?? entries.length
}
