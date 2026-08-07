<template>
  <Teleport to="body">
    <div v-if="open" class="ky-overlay" @click.self="onCancel">
      <div class="ky-modal" role="dialog">
        <div v-if="title" class="ky-modal__header">{{ title }}</div>
        <div class="ky-modal__body">
          <slot>{{ content }}</slot>
        </div>
        <div v-if="showFooter" class="ky-modal__footer">
          <KyButton v-if="showCancel" size="small" @click="onCancel">{{ cancelText }}</KyButton>
          <KyButton type="primary" size="small" @click="onOk">{{ okText }}</KyButton>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import KyButton from './KyButton.vue'

withDefaults(
  defineProps<{
    open?: boolean
    title?: string
    content?: string
    okText?: string
    cancelText?: string
    showCancel?: boolean
    showFooter?: boolean
  }>(),
  { open: false, okText: '确定', cancelText: '取消', showCancel: true, showFooter: true },
)

const emit = defineEmits<{ 'update:open': [value: boolean]; ok: []; cancel: [] }>()

function onOk() {
  emit('ok')
  emit('update:open', false)
}

function onCancel() {
  emit('cancel')
  emit('update:open', false)
}
</script>
