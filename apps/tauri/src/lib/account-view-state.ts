/** 账户/套餐拉取后的页面态：加载、失败、确无套餐、已有套餐。 */

export type AccountViewState = 'loading' | 'error' | 'empty' | 'ready'

export function resolveAccountViewState(input: {
  loading: boolean
  fetched: boolean
  loadError: string | null
  hasSubscription: boolean
}): AccountViewState {
  if (input.hasSubscription) return 'ready'
  if (input.loading) return 'loading'
  if (input.loadError) return 'error'
  if (input.fetched) return 'empty'
  return 'loading'
}

/** 并发调用复用同一个 in-flight Promise，避免 Tab 切换叠 30s 超时。 */
export function shareInflight<T>(holder: { current: Promise<T> | null }, run: () => Promise<T>): Promise<T> {
  if (holder.current) return holder.current
  const next = run().finally(() => {
    if (holder.current === next) holder.current = null
  })
  holder.current = next
  return next
}
