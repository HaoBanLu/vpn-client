import { reactive } from 'vue'

export interface ConfirmOptions {
  title: string
  content?: string
  okText?: string
  cancelText?: string
  type?: 'confirm' | 'error'
  onOk?: () => void | Promise<void>
  onCancel?: () => void
  afterClose?: () => void
}

interface ConfirmState {
  visible: boolean
  options: ConfirmOptions | null
}

export const confirmState = reactive<ConfirmState>({
  visible: false,
  options: null,
})

export function showConfirm(options: ConfirmOptions) {
  confirmState.options = options
  confirmState.visible = true
}

export function hideConfirm() {
  confirmState.visible = false
  const afterClose = confirmState.options?.afterClose
  confirmState.options = null
  afterClose?.()
}

export const confirm = {
  show: showConfirm,
  error(options: Omit<ConfirmOptions, 'type'>) {
    showConfirm({ ...options, type: 'error', cancelText: undefined })
  },
}

/** 兼容 ant-design-vue Modal.confirm / Modal.error API */
export const Modal = {
  confirm(options: ConfirmOptions) {
    showConfirm({ ...options, type: 'confirm' })
  },
  error(options: Omit<ConfirmOptions, 'type'>) {
    showConfirm({ ...options, type: 'error', cancelText: undefined })
  },
}
