const NON_NODE_TAGS = new Set(['自动选择', '手动选择', '智能选路', 'auto', 'manual'])

export function isAcquirableNodeName(name: string | null | undefined): boolean {
  const trimmed = name?.trim()
  if (!trimmed) return false
  return !NON_NODE_TAGS.has(trimmed)
}

export function resolveLineAcquireNode(
  selectedNode: string | null | undefined,
  configNode: string | null | undefined,
  effectiveNode: string | null | undefined,
): string | null {
  for (const candidate of [selectedNode, configNode, effectiveNode]) {
    if (isAcquirableNodeName(candidate)) return candidate!.trim()
  }
  return null
}
