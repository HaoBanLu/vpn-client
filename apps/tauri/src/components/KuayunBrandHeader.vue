<template>
  <div
    class="brand-header"
    :class="{
      'brand-header--large': large,
      'brand-header--auth': auth,
      'brand-header--tab': tab,
    }"
  >
    <div class="brand-logo">
      <KuayunCloudIcon :size="large ? 'lg' : 'md'" />
    </div>
    <div class="brand-copy">
      <div v-if="showBrand" class="brand-name-row">
        <p class="brand-name">跨云</p>
        <span v-if="showVersion" class="brand-version">v{{ APP_VERSION_NAME }}</span>
      </div>
      <p v-if="title" class="brand-title">{{ title }}</p>
      <p v-if="subtitle" class="brand-subtitle">{{ subtitle }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import KuayunCloudIcon from '@/components/KuayunCloudIcon.vue'
import { APP_VERSION_NAME } from '@/lib/app-meta'

withDefaults(
  defineProps<{
    title?: string
    subtitle?: string
    large?: boolean
    auth?: boolean
    /** 主 Tab 页头：更紧凑，对齐 Compose KuayunMainTabBrandHeader */
    tab?: boolean
    showBrand?: boolean
    showVersion?: boolean
  }>(),
  { large: false, auth: false, tab: false, showBrand: true, showVersion: false },
)
</script>

<style scoped>
.brand-header {
  display: flex;
  align-items: flex-start;
  gap: var(--ky-space-md);
  margin-bottom: var(--ky-space-md);
}

.brand-header--auth {
  width: 100%;
  max-width: 420px;
  margin-bottom: var(--ky-space-lg);
}

.brand-header--tab {
  align-items: center;
  margin-bottom: 2px;
  padding: 2px 0 6px;
}

.brand-header--tab .brand-logo {
  width: 40px;
  height: 40px;
  border-radius: 12px;
}

.brand-header--tab .brand-title {
  margin-top: 1px;
  font-size: 22px;
  font-weight: 650;
  letter-spacing: 0.01em;
}

.brand-header--tab .brand-subtitle {
  margin-top: 6px;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-secondary);
}

.brand-header--large {
  flex-direction: column;
  text-align: center;
  margin-bottom: var(--ky-space-2xl);
}

.brand-header--large .brand-logo {
  width: 72px;
  height: 72px;
  border-radius: var(--ky-radius-xl);
}

.brand-logo {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #0ea5e9, #2563eb);
  box-shadow: var(--ky-shadow-md);
  flex-shrink: 0;
}

.brand-copy {
  min-width: 0;
}

.brand-name-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.brand-name,
.brand-title,
.brand-subtitle {
  margin: 0;
}

.brand-name {
  font-size: var(--ky-font-lg);
  font-weight: 700;
  color: var(--ky-accent);
}

.brand-version {
  font-size: var(--ky-font-xs);
  color: var(--ky-text-muted);
  font-weight: 500;
}

.brand-title {
  margin-top: 2px;
  font-size: var(--ky-font-xl);
  font-weight: 700;
  color: var(--ky-text);
}

.brand-header--large .brand-title {
  font-size: var(--ky-font-2xl);
}

.brand-subtitle {
  margin-top: var(--ky-space-xs);
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
  line-height: 1.5;
}
</style>
