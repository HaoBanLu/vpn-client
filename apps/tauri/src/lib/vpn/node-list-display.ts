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

export type NodeRegionSection<T extends { region?: string; region_name?: string }> = {
  key: string
  title: string
  nodes: T[]
}

/**
 * 按控制面地区顺序分组（通讯录分区）。
 * 未出现在 regionOrder 的地区排在末尾。
 */
export function groupNodesByRegionOrder<T extends { region?: string; region_name?: string }>(
  nodes: T[],
  regionOrder: Array<{ code: string; name?: string }>,
): NodeRegionSection<T>[] {
  const buckets = new Map<string, T[]>()
  for (const node of nodes) {
    const key = (node.region || '').trim() || '_unknown'
    const list = buckets.get(key)
    if (list) list.push(node)
    else buckets.set(key, [node])
  }

  const sections: NodeRegionSection<T>[] = []
  const used = new Set<string>()
  for (const region of regionOrder) {
    const key = region.code
    const list = buckets.get(key)
    if (!list?.length) continue
    const title = (region.name || '').trim() || key.toUpperCase()
    sections.push({ key, title, nodes: list })
    used.add(key)
  }
  for (const [key, list] of buckets) {
    if (used.has(key) || list.length === 0) continue
    const title = (list[0]?.region_name || '').trim() || key.toUpperCase()
    sections.push({ key, title, nodes: list })
  }
  return sections
}

/** 右侧索引短字：取地区名首字（日本→日）。 */
export function regionIndexGlyph(title: string): string {
  const text = title.trim()
  return text ? text[0]! : '?'
}
