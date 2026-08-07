/**
 * 流量格式化工具函数
 */

/**
 * 格式化流量（自动选择合适的单位）
 * @param mb 流量（MB）
 * @returns 格式化后的流量字符串
 */
export const formatTraffic = (mb: number): string => {
  if (mb < 0) return '0 MB'
  
  if (mb < 1024) {
    return `${mb.toFixed(2)} MB`
  } else if (mb < 1024 * 1024) {
    return `${(mb / 1024).toFixed(2)} GB`
  } else {
    return `${(mb / (1024 * 1024)).toFixed(2)} TB`
  }
}

/**
 * 格式化流量速率（MB/s）
 */
export const formatTrafficRate = (mbps: number): string => {
  if (mbps < 0) return '0 MB/s'
  
  if (mbps < 1) {
    return `${(mbps * 1024).toFixed(2)} KB/s`
  } else if (mbps < 1024) {
    return `${mbps.toFixed(2)} MB/s`
  } else {
    return `${(mbps / 1024).toFixed(2)} GB/s`
  }
}

/**
 * 将字节转换为 MB
 */
export const bytesToMB = (bytes: number): number => {
  return bytes / (1024 * 1024)
}

/**
 * 将 MB 转换为字节
 */
export const mbToBytes = (mb: number): number => {
  return mb * 1024 * 1024
}

