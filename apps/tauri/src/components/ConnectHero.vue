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
    <p v-if="copy.subtitle" class="connect-hero__subtitle">{{ copy.subtitle }}</p>
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
  gap: 6px;
  padding: 4px 0 8px;
  width: 100%;
}

.connect-hero__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--ky-text);
  text-align: center;
  letter-spacing: 0.01em;
}

.connect-hero__title--success {
  color: var(--ky-success);
}

.connect-hero__title--error {
  color: var(--ky-danger);
}

.connect-hero__title--info {
  color: var(--ky-accent-deep);
}

.connect-hero__subtitle {
  margin: 0;
  padding: 0 var(--ky-space-xl);
  font-size: var(--ky-font-sm);
  line-height: 1.45;
  color: var(--ky-text-muted);
  text-align: center;
}
</style>
