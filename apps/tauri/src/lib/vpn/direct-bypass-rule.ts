/** 用户自定义 Mihomo 规则直连（Clash rules → DIRECT），对齐 Android DirectBypassRuleStore。 */

export const DIRECT_BYPASS_RULE_TYPES = {
  DOMAIN: 'DOMAIN',
  DOMAIN_SUFFIX: 'DOMAIN_SUFFIX',
  DOMAIN_KEYWORD: 'DOMAIN_KEYWORD',
  IP_CIDR: 'IP-CIDR',
} as const

export type DirectBypassRuleTypeName = keyof typeof DIRECT_BYPASS_RULE_TYPES

export interface DirectBypassRuleTypeMeta {
  clashType: string
  label: string
}

export const DIRECT_BYPASS_TYPE_META: Record<DirectBypassRuleTypeName, DirectBypassRuleTypeMeta> = {
  DOMAIN: { clashType: 'DOMAIN', label: '完整域名' },
  DOMAIN_SUFFIX: { clashType: 'DOMAIN-SUFFIX', label: '域名后缀' },
  DOMAIN_KEYWORD: { clashType: 'DOMAIN-KEYWORD', label: '域名关键词' },
  IP_CIDR: { clashType: 'IP-CIDR', label: 'IP 段' },
}

export interface DirectBypassRule {
  id: string
  type: DirectBypassRuleTypeName
  value: string
  enabled: boolean
}

const STORAGE_KEY = 'tauri_direct_bypass_rules'

const hostnameRegex =
  /^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)*[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$/

function isValidIpv4Part(part: string): boolean {
  const n = Number(part)
  return Number.isInteger(n) && n >= 0 && n <= 255
}

export function validateDirectBypassRule(
  type: DirectBypassRuleTypeName,
  rawValue: string,
): string {
  const trimmed = rawValue.trim()
  if (!trimmed) throw new Error('规则内容不能为空')

  switch (type) {
    case 'DOMAIN':
    case 'DOMAIN_SUFFIX': {
      let normalized = trimmed.toLowerCase()
      if (type === 'DOMAIN_SUFFIX' && normalized.startsWith('*.')) {
        normalized = normalized.slice(2)
      }
      if (normalized.length > 253 || !hostnameRegex.test(normalized)) {
        throw new Error('域名格式不正确')
      }
      return normalized
    }
    case 'DOMAIN_KEYWORD': {
      if (trimmed.length > 64) throw new Error('关键词长度不能超过 64 个字符')
      if (trimmed.includes(',')) throw new Error('关键词不能包含逗号')
      return trimmed
    }
    case 'IP_CIDR': {
      const parts = trimmed.split('/')
      if (parts.length !== 2) throw new Error('IP 段格式应为 192.168.1.0/24')
      const ipParts = parts[0].trim().split('.')
      if (ipParts.length !== 4 || !ipParts.every(isValidIpv4Part)) {
        throw new Error('IP 地址无效')
      }
      const prefix = Number(parts[1].trim())
      if (!Number.isInteger(prefix) || prefix < 0 || prefix > 32) {
        throw new Error('子网掩码长度无效')
      }
      return `${ipParts.join('.')}/${prefix}`
    }
    default:
      throw new Error('未知规则类型')
  }
}

export function loadDirectBypassRules(): DirectBypassRule[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as DirectBypassRule[]
    if (!Array.isArray(parsed)) return []
    return parsed.filter(
      (r) =>
        r?.value?.trim() &&
        r.type in DIRECT_BYPASS_TYPE_META &&
        typeof r.enabled === 'boolean',
    )
  } catch {
    return []
  }
}

export function saveDirectBypassRules(rules: DirectBypassRule[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(rules))
}

export function enabledDirectBypassRules(): DirectBypassRule[] {
  return loadDirectBypassRules().filter((r) => r.enabled)
}

function dedupeForClash(rules: DirectBypassRule[]): DirectBypassRule[] {
  const seen = new Set<string>()
  const result: DirectBypassRule[] = []
  for (const rule of rules) {
    if (!rule.enabled) continue
    const key = `${rule.type}:${rule.value.toLowerCase()}`
    if (seen.has(key)) continue
    seen.add(key)
    result.push(rule)
  }
  return result
}

export function toClashLine(rule: DirectBypassRule): string {
  const clashType = DIRECT_BYPASS_TYPE_META[rule.type].clashType
  const base = `- ${clashType},${rule.value},DIRECT`
  return rule.type === 'IP_CIDR' ? `${base},no-resolve` : base
}

/** 将用户规则直连注入 Mihomo config.yaml 的 rules 段（MATCH 前 → DIRECT）。 */
export function injectDirectBypassRules(yaml: string, rules?: DirectBypassRule[]): string {
  const enabled = dedupeForClash(rules ?? enabledDirectBypassRules())
  if (enabled.length === 0) return yaml

  const clashLines = enabled.map((rule) => `  ${toClashLine(rule)}`)
  const yamlLines = yaml.split('\n')
  let rulesSectionStart = -1
  let lastMatchIndex = -1

  yamlLines.forEach((line, index) => {
    const trimmed = line.trim()
    if (trimmed === 'rules:' || trimmed.startsWith('rules:')) {
      rulesSectionStart = index
    }
    if (rulesSectionStart >= 0 && trimmed.startsWith('- MATCH,')) {
      lastMatchIndex = index
    }
  })

  if (rulesSectionStart < 0) {
    return `${yaml.trimEnd()}\n\nrules:\n${clashLines.join('\n')}\n`
  }
  if (lastMatchIndex >= 0) {
    yamlLines.splice(lastMatchIndex, 0, ...clashLines)
    return `${yamlLines.join('\n')}\n`
  }
  return `${yamlLines.concat(clashLines).join('\n')}\n`
}

export function createDirectBypassRule(
  type: DirectBypassRuleTypeName,
  rawValue: string,
): DirectBypassRule {
  return {
    id: crypto.randomUUID(),
    type,
    value: validateDirectBypassRule(type, rawValue),
    enabled: true,
  }
}
