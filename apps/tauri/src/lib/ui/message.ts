import { reactive } from 'vue'

export type MessageType = 'info' | 'success' | 'error'

interface ToastItem {
  id: number
  type: MessageType
  content: string
}

const state = reactive<{ items: ToastItem[] }>({ items: [] })
let nextId = 1

function push(type: MessageType, content: string, duration = 3000) {
  const id = nextId++
  state.items.push({ id, type, content })
  window.setTimeout(() => {
    const idx = state.items.findIndex((item) => item.id === id)
    if (idx >= 0) state.items.splice(idx, 1)
  }, duration)
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
