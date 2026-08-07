<template>
  <KyCard highlight>
    <div class="ky-selected-banner">
      <div class="ky-selected-banner__copy">
        <p class="ky-selected-banner__label">{{ label }}</p>
        <p class="ky-selected-banner__value">{{ value }}</p>
      </div>
      <KyButton type="link" :disabled="clearDisabled" @click="$emit('clear')">
        {{ clearLabel }}
      </KyButton>
    </div>
  </KyCard>
</template>

<script setup lang="ts">
import KyCard from '@/components/KyCard.vue'
import { KyButton } from '@/components/ky'

withDefaults(
  defineProps<{
    label: string
    value: string
    clearLabel?: string
    clearDisabled?: boolean
  }>(),
  { clearLabel: '清除', clearDisabled: false },
)

defineEmits<{ clear: [] }>()
</script>

<style scoped>
.ky-selected-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ky-space-sm);
}

.ky-selected-banner__label {
  margin: 0;
  font-size: var(--ky-font-xs);
  color: var(--ky-text-muted);
}

.ky-selected-banner__value {
  margin: var(--ky-space-xs) 0 0;
  font-size: var(--ky-font-md);
  font-weight: 600;
  color: var(--ky-text);
}
</style>
