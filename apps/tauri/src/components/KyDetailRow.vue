<template>
  <div class="ky-detail-row">
    <span class="ky-detail-row__label">{{ label }}</span>
    <span
      class="ky-detail-row__value"
      :class="{ 'ky-detail-row__value--copyable': copyable }"
      :title="copyable ? '点击复制' : undefined"
      @click="onCopy"
    >{{ value }}</span>
  </div>
</template>

<script setup lang="ts">
import { message } from '@/lib/ui/message'

const props = defineProps<{
  label: string
  value: string
  copyable?: boolean
}>()

async function onCopy() {
  if (!props.copyable) return
  try {
    await navigator.clipboard.writeText(props.value)
    message.success('已复制')
  } catch {
    message.error('复制失败')
  }
}
</script>

<style scoped>
.ky-detail-row {
  display: flex;
  flex-direction: column;
  gap: var(--ky-space-xs);
}

.ky-detail-row__label {
  font-size: var(--ky-font-xs);
  color: var(--ky-text-muted);
}

.ky-detail-row__value {
  font-size: var(--ky-font-md);
  color: var(--ky-text);
  word-break: break-all;
}

.ky-detail-row__value--copyable {
  cursor: pointer;
  color: var(--ky-accent-soft);
}

.ky-detail-row__value--copyable:hover {
  text-decoration: underline;
}
</style>
