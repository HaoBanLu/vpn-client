import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { message } from '@/lib/ui/message'
import { Modal } from '@/lib/ui/confirm'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'
import { sessionInvalidationMessage, shouldInvalidateSession } from '@/lib/session-auth'
import { saveLastInvalidation, loginInvalidationTitle } from '@/lib/last-invalidation-store'
import { ApiBusinessError, mapApiError } from '@/lib/api-error'
import { resolveApiBaseUrl } from '@/lib/api-config'
import type { ApiResponse } from './client'

export { ApiBusinessError }

/** 列表刷新 / 账户拉取等：错误交给页面，避免全局多条相同 toast。 */
export const SKIP_TOAST: AxiosRequestConfig = { skipGlobalToast: true }

declare module 'axios' {
  export interface AxiosRequestConfig {
    skipGlobalToast?: boolean
  }
}

const API_BASE = resolveApiBaseUrl(import.meta.env.VITE_API_BASE_URL)

const service: AxiosInstance = axios.create({
  baseURL: API_BASE,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
})

let sessionDialogVisible = false

function requestPath(config?: { url?: string; baseURL?: string }) {
  const url = config?.url || ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  const base = (config?.baseURL || API_BASE).replace(/\/$/, '')
  const path = url.startsWith('/') ? url : `/${url}`
  return `${base}${path}`
}

function requestHadAuth(config?: { headers?: unknown }) {
  const headers = config?.headers as Record<string, string> | undefined
  if (!headers) return false
  const auth = headers.Authorization || headers.authorization
  return typeof auth === 'string' && auth.trim().length > 0
}

function shouldSkipGlobalToast(config?: AxiosRequestConfig | null) {
  return Boolean(config?.skipGlobalToast)
}

async function showSessionInvalidated(messageText?: string, appCode?: string) {
  const resolvedMessage = sessionInvalidationMessage(messageText, appCode)
  saveLastInvalidation({
    title: loginInvalidationTitle(appCode),
    message: resolvedMessage,
    appCode,
  })
  try {
    const { useConnectStore } = await import('@/stores/connect')
    await useConnectStore().forceDisconnectForAuth('session_revoked')
  } catch {
    // web dev or store unavailable
  }
  const auth = useAuthStore()
  void auth.logout({ silent: true, skipVpn: true })
  router.push({ name: 'Login' })

  if (sessionDialogVisible) return
  sessionDialogVisible = true
  Modal.error({
    title: '登录状态已失效',
    content: resolvedMessage,
    okText: '知道了',
    afterClose: () => {
      sessionDialogVisible = false
    },
  })
}

service.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token && config.headers) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  if (config.data instanceof FormData && config.headers) {
    delete config.headers['Content-Type']
  }
  return config
})

service.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResponse<unknown>
    if (res.code && res.code !== 200) {
      const path = requestPath(response.config)
      const hadAuth = requestHadAuth(response.config)
      if (
        res.code === 401 &&
        shouldInvalidateSession(path, hadAuth, res.app_code)
      ) {
        showSessionInvalidated(res.message, res.app_code)
        return Promise.reject(
          new ApiBusinessError(
            sessionInvalidationMessage(res.message, res.app_code),
            res.app_code,
          ),
        )
      }
      if (res.code !== 404) {
        const mapped = mapApiError(
          new ApiBusinessError(res.message || '操作失败，请稍后重试', res.app_code, res.trace_id),
          '操作失败，请稍后重试',
          API_BASE,
        )
        // 登录失败展示在表单内，不弹全局 toast
        if (!path.includes('/auth/login') && !shouldSkipGlobalToast(response.config)) {
          message.error(mapped)
        }
        return Promise.reject(new ApiBusinessError(mapped, res.app_code, res.trace_id))
      }
      return Promise.reject(
        new ApiBusinessError(res.message || '操作失败，请稍后重试', res.app_code, res.trace_id),
      )
    }
    return response
  },
  (error) => {
    const res = error.response?.data as (ApiResponse<unknown> & { trace_id?: string }) | undefined
    const path = requestPath(error.config)
    const hadAuth = requestHadAuth(error.config)
    const status = error.response?.status

    if (
      status === 401 &&
      shouldInvalidateSession(path, hadAuth, res?.app_code)
    ) {
      showSessionInvalidated(res?.message, res?.app_code)
      return Promise.reject(
        new ApiBusinessError(
          sessionInvalidationMessage(res?.message, res?.app_code),
          res?.app_code,
          res?.trace_id,
        ),
      )
    }

    // 登录页有表单内错误条，避免与顶部 toast 重复；404/503 由页面自行处理
    const pathSkipsToast = path.includes('/auth/login')
    const statusSkipsToast = status === 404 || status === 503
    const mapped = mapApiError(error, '操作失败，请稍后重试', API_BASE)
    if (!statusSkipsToast && !pathSkipsToast && !shouldSkipGlobalToast(error.config)) {
      message.error(mapped)
    }

    return Promise.reject(
      new ApiBusinessError(mapped, res?.app_code, res?.trace_id),
    )
  },
)

async function unwrap<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<ApiResponse<T>> {
  const response = await promise
  return response.data
}

const request = {
  get<T>(url: string, config?: AxiosRequestConfig) {
    return unwrap<T>(service.get(url, config))
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return unwrap<T>(service.post(url, data, config))
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return unwrap<T>(service.put(url, data, config))
  },
}

export default request
