/**
 * 与 apps/android/app/build.gradle.kts 中 releaseAppBaseUrl 保持一致。
 * Android: API_BASE_URL = releaseAppBaseUrl + "api/v1/"
 * Tauri:    VITE_API_BASE_URL = releaseAppBaseUrl + "api"（业务路径再拼 /v1/...）
 */
export const DEFAULT_APP_BASE_URL = 'http://192.229.87.112:44080/'

export const DEFAULT_API_BASE_URL = `${DEFAULT_APP_BASE_URL.replace(/\/$/, '')}/api`

export function resolveApiBaseUrl(envValue?: string): string {
  const trimmed = envValue?.trim()
  return trimmed || DEFAULT_API_BASE_URL
}

/** API base 去掉 `/api` 后缀，用于拼接 `/api/uploads/...` 等静态资源路径 */
export function resolveAppOrigin(apiBase?: string): string {
  return (apiBase ?? resolveApiBaseUrl()).replace(/\/api\/?$/, '').replace(/\/$/, '')
}

/** 控制面常返回 `/api/uploads/...` 相对路径；DownloadManager / 外链需要绝对 URL */
export function resolveAbsoluteDownloadUrl(downloadUrl?: string, apiBase?: string): string | undefined {
  const trimmed = downloadUrl?.trim()
  if (!trimmed) return undefined
  if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) return trimmed
  const origin = resolveAppOrigin(apiBase)
  const path = trimmed.startsWith('/') ? trimmed : `/${trimmed}`
  return `${origin}${path}`
}
