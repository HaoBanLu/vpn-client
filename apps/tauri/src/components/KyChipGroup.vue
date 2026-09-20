<template>
  <div class="ky-chip-group" :class="{ 'ky-chip-group--vertical': vertical }" role="listbox">
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
withDefaults(
  defineProps<{
    modelValue: string | null
    items: Array<{ label: string; value: string | null }>
    /** 竖向排列（节点页左侧地区栏） */
    vertical?: boolean
  }>(),
  { vertical: false },
)

defineEmits<{ 'update:modelValue': [value: string | null] }>()
</script>

<style scoped>
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

.ky-chip-group--vertical {
  flex-direction: column;
  overflow-x: hidden;
  overflow-y: auto;
  padding-bottom: 0;
  gap: 6px;
  max-height: 100%;
  scrollbar-width: thin;
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
  text-align: center;
}

.ky-chip-group--vertical .ky-chip {
  width: 100%;
  padding: 8px 8px;
  font-size: 12px;
  line-height: 1.25;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ky-chip.active {
  border-color: transparent;
  background: var(--ky-nav-active-pill);
  color: var(--ky-on-primary-container);
  font-weight: 650;
}
</style>
