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
import { APP_VERSION_CODE } from '@/lib/app-meta'

/** 始终高于当前安装版本，避免发版 bump APP_VERSION_CODE 后测试失效 */
const NEWER_VERSION_CODE = APP_VERSION_CODE + 1
const NEWER_VERSION_NAME = '9.9.9'

const baseResult = (overrides: Partial<AppUpdateResult> = {}): AppUpdateResult => ({
  source: 'api',
  hasUpdate: true,
  message: `发现新版本 ${NEWER_VERSION_NAME}`,
  latestVersionCode: NEWER_VERSION_CODE,
  latestVersionName: NEWER_VERSION_NAME,
  ...overrides,
})

describe('app-update dismiss', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('resolveUpdateVersionKey prefers version code', () => {
    expect(
      resolveUpdateVersionKey({ latestVersionCode: NEWER_VERSION_CODE, latestVersionName: NEWER_VERSION_NAME }),
    ).toBe(String(NEWER_VERSION_CODE))
    expect(resolveUpdateVersionKey({ latestVersionName: NEWER_VERSION_NAME })).toBe(NEWER_VERSION_NAME)
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
    const olderCode = APP_VERSION_CODE - 1
    expect(shouldShowUpdatePrompt(baseResult({ latestVersionCode: olderCode, latestVersionName: '0.0.0' }))).toBe(
      false,
    )
    expect(isLocalVersionUpToDate(baseResult({ latestVersionCode: olderCode, latestVersionName: '0.0.0' }))).toBe(true)
  })

  it('shouldRunPeriodicUpdateCheck uses 30min window', () => {
    const now = 1_700_000_000_000
    localStorage.setItem(UPDATE_LAST_CHECK_KEY, String(now - UPDATE_CHECK_INTERVAL_MS + 1))
    expect(shouldRunPeriodicUpdateCheck(now)).toBe(false)
    localStorage.setItem(UPDATE_LAST_CHECK_KEY, String(now - UPDATE_CHECK_INTERVAL_MS - 1))
    expect(shouldRunPeriodicUpdateCheck(now)).toBe(true)
  })

  it('clearUpdateAccepted removes accepted marker', () => {
    markUpdateAccepted(baseResult())
    expect(localStorage.getItem(UPDATE_ACCEPTED_KEY)).toBe(String(NEWER_VERSION_CODE))
    clearUpdateAccepted()
    expect(localStorage.getItem(UPDATE_ACCEPTED_KEY)).toBeNull()
  })
})
