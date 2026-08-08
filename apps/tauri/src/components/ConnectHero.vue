<template>
  <section class="connect-hero">
    <ConnectPowerButton
      :label="copy.buttonLabel"
      :variant="copy.variant"
      :connecting="copy.connecting"
      @click="$emit('click')"
    />
    <h2 class="connect-hero__title" :class="`connect-hero__title--${copy.titleTone}`">
      {{ copy.title }}
    </h2>
    <p class="connect-hero__subtitle">{{ copy.subtitle }}</p>
  </section>
</template>

<script setup lang="ts">
import ConnectPowerButton from '@/components/ConnectPowerButton.vue'
import type { ConnectHeroCopy } from '@/lib/connect-hero'

defineProps<{
  copy: ConnectHeroCopy
}>()

defineEmits<{ click: [] }>()
</script>

<style scoped>
.connect-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 0 0 4px;
  width: 100%;
}

.connect-hero__title {
  margin: 0;
  font-size: var(--ky-font-xl);
  font-weight: 600;
  color: var(--ky-text);
  text-align: center;
  letter-spacing: 0.02em;
}

.connect-hero__title--success {
  color: #16a34a; /* ConnectVisual.protectedGreen */
}

.connect-hero__title--error {
  color: #dc2626; /* ConnectVisual.errorRed */
}

.connect-hero__title--info {
  color: #2563eb; /* ConnectVisual.connectingBlue */
}

.connect-hero__subtitle {
  margin: 0;
  padding: 0 var(--ky-space-xl);
  font-size: var(--ky-font-sm);
  line-height: 1.45;
  color: var(--ky-text-secondary);
  text-align: center;
}
</style>
