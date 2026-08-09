<template>
  <!-- 对齐 Android CurrentSubscriptionSummaryBar：单行摘要，无进度条 -->
  <div class="ky-subscription-summary">
    <p class="ky-subscription-summary__label">{{ label }}</p>
    <p class="ky-subscription-summary__line">{{ summaryLine }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    packageName: string
    label?: string
    remainingGb?: number | null
    expiresAt?: string | null
  }>(),
  {
    label: '当前套餐',
    remainingGb: null,
    expiresAt: null,
  },
)

const remainingText = computed(() => {
  if (typeof props.remainingGb === 'number' && Number.isFinite(props.remainingGb)) {
    return `${props.remainingGb.toFixed(1)}GB`
  }
  return '-'
})

const expiryText = computed(() => {
  if (!props.expiresAt) return '-'
  const date = new Date(props.expiresAt)
  if (Number.isNaN(date.getTime())) return props.expiresAt.slice(0, 10)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
})

const summaryLine = computed(
  () => `${props.packageName} · 剩余 ${remainingText.value} · ${expiryText.value} 到期`,
)
</script>

<style scoped>
.ky-subscription-summary {
  padding: 14px 16px;
  border-radius: 16px;
  background: var(--ky-bg-card);
  border: 1px solid var(--ky-border-soft);
}

.ky-subscription-summary__label {
  margin: 0;
  font-size: var(--ky-font-xs);
  color: var(--ky-text-muted);
}

.ky-subscription-summary__line {
  margin: 4px 0 0;
  font-size: var(--ky-font-sm);
  font-weight: 600;
  color: var(--ky-text);
  line-height: 1.45;
}
</style>
