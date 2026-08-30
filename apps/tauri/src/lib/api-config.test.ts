import { describe, expect, it } from 'vitest'
import { resolveAbsoluteDownloadUrl, resolveAppOrigin } from '@/lib/api-config'

describe('api-config download url', () => {
  const apiBase = 'http://192.229.87.112:44080/api'

  it('resolveAppOrigin strips /api suffix', () => {
    expect(resolveAppOrigin(apiBase)).toBe('http://192.229.87.112:44080')
  })

  it('resolveAbsoluteDownloadUrl expands relative upload path', () => {
    expect(resolveAbsoluteDownloadUrl('/api/uploads/apk/android_148.apk', apiBase)).toBe(
      'http://192.229.87.112:44080/api/uploads/apk/android_148.apk',
    )
  })

  it('resolveAbsoluteDownloadUrl keeps absolute https url', () => {
    const url = 'https://cdn.example.com/app.apk'
    expect(resolveAbsoluteDownloadUrl(url, apiBase)).toBe(url)
  })
})
