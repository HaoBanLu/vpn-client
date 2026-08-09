import {
  CONNECTION_SCENARIO,
  normalizeConnectionScenario,
  type ConnectionScenarioValue,
} from './connection-scenario'

/** 节点接入类型标签（对齐 Compose NodeAccessHint.poolLabel）。 */
export function poolLabel(accessMode?: string | null): string | null {
  const mode = accessMode?.trim().toLowerCase()
  if (mode === 'relay') return '回国专线'
  if (mode === 'direct') return '海外直连'
  return null
}

/**
 * 场景与节点类型不匹配时的提示（对齐 Compose NodeAccessHint.scenarioMismatchHint）。
 * 匹配则返回 null。
 */
export function scenarioMismatchHint(
  scenario?: string | null,
  accessMode?: string | null,
): string | null {
  const normalized: ConnectionScenarioValue = normalizeConnectionScenario(scenario)
  const mode = accessMode?.trim().toLowerCase()
  const isRelay = mode === 'relay'
  const isDirect = mode === 'direct'
  if (normalized === CONNECTION_SCENARIO.OVERSEAS && isRelay) {
    return '芜湖/武汉等为「回国专线」，缅甸/海外访问外网请选新加坡、香港等「海外直连」节点。'
  }
  if (normalized === CONNECTION_SCENARIO.RETURN_HOME && isDirect) {
    return '当前为「回国加速」，海外直连节点不适合访问国内站；建议选择武汉或贵州。'
  }
  return null
}
