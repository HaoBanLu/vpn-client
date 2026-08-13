import { describe, expect, it } from 'vitest'
import {
  sessionInvalidationMessage,
  shouldInvalidateSession,
  shouldLogoutOnApiFailure,
} from './session-auth'

describe('session-auth', () => {
  it('shouldInvalidate_whenBearer401OnProtectedEndpoint', () => {
    expect(shouldInvalidateSession('/api/v1/users/me', true, undefined)).toBe(true)
  })

  it('shouldNotInvalidate_whenLogin401', () => {
    expect(shouldInvalidateSession('/api/v1/auth/login', true, undefined)).toBe(false)
  })

  it('shouldInvalidate_whenSessionRevokedAppCode', () => {
    expect(shouldInvalidateSession('/api/v1/auth/login', false, 'SESSION_REVOKED')).toBe(true)
  })

  it('mapsInvalidTokenMessageToChinese', () => {
    expect(sessionInvalidationMessage('Invalid token')).toBe('登录状态已失效，请重新登录')
  })

  it('mapsLoginOnAnotherDeviceMessage', () => {
    expect(
      sessionInvalidationMessage('账号已在其他设备登录，请重新登录', 'LOGIN_ON_ANOTHER_DEVICE'),
    ).toBe('账号已在其他设备登录，请重新登录')
  })

  it('does not logout on timeout or missing http status', () => {
    expect(
      shouldLogoutOnApiFailure({
        path: '/v1/users/me',
        hadAuth: true,
      }),
    ).toBe(false)
    expect(
      shouldLogoutOnApiFailure({
        httpStatus: 408,
        path: '/v1/users/me',
        hadAuth: true,
      }),
    ).toBe(false)
  })

  it('logs out only on 401 for protected endpoints', () => {
    expect(
      shouldLogoutOnApiFailure({
        httpStatus: 401,
        path: '/v1/users/me',
        hadAuth: true,
      }),
    ).toBe(true)
    expect(
      shouldLogoutOnApiFailure({
        businessCode: 401,
        path: '/v1/users/me',
        hadAuth: true,
      }),
    ).toBe(true)
  })
})
