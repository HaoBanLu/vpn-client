/** 对齐 Android NodeListDisplay：节点列表展示简化。 */

/** 已选地区 Tab 时不再重复展示地区行。 */
export function shouldShowRegionLine(
  filterRegion: string | null | undefined,
  nodeRegion: string | null | undefined,
): boolean {
  if (!filterRegion?.trim()) return true
  return filterRegion.toLowerCase() !== (nodeRegion ?? '').toLowerCase()
}

/**
 * 筛选大陆等地区时，去掉与 Tab 语义重复的场景标签。
 * 例如 Tab=中国大陆 时隐藏「适合回国」。
 */
export function displaySceneTags(
  tags: string[] | null | undefined,
  filterRegion: string | null | undefined,
): string[] {
  const raw = (tags ?? []).map((t) => t.trim()).filter(Boolean)
  if (raw.length === 0) return []
  const hideReturnHome =
    filterRegion?.toLowerCase() === 'cn' || filterRegion?.toLowerCase() === 'china'
  return hideReturnHome ? raw.filter((t) => t !== '适合回国') : raw
}

/** 有延迟的节点按延迟升序；未测速的排后面，保持相对稳定。 */
export function sortNodesByLatency<T extends { id: number }>(
  nodes: T[],
  latencyMap: Record<number, number | undefined>,
): T[] {
  return [...nodes].sort((a, b) => {
    const la = latencyMap[a.id]
    const lb = latencyMap[b.id]
    const aHas = typeof la === 'number' && la > 0
    const bHas = typeof lb === 'number' && lb > 0
    if (aHas && bHas) return (la as number) - (lb as number)
    if (aHas) return -1
    if (bHas) return 1
    return 0
  })
}

/** 延迟最低的节点 id；无有效测速则 null。 */
export function findFastestNodeId(
  nodes: Array<{ id: number }>,
  latencyMap: Record<number, number | undefined>,
): number | null {
  let bestId: number | null = null
  let bestMs = Number.POSITIVE_INFINITY
  for (const node of nodes) {
    const ms = latencyMap[node.id]
    if (typeof ms === 'number' && ms > 0 && ms < bestMs) {
      bestMs = ms
      bestId = node.id
    }
  }
  return bestId
}
