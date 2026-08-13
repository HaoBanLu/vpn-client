export type ProtectionLevel = 'disconnected' | 'protected' | 'degraded'

export function resolveProtectionStatus(input: {
  connected: boolean
  appDirectCount: number
  ruleCount: number
  hardeningIncomplete?: boolean
}): { level: ProtectionLevel; title: string; summary: string } {
  if (!input.connected) {
    return {
      level: 'disconnected',
      title: '未连接',
      summary: '连接后流量默认走隧道。可在下方调整重连与系统加固。',
    }
  }
  const bypass: string[] = []
  if (input.appDirectCount > 0) bypass.push(`应用直连 ${input.appDirectCount} 个`)
  if (input.ruleCount > 0) bypass.push(`规则直连 ${input.ruleCount} 条`)
  if (bypass.length > 0) {
    return {
      level: 'degraded',
      title: '保护未完整',
      summary: `已降低保护：${bypass.join(' · ')}。这些流量会暴露真实 IP。`,
    }
  }
  if (input.hardeningIncomplete) {
    return {
      level: 'degraded',
      title: '保护未完整',
      summary: '建议完成系统 Always-on、禁止绕过 VPN 与电池优化，减少意外掉线。',
    }
  }
  return {
    level: 'protected',
    title: '已保护',
    summary: '已连接，流量默认走隧道。',
  }
}
