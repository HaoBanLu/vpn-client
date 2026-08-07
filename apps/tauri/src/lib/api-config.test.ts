import { describe, expect, it } from 'vitest'
import { DEFAULT_API_BASE_URL, resolveApiBaseUrl } from './api-config'

describe('api-config', () => {
  it('defaultsToAndroidReleaseApi', () => {
    expect(DEFAULT_API_BASE_URL).toBe('http://192.229.87.112:44080/api')
  })

  it('resolveApiBaseUrlPrefersEnv', () => {
    expect(resolveApiBaseUrl('http://127.0.0.1:48080/api')).toBe('http://127.0.0.1:48080/api')
  })
})
