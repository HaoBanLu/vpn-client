<template>
  <div class="ky-card" :class="{ 'ky-card--highlight': highlight, 'ky-card--flat': flat, 'ky-card--soft': soft }">
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
  /** 登录等表单：surfaceVariant 半透明，对齐 Android AuthForm Card */
  soft?: boolean
}>()
</script>

<style scoped>
.ky-card {
  border-radius: var(--ky-radius-lg);
  border: 1px solid var(--ky-border-soft);
  background: var(--ky-bg-card);
  overflow: hidden;
  box-shadow: var(--ky-shadow-sm);
}

.ky-card--highlight {
  border-color: transparent;
  background: linear-gradient(
    135deg,
    rgba(214, 228, 255, 0.9) 0%,
    rgba(232, 238, 248, 0.95) 100%
  );
}

.ky-card--flat {
  background: var(--ky-bg-elevated);
  box-shadow: none;
}

.ky-card--soft {
  background: color-mix(in srgb, var(--ky-surface-variant) 55%, #fff);
  border-color: transparent;
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
