<template>
  <button type="button" class="ky-list-item" :class="{ 'ky-list-item--disabled': disabled }" :disabled="disabled" @click="$emit('click')">
    <div v-if="icon || $slots.icon" class="ky-list-item__icon">
      <slot name="icon">
        <component :is="icon" v-if="icon" />
      </slot>
    </div>
    <div class="ky-list-item__content">
      <span class="ky-list-item__title">{{ title }}</span>
      <span v-if="subtitle" class="ky-list-item__subtitle">{{ subtitle }}</span>
    </div>
    <div v-if="$slots.trailing || trailing || arrow" class="ky-list-item__trailing">
      <slot name="trailing">
        <span v-if="trailing" class="ky-list-item__trailing-text">{{ trailing }}</span>
      </slot>
      <RightOutlined v-if="arrow" class="ky-list-item__arrow" />
    </div>
  </button>
</template>

<script setup lang="ts">
import { RightOutlined } from '@ant-design/icons-vue'
import type { Component } from 'vue'

defineProps<{
  title: string
  subtitle?: string
  icon?: Component
  trailing?: string
  arrow?: boolean
  disabled?: boolean
}>()

defineEmits<{ click: [] }>()
</script>

<style scoped>
.ky-list-item {
  display: flex;
  align-items: center;
  gap: var(--ky-space-md);
  width: 100%;
  padding: var(--ky-space-md);
  border: 0;
  background: transparent;
  color: var(--ky-text);
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease;
}

.ky-list-item:not(:disabled):hover {
  background: rgba(255, 255, 255, 0.04);
}

.ky-list-item--disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ky-list-item__icon {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: var(--ky-radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--ky-accent-bg);
  color: var(--ky-accent);
  font-size: 16px;
}

.ky-list-item__content {
  flex: 1;
  min-width: 0;
}

.ky-list-item__title {
  display: block;
  font-size: var(--ky-font-md);
  font-weight: 600;
  color: var(--ky-text);
}

.ky-list-item__subtitle {
  display: block;
  margin-top: 2px;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
}

.ky-list-item__trailing {
  display: flex;
  align-items: center;
  gap: var(--ky-space-xs);
  flex-shrink: 0;
}

.ky-list-item__trailing-text {
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
}

.ky-list-item__arrow {
  font-size: 12px;
  color: var(--ky-text-hint);
}
</style>
