import { describe, expect, it, beforeEach } from 'vitest'
import {
  clearUpdateAccepted,
  isLocalVersionUpToDate,
  markUpdateAccepted,
  markUpdateDismissed,
  resolveUpdateVersionKey,
  shouldRunPeriodicUpdateCheck,
  shouldShowUpdatePrompt,
  UPDATE_ACCEPTED_KEY,
  UPDATE_DISMISSED_KEY,
  UPDATE_CHECK_INTERVAL_MS,
  UPDATE_LAST_CHECK_KEY,
} from '@/lib/app-update/dismiss'
import type { AppUpdateResult } from '@/lib/desktop/updater'

const baseResult = (overrides: Partial<AppUpdateResult> = {}): AppUpdateResult => ({
  source: 'api',
  hasUpdate: true,
  message: '发现新版本 1.2.28',
  latestVersionCode: 148,
  latestVersionName: '1.2.28',
  ...overrides,
})

describe('app-update dismiss', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('resolveUpdateVersionKey prefers version code', () => {
    expect(resolveUpdateVersionKey({ latestVersionCode: 148, latestVersionName: '1.2.28' })).toBe('148')
    expect(resolveUpdateVersionKey({ latestVersionName: '1.2.28' })).toBe('1.2.28')
  })

  it('shouldShowUpdatePrompt respects dismiss and accept', () => {
    const result = baseResult()
    expect(shouldShowUpdatePrompt(result)).toBe(true)
    markUpdateDismissed(result)
    expect(shouldShowUpdatePrompt(result)).toBe(false)
    localStorage.removeItem(UPDATE_DISMISSED_KEY)
    markUpdateAccepted(result)
    expect(shouldShowUpdatePrompt(result)).toBe(false)
  })

  it('force update always prompts', () => {
    markUpdateDismissed(baseResult())
    expect(shouldShowUpdatePrompt(baseResult({ forceUpdate: true }))).toBe(true)
  })

  it('isLocalVersionUpToDate clears need to prompt', () => {
    expect(shouldShowUpdatePrompt(baseResult({ latestVersionCode: 147, latestVersionName: '1.2.27' }))).toBe(false)
    expect(isLocalVersionUpToDate(baseResult({ latestVersionCode: 147, latestVersionName: '1.2.27' }))).toBe(true)
  })

  it('shouldRunPeriodicUpdateCheck uses 24h window', () => {
    const now = 1_700_000_000_000
    localStorage.setItem(UPDATE_LAST_CHECK_KEY, String(now - UPDATE_CHECK_INTERVAL_MS + 1))
    expect(shouldRunPeriodicUpdateCheck(now)).toBe(false)
    localStorage.setItem(UPDATE_LAST_CHECK_KEY, String(now - UPDATE_CHECK_INTERVAL_MS - 1))
    expect(shouldRunPeriodicUpdateCheck(now)).toBe(true)
  })

  it('clearUpdateAccepted removes accepted marker', () => {
    markUpdateAccepted(baseResult())
    expect(localStorage.getItem(UPDATE_ACCEPTED_KEY)).toBe('148')
    clearUpdateAccepted()
    expect(localStorage.getItem(UPDATE_ACCEPTED_KEY)).toBeNull()
  })
})
