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
