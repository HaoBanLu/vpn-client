import { isProfileRoute } from '@/lib/route-groups'

export type SubpageBackTarget = { name: string } | 'history-back'

export function resolveSubpageBack(input: {
  backTo?: string
  routeName?: string | symbol | null
  historyLength: number
}): SubpageBackTarget {
  if (input.backTo) return { name: input.backTo }
  if (isProfileRoute(input.routeName) && input.routeName !== 'Profile') {
    return { name: 'Profile' }
  }
  if (input.historyLength > 1) return 'history-back'
  return { name: 'Profile' }
}
