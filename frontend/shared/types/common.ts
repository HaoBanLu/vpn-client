/**
 * 通用类型定义
 */

/**
 * API 响应基础结构
 */
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

/**
 * 分页参数
 */
export interface PaginationParams {
  page: number
  page_size: number
}

/**
 * 分页响应
 */
export interface PaginatedResponse<T> {
  items: T[]
  total: number
  page: number
  page_size: number
}

/**
 * 状态类型
 */
export type Status = 'active' | 'inactive' | 'pending' | 'cancelled' | 'expired'

/**
 * 优先级类型
 */
export type Priority = 'low' | 'medium' | 'high' | 'urgent'

