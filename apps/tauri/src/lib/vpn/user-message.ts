/** 对用户展示前，去掉底层实现相关的技术术语 */
export function sanitizeVpnUserMessage(message: string): string {
  return message
    .replace(/mihomo/gi, '代理服务')
    .replace(/sing-box/gi, '代理服务')
    .replace(/sidecar/gi, '')
    .replace(/libbox/gi, '')
    .replace(/resources\/bin/gi, '')
    .replace(/\s{2,}/g, ' ')
    .trim()
}
