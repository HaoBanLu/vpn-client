<template>
  <textarea
    class="ky-textarea"
    :class="{ 'ky-textarea--large': size === 'large' }"
    :rows="rows"
    :value="modelValue"
    :placeholder="placeholder"
    @input="onInput"
  />
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    modelValue?: string
    rows?: number
    placeholder?: string
    size?: 'large'
  }>(),
  { modelValue: '', rows: 3 },
)

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

function onInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLTextAreaElement).value)
}
</script>
