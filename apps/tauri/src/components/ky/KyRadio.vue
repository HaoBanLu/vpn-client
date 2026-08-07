<template>
  <label class="ky-radio" :class="{ 'ky-radio--checked': checked }" @click.prevent="$emit('click')">
    <span class="ky-radio__dot" />
    <span><slot /></span>
  </label>
</template>

<script setup lang="ts">
defineProps<{ checked?: boolean }>()
defineEmits<{ click: [] }>()
</script>
