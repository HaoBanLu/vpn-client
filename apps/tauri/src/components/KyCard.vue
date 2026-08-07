<template>
  <div class="ky-card" :class="{ 'ky-card--highlight': highlight, 'ky-card--flat': flat }">
    <div v-if="title || $slots.header" class="ky-card__head">
      <slot name="header">
        <h3 v-if="title" class="ky-card__title">{{ title }}</h3>
        <p v-if="subtitle" class="ky-card__subtitle">{{ subtitle }}</p>
      </slot>
      <div v-if="$slots.extra" class="ky-card__extra">
        <slot name="extra" />
      </div>
    </div>
    <div class="ky-card__body">
      <slot />
    </div>
    <div v-if="$slots.footer" class="ky-card__footer">
      <slot name="footer" />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title?: string
  subtitle?: string
  highlight?: boolean
  flat?: boolean
}>()
</script>

<style scoped>
.ky-card {
  border-radius: var(--ky-radius-lg);
  border: 1px solid var(--ky-border);
  background: var(--ky-bg-card);
  overflow: hidden;
}

.ky-card--highlight {
  border-color: var(--ky-border-strong);
  background: linear-gradient(135deg, var(--ky-bg-card) 0%, var(--ky-bg-elevated) 100%);
}

.ky-card--flat {
  background: var(--ky-bg-elevated);
}

.ky-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--ky-space-sm);
  padding: var(--ky-space-md) var(--ky-space-md) 0;
}

.ky-card__title {
  margin: 0;
  font-size: var(--ky-font-lg);
  font-weight: 600;
  color: var(--ky-text);
}

.ky-card__subtitle {
  margin: var(--ky-space-xs) 0 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
}

.ky-card__extra {
  flex-shrink: 0;
}

.ky-card__body {
  padding: var(--ky-space-lg) var(--ky-space-xl);
}

.ky-card__head + .ky-card__body {
  padding-top: var(--ky-space-md);
}

.ky-card__footer {
  padding: 0 var(--ky-space-md) var(--ky-space-md);
  border-top: 1px solid var(--ky-border-soft);
  padding-top: var(--ky-space-md);
}
</style>
