<template>
  <span class="ky-status-badge" :class="variant">{{ text }}</span>
</template>

<script setup lang="ts">
export type StatusBadgeVariant =
  | 'success'
  | 'warning'
  | 'error'
  | 'info'
  | 'neutral'
  | 'online'
  | 'offline'
  | 'recommend'

withDefaults(
  defineProps<{
    text: string
    variant?: StatusBadgeVariant
  }>(),
  { variant: 'neutral' },
)
</script>

<style scoped>
.ky-status-badge {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  padding: 3px 9px;
  border-radius: var(--ky-radius-sm);
  font-size: var(--ky-font-xs);
  font-weight: 600;
  letter-spacing: 0;
  line-height: 1.3;
  border: 1px solid transparent;
  white-space: nowrap;
}

.ky-status-badge.success,
.ky-status-badge.online {
  background: var(--ky-success-bg);
  border-color: rgba(74, 222, 128, 0.3);
  color: var(--ky-success);
}

.ky-status-badge.warning {
  background: var(--ky-warning-bg);
  border-color: rgba(251, 191, 36, 0.3);
  color: var(--ky-warning);
}

.ky-status-badge.error {
  background: var(--ky-danger-bg);
  border-color: rgba(248, 113, 113, 0.3);
  color: var(--ky-danger);
}

.ky-status-badge.info,
.ky-status-badge.recommend {
  background: var(--ky-accent-bg);
  border-color: var(--ky-border-strong);
  color: var(--ky-accent-soft);
}

.ky-status-badge.neutral,
.ky-status-badge.offline {
  background: rgba(255, 255, 255, 0.06);
  border-color: var(--ky-border);
  color: var(--ky-text-secondary);
}
</style>
