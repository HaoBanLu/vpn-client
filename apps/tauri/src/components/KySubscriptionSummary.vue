<template>
  <!-- 紧凑单行状态条：无标题，信息一眼扫完 -->
  <div class="ky-subscription-summary">
    <span class="ky-subscription-summary__tag">当前</span>
    <p class="ky-subscription-summary__line">
      <span class="ky-subscription-summary__name">{{ packageName }}</span>
      <span class="ky-subscription-summary__sep" aria-hidden="true">·</span>
      <span>剩余 {{ remainingText }}</span>
      <span class="ky-subscription-summary__sep" aria-hidden="true">·</span>
      <span>{{ expiryText }} 到期</span>
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    packageName: string
    remainingGb?: number | null
    expiresAt?: string | null
  }>(),
  {
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
</script>

<style scoped>
.ky-subscription-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--ky-nav-active-pill);
  border: 1px solid rgba(59, 130, 246, 0.16);
}

.ky-subscription-summary__tag {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.35;
  color: var(--ky-accent);
  background: color-mix(in srgb, var(--ky-accent) 12%, white);
}

.ky-subscription-summary__line {
  margin: 0;
  min-width: 0;
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 6px;
  font-size: var(--ky-font-sm);
  font-weight: 600;
  color: var(--ky-text);
  line-height: 1.4;
}

.ky-subscription-summary__name {
  font-weight: 700;
}

.ky-subscription-summary__sep {
  color: var(--ky-text-muted);
  font-weight: 500;
}
</style>
