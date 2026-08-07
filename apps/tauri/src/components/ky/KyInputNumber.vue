<template>
  <input
    class="ky-input-number"
    type="number"
    :value="modelValue"
    :min="min"
    :max="max"
    :step="step"
    :placeholder="placeholder"
    @input="onInput"
  />
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    modelValue?: number
    min?: number
    max?: number
    step?: number
    placeholder?: string
  }>(),
  { modelValue: 0, step: 1 },
)

const emit = defineEmits<{ 'update:modelValue': [value: number] }>()

function onInput(event: Event) {
  const raw = (event.target as HTMLInputElement).value
  emit('update:modelValue', raw === '' ? 0 : Number(raw))
}
</script>
