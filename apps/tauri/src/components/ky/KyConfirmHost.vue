<template>
  <KyModal
    :open="confirmState.visible"
    :title="confirmState.options?.title || ''"
    :content="confirmState.options?.content"
    :ok-text="confirmState.options?.okText || '确定'"
    :cancel-text="confirmState.options?.cancelText || '取消'"
    :show-cancel="confirmState.options?.type !== 'error' && !!confirmState.options?.cancelText"
    @update:open="onVisibleChange"
    @ok="handleOk"
    @cancel="handleCancel"
  />
</template>

<script setup lang="ts">
import KyModal from './KyModal.vue'
import { confirmState, hideConfirm } from '@/lib/ui/confirm'

async function handleOk() {
  await confirmState.options?.onOk?.()
  hideConfirm()
}

function handleCancel() {
  confirmState.options?.onCancel?.()
  hideConfirm()
}

function onVisibleChange(visible: boolean) {
  if (!visible) hideConfirm()
}
</script>
