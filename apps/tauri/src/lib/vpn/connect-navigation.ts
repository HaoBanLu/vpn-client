/** 连接页导航意图（对齐 Android ConnectViewModel requestNavigateToNodes）。 */

export function shouldNavigateToNodes(selectedNode: string | null | undefined): boolean {
  const trimmed = selectedNode?.trim()
  if (!trimmed) return true
  const blocked = new Set(['自动选择', '手动选择', '智能选路', 'auto', 'manual'])
  return blocked.has(trimmed)
}

export function shouldNavigateToPackages(hasSubscription: boolean): boolean {
  return !hasSubscription
}

/** 节点页「连接此节点」：未连接时应选完即连（对齐 Android）。 */
export function shouldConnectAfterNodeSelect(wasConnected: boolean): boolean {
  return !wasConnected
}

/** 选节点后应立即进入连接页（对齐 Android MainShell selectedTab = 0）。 */
export function shouldNavigateToConnectAfterNodeSelect(): boolean {
  return true
}
