import type { AppRouteMode } from './app-route-mode'
import { APP_ROUTE_MODE } from './app-route-mode'

export const CONNECTION_SCENARIO = {
  AUTO: 'auto',
  RETURN_HOME: 'return_home',
  OVERSEAS: 'overseas',
} as const

export type ConnectionScenarioValue =
  (typeof CONNECTION_SCENARIO)[keyof typeof CONNECTION_SCENARIO]

export const CLIENT_PROFILE = {
  DOMESTIC_RETURN: 'domestic_return',
  OVERSEAS_WEAK: 'overseas_weak',
} as const

export type ClientProfile = (typeof CLIENT_PROFILE)[keyof typeof CLIENT_PROFILE]

export interface ResolvedConnectionConfig {
  profile: ClientProfile
  routeMode: AppRouteMode
}

export function normalizeConnectionScenario(raw?: string | null): ConnectionScenarioValue {
  const v = raw?.trim().toLowerCase() ?? ''
  if (
    v === CONNECTION_SCENARIO.RETURN_HOME ||
    v === 'return-home' ||
    v === 'returnhome' ||
    v === CLIENT_PROFILE.DOMESTIC_RETURN
  ) {
    return CONNECTION_SCENARIO.RETURN_HOME
  }
  if (v === CONNECTION_SCENARIO.OVERSEAS || v === CLIENT_PROFILE.OVERSEAS_WEAK) {
    return CONNECTION_SCENARIO.OVERSEAS
  }
  return CONNECTION_SCENARIO.AUTO
}

export function connectionScenarioLabel(scenario?: string | null): string {
  switch (normalizeConnectionScenario(scenario)) {
    case CONNECTION_SCENARIO.RETURN_HOME:
      return '回国加速'
    case CONNECTION_SCENARIO.OVERSEAS:
      return '海外访问'
    default:
      return '自动'
  }
}

export function isDomesticReturnProfile(profile?: string | null): boolean {
  return profile?.trim().toLowerCase() === CLIENT_PROFILE.DOMESTIC_RETURN
}

/** 选中国大陆/回国专线节点时，自动场景应走回国 profile。 */
export function inferDomesticReturnFromNode(
  region?: string | null,
  accessMode?: string | null,
): boolean {
  const code = region?.trim().toLowerCase() ?? ''
  if (code === 'cn' || code === 'china' || code.includes('中国')) return true
  return accessMode?.toLowerCase() === 'relay'
}

export function resolveConnectionConfig(
  scenario?: string | null,
  nodeRegion?: string | null,
  accessMode?: string | null,
): ResolvedConnectionConfig {
  const mode = accessMode?.trim().toLowerCase() ?? ''
  // 节点接入类型优先：避免「回国加速」场景下仍连新加坡却下发 domestic_return 画像。
  if (mode === 'direct') {
    return { profile: CLIENT_PROFILE.OVERSEAS_WEAK, routeMode: APP_ROUTE_MODE.FULL }
  }
  if (mode === 'relay') {
    return { profile: CLIENT_PROFILE.DOMESTIC_RETURN, routeMode: APP_ROUTE_MODE.FULL }
  }

  switch (normalizeConnectionScenario(scenario)) {
    case CONNECTION_SCENARIO.RETURN_HOME:
      return { profile: CLIENT_PROFILE.DOMESTIC_RETURN, routeMode: APP_ROUTE_MODE.FULL }
    case CONNECTION_SCENARIO.OVERSEAS:
      return { profile: CLIENT_PROFILE.OVERSEAS_WEAK, routeMode: APP_ROUTE_MODE.FULL }
    default:
      if (inferDomesticReturnFromNode(nodeRegion, accessMode)) {
        return { profile: CLIENT_PROFILE.DOMESTIC_RETURN, routeMode: APP_ROUTE_MODE.FULL }
      }
      return { profile: CLIENT_PROFILE.OVERSEAS_WEAK, routeMode: APP_ROUTE_MODE.FULL }
  }
}
