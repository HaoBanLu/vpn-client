<template>
  <label class="ky-checkbox" :class="{ 'ky-checkbox--checked': checked }" @click.prevent="toggle">
    <span class="ky-checkbox__box" />
    <span><slot /></span>
  </label>
</template>

<script setup lang="ts">
const props = defineProps<{ checked?: boolean }>()
const emit = defineEmits<{ 'update:checked': [value: boolean] }>()

function toggle() {
  emit('update:checked', !props.checked)
}
</script>
