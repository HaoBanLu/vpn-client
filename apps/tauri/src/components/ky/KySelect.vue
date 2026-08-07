<template>
  <select
    class="ky-select"
    :value="displayValue(modelValue ?? null)"
    @change="onChange"
  >
    <option v-if="allowEmpty" value="__empty__">{{ placeholder || '请选择' }}</option>
    <option v-for="opt in options" :key="String(opt.value ?? '__empty__')" :value="optionValue(opt.value)">
      {{ opt.label }}
    </option>
  </select>
</template>

<script setup lang="ts">
export interface KySelectOption {
  label: string
  value: string | number
}

defineProps<{
  modelValue?: string | number | null
  options: KySelectOption[]
  placeholder?: string
  allowEmpty?: boolean
}>()

const emit = defineEmits<{ 'update:modelValue': [value: string | number | null]; change: [value: string | number | null] }>()

function optionValue(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return '__empty__'
  return String(value)
}

function displayValue(value: string | number | null | undefined) {
  return optionValue(value)
}

function onChange(event: Event) {
  const raw = (event.target as HTMLSelectElement).value
  const value = raw === '__empty__' ? null : raw
  emit('update:modelValue', value)
  emit('change', value)
}
</script>
