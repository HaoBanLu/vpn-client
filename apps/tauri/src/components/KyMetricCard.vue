<template>
  <div class="ky-metric" :class="{ 'ky-metric--highlight': highlight }">
    <span class="ky-metric__label">{{ label }}</span>
    <span class="ky-metric__value">{{ value }}</span>
    <span v-if="hint" class="ky-metric__hint">{{ hint }}</span>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  label: string
  value: string
  hint?: string
  highlight?: boolean
}>()
</script>

<style scoped>
.ky-metric {
  flex: 1;
  min-width: 0;
  padding: var(--ky-space-md);
  border-radius: var(--ky-radius-md);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--ky-border-soft);
}

.ky-metric--highlight {
  background: var(--ky-accent-bg);
  border-color: var(--ky-border-strong);
}

.ky-metric__label {
  display: block;
  font-size: var(--ky-font-xs);
  color: var(--ky-text-muted);
  margin-bottom: var(--ky-space-xs);
}

.ky-metric__value {
  display: block;
  font-size: var(--ky-font-md);
  font-weight: 600;
  color: var(--ky-text);
  word-break: break-word;
}

.ky-metric--highlight .ky-metric__value {
  color: var(--ky-accent);
}

.ky-metric__hint {
  display: block;
  margin-top: 2px;
  font-size: var(--ky-font-xs);
  color: var(--ky-text-hint);
}
</style>
