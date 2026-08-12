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

/** 后端英文/技术文案 → 用户可读中文（勿暴露路径、主机名等） */
const KNOWN_BUSINESS_MESSAGES: Record<string, string> = {
  'invalid credentials': '邮箱或密码错误',
  'invalid request': '填写有误，请检查后重试',
  'invalid mfa code': '验证码错误或已过期',
  'failed to login': '登录失败，请稍后重试',
  'failed to register': '注册失败，请稍后重试',
  unauthorized: '登录状态已失效，请重新登录',
  'token expired': '登录已过期，请重新登录',
  'invalid token': '登录状态已失效，请重新登录',
  'network error': '网络异常，请稍后重试',
}

function mapBusinessMessage(message?: string, appCode?: string): string {
  const raw = message?.trim()
  if (!raw) return ''
  if (appCode === 'LOGIN_DENIED_NEW_DEVICE') return raw
  if (appCode === 'LOGIN_ON_ANOTHER_DEVICE') return raw
  if (appCode === 'SESSION_REVOKED') return raw
  const mapped = KNOWN_BUSINESS_MESSAGES[raw.toLowerCase()]
  return mapped || sanitizeUserMessage(raw)
}

/** 去掉用户不应看到的技术细节（URL、/api、IP:端口等） */
function sanitizeUserMessage(raw: string): string {
  let text = raw.trim()
  // 完整 URL
  text = text.replace(/https?:\/\/[^\s，。；）)\]]+/gi, '')
  // 裸露路径片段（如 /api、/v1/...）
  text = text.replace(/(^|[\s：:])\/(?:api|v\d+)(?:\/[^\s，。；）)\]]*)?/gi, '$1')
  // host:port
  text = text.replace(/\b\d{1,3}(?:\.\d{1,3}){3}(?::\d+)?\b/g, '')
  text = text.replace(/\s{2,}/g, ' ').replace(/[（(]\s*[）)]/g, '').trim()
  text = text.replace(/^[，。；、：:\s]+|[，。；、：:\s]+$/g, '').trim()
  return text || ''
}

function formatTimeoutMessage(): string {
  return '连接超时，请检查网络后重试'
}

function formatNetworkMessage(error: AxiosError): string {
  const code = error.code || ''
  const raw = error.message || ''

  if (code === 'ECONNABORTED' || raw.toLowerCase().includes('timeout')) {
    return formatTimeoutMessage()
  }
  if (code === 'ERR_NETWORK' || raw.toLowerCase().includes('network error')) {
    return '网络异常，暂时无法连接服务，请稍后重试'
  }
  return '网络异常，请检查网络后重试'
}

export function mapApiError(
  error: unknown,
  fallback = '操作失败，请稍后重试',
  /** @deprecated 保留参数兼容旧调用，不再向用户展示服务地址 */
  _serverBaseUrl?: string,
): string {
  if (error instanceof ApiBusinessError) {
    return mapBusinessMessage(error.message, error.appCode) || fallback
  }

  const axiosError = error as AxiosError<ApiResponse<unknown>>
  const data = axiosError.response?.data
  if (data?.message) {
    return mapBusinessMessage(data.message, data.app_code) || fallback
  }

  if (!axiosError.response) {
    return formatNetworkMessage(axiosError)
  }

  return fallback
}
