<template>
  <div class="ky-chip-group" role="listbox">
    <button
      v-for="item in items"
      :key="String(item.value)"
      type="button"
      role="option"
      class="ky-chip"
      :class="{ active: modelValue === item.value }"
      :aria-selected="modelValue === item.value"
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
/* 对齐 Android RegionFilterRow / FilterChip：横滑、非 pill、选中 primaryContainer */
.ky-chip-group {
  display: flex;
  flex-wrap: nowrap;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}

.ky-chip-group::-webkit-scrollbar {
  display: none;
}

.ky-chip {
  flex-shrink: 0;
  border: 1px solid var(--ky-border);
  border-radius: 8px;
  padding: 6px 12px;
  font-size: 13px;
  background: #fff;
  color: var(--ky-text);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.ky-chip.active {
  border-color: transparent;
  background: #d6e4ff;
  color: #0a2463;
  font-weight: 650;
}
</style>
