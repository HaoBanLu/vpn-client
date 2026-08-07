<template>
  <div class="ky-chip-group">
    <button
      v-for="item in items"
      :key="String(item.value)"
      type="button"
      class="ky-chip"
      :class="{ active: modelValue === item.value }"
      @click="$emit('update:modelValue', item.value)"
    >
      {{ item.label }}
    </button>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  modelValue: string | null
  items: Array<{ label: string; value: string | null }>
}>()

defineEmits<{ 'update:modelValue': [value: string | null] }>()
</script>

<style scoped>
.ky-chip-group {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ky-space-sm);
}

.ky-chip {
  border: 1px solid var(--ky-border);
  border-radius: var(--ky-radius-full);
  padding: 6px 14px;
  font-size: var(--ky-font-md);
  background: rgba(255, 255, 255, 0.04);
  color: var(--ky-text-secondary);
  cursor: pointer;
  transition: all 0.15s ease;
}

.ky-chip.active {
  border-color: var(--ky-border-strong);
  background: var(--ky-accent-bg);
  color: var(--ky-accent);
  font-weight: 500;
}
</style>
