/** 泄露自检历史（最近 10 次持久化；UI 仅展示最近 1 条会员向摘要）。 */

import type { PrivacyLeakProbeResult } from './privacy-leak-probe'

const STORAGE_KEY = 'tauri_privacy_probe_history'
const MAX_ENTRIES = 10

export interface PrivacyProbeHistoryEntry {
  atMillis: number
  passed: boolean
  exitIp: string | null
  summary: string
}

/** 列表展示用白话；兼容历史条目中的工程向 summary。 */
export function memberFacingProbeSummary(entry: Pick<PrivacyProbeHistoryEntry, 'passed'>): string {
  return entry.passed ? '已通过' : '未通过 · 可能泄露真实网络信息'
}

function buildSummary(result: PrivacyLeakProbeResult): string {
  return memberFacingProbeSummary(result)
}

export function loadPrivacyProbeHistory(): PrivacyProbeHistoryEntry[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as PrivacyProbeHistoryEntry[]
    if (!Array.isArray(parsed)) return []
    return parsed.filter(
      (e) => typeof e.atMillis === 'number' && typeof e.passed === 'boolean' && typeof e.summary === 'string',
    )
  } catch {
    return []
  }
}

export function appendPrivacyProbeHistory(result: PrivacyLeakProbeResult): PrivacyProbeHistoryEntry[] {
  const entry: PrivacyProbeHistoryEntry = {
    atMillis: Date.now(),
    passed: result.passed,
    exitIp: result.exitIp,
    summary: buildSummary(result),
  }
  const next = [entry, ...loadPrivacyProbeHistory()].slice(0, MAX_ENTRIES)
  localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  return next
}

export function clearPrivacyProbeHistory(): void {
  localStorage.removeItem(STORAGE_KEY)
}
