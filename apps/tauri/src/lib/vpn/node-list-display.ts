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
