import { describe, expect, it, beforeEach } from 'vitest'
import {
  consumeLastInvalidation,
  formatLoginBanner,
  loginInvalidationTitle,
  peekLastInvalidation,
  saveLastInvalidation,
} from './last-invalidation-store'

describe('last-invalidation-store', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('mapsLoginOnAnotherDeviceTitle', () => {
    expect(loginInvalidationTitle('LOGIN_ON_ANOTHER_DEVICE')).toBe('账号在其他设备登录')
  })

  it('persistsAndConsumesBannerPayload', () => {
    saveLastInvalidation({
      title: '账号在其他设备登录',
      message: '账号已在其他设备登录，请重新登录',
      appCode: 'LOGIN_ON_ANOTHER_DEVICE',
    })
    expect(peekLastInvalidation()?.message).toContain('其他设备')
    const consumed = consumeLastInvalidation()
    expect(consumed?.title).toBe('账号在其他设备登录')
    expect(peekLastInvalidation()).toBeNull()
  })

  it('formatsLoginBannerLikeAndroid', () => {
    expect(formatLoginBanner('账号在其他设备登录', '请重新登录')).toBe(
      '账号在其他设备登录：请重新登录',
    )
  })
})
