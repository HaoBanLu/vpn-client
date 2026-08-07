import { describe, expect, it } from 'vitest'
import type { AxiosError } from 'axios'
import { ApiBusinessError, mapApiError } from './api-error'

describe('mapApiError', () => {
  it('mapsInvalidCredentialsToChinese', () => {
    const error = {
      response: {
        status: 401,
        data: { code: 401, message: 'Invalid credentials' },
      },
      isAxiosError: true,
    } as AxiosError

    expect(mapApiError(error, '登录失败')).toBe('邮箱或密码错误')
  })

  it('mapsTimeoutWithoutResponse', () => {
    const error = {
      code: 'ECONNABORTED',
      message: 'timeout of 30000ms exceeded',
      isAxiosError: true,
    } as AxiosError

    expect(mapApiError(error, '登录失败', 'https://vpn.example.com/api')).toContain('连接服务器超时')
  })

  it('mapsNetworkErrorWithServerHint', () => {
    const error = {
      code: 'ERR_NETWORK',
      message: 'Network Error',
      isAxiosError: true,
    } as AxiosError

    expect(
      mapApiError(error, '登录失败', 'http://192.229.87.112:44080/api'),
    ).toContain('192.229.87.112:44080')
  })

  it('keepsLoginDeniedNewDeviceMessage', () => {
    const error = new ApiBusinessError(
      '当前账号已达到在线设备上限，请先在其他设备退出后再试',
      'LOGIN_DENIED_NEW_DEVICE',
    )

    expect(mapApiError(error, '登录失败')).toContain('在线设备上限')
  })

  it('appendsTraceIdForBusinessError', () => {
    const error = new ApiBusinessError('加载失败', 'UPSTREAM_UNAVAILABLE', 'trace-abc')
    expect(mapApiError(error, '失败')).toContain('追踪ID: trace-abc')
  })
})
