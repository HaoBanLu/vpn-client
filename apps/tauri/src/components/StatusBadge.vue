<template>
  <span class="ky-status-badge" :class="variant">
    <span v-if="showDot" class="ky-status-badge__dot" aria-hidden="true" />
    {{ text }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export type StatusBadgeVariant =
  | 'success'
  | 'warning'
  | 'error'
  | 'info'
  | 'neutral'
  | 'online'
  | 'offline'
  | 'recommend'

const props = withDefaults(
  defineProps<{
    text: string
    variant?: StatusBadgeVariant
    dot?: boolean
  }>(),
  { variant: 'neutral', dot: undefined },
)

const showDot = computed(() => {
  if (props.dot != null) return props.dot
  return props.variant === 'online' || props.variant === 'success' || props.variant === 'offline'
})
</script>

<style scoped>
.ky-status-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
  padding: 3px 10px;
  border-radius: 20px;
  font-size: var(--ky-font-xs);
  font-weight: 650;
  letter-spacing: 0;
  line-height: 1.3;
  white-space: nowrap;
}

.ky-status-badge__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}

.ky-status-badge.success,
.ky-status-badge.online {
  background: rgba(76, 175, 80, 0.14);
  color: #2e7d32;
}

.ky-status-badge.warning {
  background: var(--ky-warning-bg);
  color: var(--ky-warning);
}

.ky-status-badge.error {
  background: var(--ky-danger-bg);
  color: var(--ky-danger);
}

.ky-status-badge.info,
.ky-status-badge.recommend {
  background: var(--ky-accent-bg);
  color: var(--ky-accent);
}

.ky-status-badge.neutral,
.ky-status-badge.offline {
  background: var(--ky-surface-variant);
  color: var(--ky-text-muted);
}
</style>
