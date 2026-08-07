import type { AxiosError } from 'axios'
import type { ApiResponse } from '@/api/client'

export class ApiBusinessError extends Error {
  appCode?: string
  traceId?: string

  constructor(message: string, appCode?: string, traceId?: string) {
    super(message)
    this.name = 'ApiBusinessError'
    this.appCode = appCode
    this.traceId = traceId
  }
}

const KNOWN_BUSINESS_MESSAGES: Record<string, string> = {
  'invalid credentials': '邮箱或密码错误',
  'invalid request': '请求参数无效',
  'invalid mfa code': '验证码错误',
  'failed to login': '登录失败，请稍后重试或联系客服',
  'failed to register': '注册失败，请稍后重试',
}

function formatServerHint(serverBaseUrl?: string): string | null {
  const trimmed = serverBaseUrl?.trim()
  if (!trimmed) return null
  return trimmed.replace(/\/$/, '')
}

function mapBusinessMessage(message?: string, appCode?: string): string {
  const raw = message?.trim()
  if (!raw) return ''
  if (appCode === 'LOGIN_DENIED_NEW_DEVICE') return raw
  if (appCode === 'LOGIN_ON_ANOTHER_DEVICE') return raw
  if (appCode === 'SESSION_REVOKED') return raw
  const mapped = KNOWN_BUSINESS_MESSAGES[raw.toLowerCase()]
  return mapped || raw
}

function appendTraceId(message: string, traceId?: string): string {
  const id = traceId?.trim()
  if (!id) return message
  return `${message}（追踪ID: ${id}）`
}

function formatTimeoutMessage(serverBaseUrl?: string): string {
  const hint = formatServerHint(serverBaseUrl)
  if (hint) return `连接服务器超时（${hint}），请稍后重试`
  return '连接超时，请稍后重试'
}

function formatNetworkMessage(error: AxiosError, serverBaseUrl?: string): string {
  const hint = formatServerHint(serverBaseUrl)
  const code = error.code || ''
  const raw = error.message || ''

  if (code === 'ECONNABORTED' || raw.toLowerCase().includes('timeout')) {
    return formatTimeoutMessage(serverBaseUrl)
  }
  if (code === 'ERR_NETWORK' || raw.toLowerCase().includes('network error')) {
    if (hint) return `网络请求失败，无法连接 ${hint}，请检查网络或服务器状态`
    return '网络请求失败，请检查网络后重试'
  }
  if (hint) return `无法连接服务器（${hint}），请确认服务可用后重试`
  return '网络请求失败，请检查网络后重试'
}

export function mapApiError(
  error: unknown,
  fallback = '请求失败',
  serverBaseUrl?: string,
): string {
  if (error instanceof ApiBusinessError) {
    const base = mapBusinessMessage(error.message, error.appCode) || fallback
    return appendTraceId(base, error.traceId)
  }

  const axiosError = error as AxiosError<ApiResponse<unknown>>
  const data = axiosError.response?.data
  if (data?.message) {
    const base = mapBusinessMessage(data.message, data.app_code) || fallback
    return appendTraceId(base, (data as ApiResponse<unknown> & { trace_id?: string }).trace_id)
  }

  if (!axiosError.response) {
    return formatNetworkMessage(axiosError, serverBaseUrl)
  }

  return fallback
}
