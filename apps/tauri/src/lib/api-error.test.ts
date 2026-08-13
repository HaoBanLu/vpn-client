import { describe, expect, it } from 'vitest'
import type { AxiosError } from 'axios'
import { ApiBusinessError, mapApiError, isNetworkConnectivityError } from './api-error'

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

  it('mapsTimeoutWithoutExposingServerUrl', () => {
    const error = {
      code: 'ECONNABORTED',
      message: 'timeout of 30000ms exceeded',
      isAxiosError: true,
    } as AxiosError

    const text = mapApiError(error, '登录失败', 'https://vpn.example.com/api')
    expect(text).toBe('连接超时，请检查网络后重试')
    expect(text).not.toMatch(/\/api|example\.com/i)
  })

  it('mapsNetworkErrorWithoutExposingHostOrApiPath', () => {
    const error = {
      code: 'ERR_NETWORK',
      message: 'Network Error',
      isAxiosError: true,
    } as AxiosError

    const text = mapApiError(error, '登录失败', 'http://192.229.87.112:44080/api')
    expect(text).toBe('网络异常，暂时无法连接服务，请稍后重试')
    expect(text).not.toMatch(/\/api|192\.229\.87\.112/)
  })

  it('keepsLoginDeniedNewDeviceMessage', () => {
    const error = new ApiBusinessError(
      '当前账号已达到在线设备上限，请先在其他设备退出后再试',
      'LOGIN_DENIED_NEW_DEVICE',
    )

    expect(mapApiError(error, '登录失败')).toContain('在线设备上限')
  })

  it('doesNotAppendTraceIdToUserFacingMessage', () => {
    const error = new ApiBusinessError('加载失败', 'UPSTREAM_UNAVAILABLE', 'trace-abc')
    expect(mapApiError(error, '失败')).toBe('加载失败')
    expect(mapApiError(error, '失败')).not.toContain('追踪ID')
  })

  it('stripsTechnicalFragmentsFromBusinessMessage', () => {
    const error = new ApiBusinessError('无法连接 https://vpn.example.com/api 请重试')
    expect(mapApiError(error, '失败')).toBe('无法连接 请重试')
  })

  it('detects connectivity failures without http response', () => {
    expect(
      isNetworkConnectivityError({
        code: 'ERR_NETWORK',
        message: 'Network Error',
        isAxiosError: true,
      }),
    ).toBe(true)
    expect(
      isNetworkConnectivityError({
        code: 'ECONNABORTED',
        message: 'timeout of 30000ms exceeded',
        isAxiosError: true,
      }),
    ).toBe(true)
    expect(
      isNetworkConnectivityError({
        response: { status: 401, data: { message: 'invalid token' } },
        isAxiosError: true,
      }),
    ).toBe(false)
    expect(isNetworkConnectivityError(new ApiBusinessError('网络异常，请检查网络后重试'))).toBe(true)
  })
})
