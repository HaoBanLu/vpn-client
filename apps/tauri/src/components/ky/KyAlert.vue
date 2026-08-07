<template>
  <div class="ky-alert" :class="`ky-alert--${type}`">
    <span>{{ message }}</span>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    type?: 'info' | 'success' | 'warning' | 'error'
    message?: string
  }>(),
  { type: 'info' },
)
</script>
