<template>
  <div class="ky-space" :style="wrap ? undefined : { flexWrap: 'nowrap' }">
    <slot />
  </div>
</template>

<script setup lang="ts">
defineProps<{ wrap?: boolean }>()
</script>
