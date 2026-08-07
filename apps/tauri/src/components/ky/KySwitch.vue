<template>
  <button
    type="button"
    class="ky-switch"
    :class="{ 'ky-switch--checked': checked, 'ky-switch--disabled': disabled }"
    role="switch"
    :aria-checked="checked"
    :disabled="disabled"
    @click="toggle"
  >
    <span class="ky-switch__thumb" />
  </button>
</template>

<script setup lang="ts">
const props = defineProps<{ checked?: boolean; disabled?: boolean }>()
const emit = defineEmits<{ 'update:checked': [value: boolean] }>()

function toggle() {
  if (props.disabled) return
  emit('update:checked', !props.checked)
}
</script>
