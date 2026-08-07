<template>
  <div class="ky-progress" :class="{ 'ky-progress--error': status === 'exception' }">
    <div class="ky-progress__bar" :style="{ width: `${percent}%` }" />
  </div>
</template>

<script setup lang="ts">
defineProps<{
  percent?: number
  status?: 'normal' | 'exception' | 'success'
}>()
</script>
