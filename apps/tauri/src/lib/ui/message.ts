import { reactive } from 'vue'

export type MessageType = 'info' | 'success' | 'error'

interface ToastItem {
  id: number
  type: MessageType
  content: string
}

const state = reactive<{ items: ToastItem[] }>({ items: [] })
let nextId = 1

const DEFAULT_DURATION: Record<MessageType, number> = {
  info: 1500,
  success: 1500,
  error: 2200,
}

/** 相同 type+content 短窗内只弹一次，挡住 Promise.all 刷新风暴。 */
const DEDUPE_WINDOW_MS = 2000
let lastDedupeKey = ''
let lastDedupeAt = 0

function push(type: MessageType, content: string, duration?: number) {
  const key = `${type}:${content}`
  const now = Date.now()
  if (key === lastDedupeKey && now - lastDedupeAt < DEDUPE_WINDOW_MS) {
    return
  }
  lastDedupeKey = key
  lastDedupeAt = now

  const id = nextId++
  state.items.push({ id, type, content })
  const ms = duration ?? DEFAULT_DURATION[type]
  window.setTimeout(() => {
    const idx = state.items.findIndex((item) => item.id === id)
    if (idx >= 0) state.items.splice(idx, 1)
  }, ms)
}

export const messageState = state

export const message = {
  info(content: string) {
    push('info', content)
  },
  success(content: string) {
    push('success', content)
  },
  error(content: string) {
    push('error', content)
  },
  warning(content: string) {
    push('info', content)
  },
}
