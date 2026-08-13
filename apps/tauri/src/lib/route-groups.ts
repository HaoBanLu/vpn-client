/** 「我的」Tab 及其子页面路由名，用于侧栏高亮 */
export const PROFILE_ROUTE_NAMES = new Set([
  'Profile',
  'Recharge',
  'Orders',
  'RechargeOrders',
  'PurchaseOrders',
  'Traffic',
  'ChangePassword',
  'Tickets',
  'Devices',
  'Support',
  'Help',
  'About',
  'StabilitySettings',
  'AppDirectConnect',
  'DirectBypassRules',
  'DebugLog',
])

export function isProfileRoute(name: string | symbol | null | undefined): boolean {
  return typeof name === 'string' && PROFILE_ROUTE_NAMES.has(name)
}
